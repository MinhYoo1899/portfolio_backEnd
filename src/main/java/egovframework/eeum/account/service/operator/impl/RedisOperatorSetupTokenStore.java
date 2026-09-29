package egovframework.eeum.account.service.operator.impl;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import egovframework.eeum.account.service.operator.OperatorSetupTokenStore;
import jakarta.annotation.PostConstruct;

/**
 * Redis 기반 운영자 설정토큰 저장소.
 *
 * <p>키: {@code operator:setup:{userSn}} (String) — 저장값 = HMAC hex, TTL = setup ttl-seconds(기본 48h). 1회성.
 *
 * <p>보안/원자성({@link RedisEmailVerificationChallengeStore} 본뜸): 평문 토큰은 저장/로그 금지 — HMAC 만 저장.
 * 소비는 단일 Lua 로 처리한다 — 저장된 해시가 제시 해시와 일치하면 DEL 후 1, 아니면 0(키는 유지해 정상 토큰
 * 재시도를 막지 않되, 일치 소비는 즉시 폐기). GETDEL 대신 비교-후-DEL Lua 로 잘못된 토큰에 키가 날아가는 것을 방지.
 */
@Component
public class RedisOperatorSetupTokenStore implements OperatorSetupTokenStore {

	private static final String KEY_SETUP = "operator:setup:";

	/**
	 * 소비(원자) — KEYS[1]=setup. ARGV[1]=expectedHmac.
	 * 저장 해시가 없으면 0, 제시 해시와 일치하면 DEL 후 1(소비), 불일치면 0(키 유지).
	 */
	private static final RedisScript<Long> CONSUME_SCRIPT = new DefaultRedisScript<>(
			"local h = redis.call('GET', KEYS[1]) "
					+ "if h == false then return 0 end "
					+ "if h == ARGV[1] then redis.call('DEL', KEYS[1]) return 1 end "
					+ "return 0",
			Long.class);

	private final StringRedisTemplate redis;
	private final OperatorSetupTokenCodec codec;

	/** 설정토큰 유효시간(초) — 기본 48시간. */
	@Value("${eeum.operator.setup.ttl-seconds:172800}")
	private long ttlSeconds;

	public RedisOperatorSetupTokenStore(
			StringRedisTemplate redis,
			@Value("${eeum.operator.setup.hmac-secret:${Globals.jwt.secret}}") String hmacSecret) {
		this.redis = redis;
		this.codec = new OperatorSetupTokenCodec(hmacSecret);
	}

	/** 설정값 양수 검증 — 오설정(0/음수) 시 TTL 없는 토큰이 영구 잔류하지 않게 기동 거부. */
	@PostConstruct
	void validateConfig() {
		if (ttlSeconds <= 0) {
			throw new IllegalStateException("운영자 설정토큰 ttl-seconds 는 양수여야 합니다 — ttl-seconds=" + ttlSeconds);
		}
	}

	@Override
	public String issueToken(long userSn) {
		OperatorSetupTokenCodec.IssuedToken issued = codec.generate(userSn);
		// 재발급 시 이전 토큰 자동 무효화(키 1개) — TTL 동반 SET 으로 TTL 없는 토큰 잔류 방지.
		redis.opsForValue().set(KEY_SETUP + userSn, issued.hmac(),
				java.time.Duration.ofSeconds(ttlSeconds));
		// 평문 토큰은 호출측이 발급 응답에만 1회 노출하고 메모리 밖으로 내보내지 않는다.
		return issued.token();
	}

	@Override
	public Long consume(String token) {
		OperatorSetupTokenCodec.ParsedToken parsed = codec.parse(token);
		if (parsed == null) {
			return null;
		}
		String hmac = codec.hmac(parsed.userSn(), parsed.tokenSecret());
		Long r = redis.execute(CONSUME_SCRIPT, List.of(KEY_SETUP + parsed.userSn()), hmac);
		return (r != null && r == 1L) ? parsed.userSn() : null;
	}
}
