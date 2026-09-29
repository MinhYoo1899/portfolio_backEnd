package egovframework.eeum.account.service.account.impl;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import egovframework.eeum.base.exception.BusinessException;
import egovframework.eeum.base.exception.CommonErrorCode;
import lombok.extern.slf4j.Slf4j;

/**
 * 비밀번호 변경 시 "현재 비밀번호" 온라인 추측(brute-force) 차단 — 사용자별 실패 카운터(Redis, codex SHOULD ⑧).
 *
 * <p>access 토큰이 탈취돼도 만료(30분) 안에 현재 비밀번호를 무차별 추측하지 못하게 한다.
 * 키는 {@code userSn} 기준 — 탈취자가 IP 를 바꿔도 한 사용자에 대한 시도는 모두 집계된다.
 * 차단 응답은 429(RATE_LIMITED). Redis 장애 시 fail-open(실제 변경은 세대 증가에 Redis 가 필요해 어차피 완결 불가).
 */
@Slf4j
@Component
public class PasswordChangeGuard {

	private static final String KEY_PREFIX = "auth:pwchg:fail:";

	/** 시도 카운트 증가(원자) — INCR 후 최초(==1)일 때만 EXPIRE(고정 윈도우). 반환: 증가된 카운트. */
	private static final RedisScript<Long> INCR_WITH_TTL = new DefaultRedisScript<>(
			"local c = redis.call('INCR', KEYS[1]) "
					+ "if c == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end "
					+ "return c",
			Long.class);

	private final StringRedisTemplate redis;

	/** 윈도우 내 허용 시도 횟수 — 초과 시 차단. */
	@Value("${eeum.pwchg-guard.max:5}")
	private int max;
	/** 카운팅/잠금 윈도우(초) — 기본 15분. */
	@Value("${eeum.pwchg-guard.window-seconds:900}")
	private long windowSeconds;

	public PasswordChangeGuard(StringRedisTemplate redis) {
		this.redis = redis;
	}

	/**
	 * 변경 시도를 1건 등록하고 임계 초과면 429 — 증가와 판정을 한 번의 Lua 로 원자 처리(동시요청 우회 차단, codex SHOULD ④).
	 * 성공 시 {@link #reset}으로 카운터를 비운다(연속 실패만 누적). Redis 장애 시 fail-open.
	 */
	public void registerAttemptAndCheck(long userSn) {
		long count;
		try {
			Long c = redis.execute(INCR_WITH_TTL, List.of(KEY_PREFIX + userSn), String.valueOf(windowSeconds));
			count = c == null ? 0L : c;
		} catch (RuntimeException e) {
			log.warn("[PWCHG-GUARD] redis unavailable (fail-open). userSn={}", userSn, e);
			return;
		}
		if (count > max) {
			log.warn("[PWCHG-GUARD] blocked (TOO_MANY_ATTEMPTS): userSn={}, count={}", userSn, count);
			throw new BusinessException(CommonErrorCode.RATE_LIMITED);
		}
	}

	/** 변경 성공 시 호출 — 시도 카운터 리셋. */
	public void reset(long userSn) {
		try {
			redis.delete(KEY_PREFIX + userSn);
		} catch (RuntimeException e) {
			log.warn("[PWCHG-GUARD] redis unavailable on reset. userSn={}", userSn, e);
		}
	}
}
