package egovframework.eeum.auth.service.token.impl;

import java.util.concurrent.atomic.AtomicLong;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import egovframework.eeum.auth.service.token.AuthRevocationStore;
import lombok.extern.slf4j.Slf4j;

/**
 * Redis 기반 토큰 세대 저장소. 키: {@code auth:token-gen:{userSn}} = 단조 증가 정수(세대).
 *
 * <p>세대 증가는 {@code INCR}(원자적)이라 downgrade 가 불가능하다. <b>키는 만료시키지 않는다(영구)</b> —
 * TTL 을 걸면 키가 만료된 뒤 {@code INCR} 이 0 부터 다시 시작해 세대값이 재사용되고, 그 사이 발급된 옛 토큰이
 * 새 세대와 같은 값을 가져 무효화가 깨진다(codex MUST). 키는 비밀번호 변경/탈퇴를 한 번이라도 한 사용자만
 * 생기는 작은 정수라 영구 보관해도 메모리 부담이 작다(Redis 는 noeviction 운영 권장).
 */
@Slf4j
@Component
public class RedisAuthRevocationStore implements AuthRevocationStore {

	private static final String KEY_PREFIX = "auth:token-gen:";

	/** fail-open 로그 폭주 억제 — 최소 간격(ms). */
	private static final long WARN_THROTTLE_MS = 60_000L;

	private final StringRedisTemplate redisTemplate;
	private final AtomicLong lastWarnMs = new AtomicLong(0L);

	public RedisAuthRevocationStore(StringRedisTemplate redisTemplate) {
		this.redisTemplate = redisTemplate;
	}

	private static String key(long userSn) {
		return KEY_PREFIX + userSn;
	}

	@Override
	public long currentGeneration(long userSn) {
		try {
			String value = redisTemplate.opsForValue().get(key(userSn));
			return value == null ? 0L : Long.parseLong(value);
		} catch (RuntimeException e) { // NumberFormatException 포함 — Redis 장애·형식오류 모두 fail-open
			// fail-open — Redis 장애 시 세대 0(무효화 미적용). 전체 인증 마비보다 위험 작음.
			throttledWarn(userSn, e);
			return 0L;
		}
	}

	@Override
	public void bumpGeneration(long userSn) {
		// 영구 카운터 — TTL 없이 INCR 만(값 재사용 방지). downgrade 불가(단조 증가).
		redisTemplate.opsForValue().increment(key(userSn));
	}

	/** 장애 로그를 최소 간격으로만 — 매 요청 stacktrace 폭주 방지(codex SHOULD). */
	private void throttledWarn(long userSn, Exception e) {
		long now = System.currentTimeMillis();
		long prev = lastWarnMs.get();
		if (now - prev >= WARN_THROTTLE_MS && lastWarnMs.compareAndSet(prev, now)) {
			log.warn("[AUTH] token-generation lookup failed (fail-open). userSn={}", userSn, e);
		}
	}
}
