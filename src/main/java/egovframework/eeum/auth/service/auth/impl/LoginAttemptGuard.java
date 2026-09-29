package egovframework.eeum.auth.service.auth.impl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import egovframework.eeum.auth.exception.AuthErrorCode;
import egovframework.eeum.base.exception.BusinessException;
import egovframework.eeum.base.exception.CommonErrorCode;
import lombok.extern.slf4j.Slf4j;

/**
 * 로그인 무차별 대입(brute-force) 방어 — Redis 기반 실패 카운터/임시 잠금 (보안 자가점검 + codex 교차검토).
 *
 * <p>설계:
 * <ul>
 *   <li>2계층 키: (계정+IP) 임계/윈도우, (IP 집계) 더 높은 임계. 계정 단독 하드락은 피해자 계정 잠금 DoS 라 미사용.</li>
 *   <li>키에 이메일/IP 원문 대신 SHA-256 해시 — PII 미저장 + 키 길이 고정(noeviction 메모리 잠식 완화).</li>
 *   <li>차단 응답 = {@link AuthErrorCode#AUTH_INVALID_CREDENTIALS} 동일 — 잠금/남은시간 노출은 계정 열거 신호라 사유는 로그만.</li>
 *   <li>Redis 장애 시 fail-closed(503) — 세션 저장도 Redis 라 어차피 로그인 완결 불가. 잘못된 비번처럼 보이지 않게 503.</li>
 *   <li>INCR+EXPIRE 는 Lua 로 원자 처리(최초 1회만 TTL = 고정 윈도우, 무한 잠금 방지).</li>
 * </ul>
 */
@Slf4j
@Component
public class LoginAttemptGuard {

	private static final String KEY_ACCT_IP = "auth:lgnfail:ai:";
	private static final String KEY_IP = "auth:lgnfail:ip:";

	/** INCR 후 최초(==1)일 때만 EXPIRE — 고정 윈도우. 반환: 현재 카운트. */
	private static final RedisScript<Long> INCR_WITH_TTL = new DefaultRedisScript<>(
			"local c = redis.call('INCR', KEYS[1]) "
					+ "if c == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end "
					+ "return c",
			Long.class);

	private final StringRedisTemplate redis;

	/** (계정+IP) 윈도우 내 허용 실패 횟수 — 초과 시 차단. */
	@Value("${eeum.login-guard.acct-ip-max:5}")
	private int acctIpMax;
	/** (IP 집계) 윈도우 내 허용 실패 횟수 — 분산 계정 시도 방어. */
	@Value("${eeum.login-guard.ip-max:50}")
	private int ipMax;
	/** 카운팅/잠금 윈도우(초) — 기본 15분. */
	@Value("${eeum.login-guard.window-seconds:900}")
	private long windowSeconds;

	public LoginAttemptGuard(StringRedisTemplate redis) {
		this.redis = redis;
	}

	/** 로그인 처리 전 호출 — 임계 초과 시 차단(generic 응답), Redis 장애 시 503(fail-closed). */
	public void assertNotBlocked(String email, String ip) {
		long acctIp;
		long ipCount;
		try {
			acctIp = count(KEY_ACCT_IP + hash(email + "|" + ip));
			ipCount = count(KEY_IP + hash(ip));
		} catch (RuntimeException e) {
			log.error("[LOGIN-GUARD] redis unavailable on check — fail-closed", e);
			throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE);
		}
		if (acctIp >= acctIpMax || ipCount >= ipMax) {
			// 잠금 사실은 노출하지 않음(열거 방지) — 사유는 로그만
			log.warn("[LOGIN-GUARD] blocked (TOO_MANY_ATTEMPTS): acctIp={}, ip={}", acctIp, ipCount);
			throw new BusinessException(AuthErrorCode.AUTH_INVALID_CREDENTIALS);
		}
	}

	/** 로그인 실패 시 호출 — 계정+IP·IP 카운터 증가. 기록 실패는 로그만(본 흐름 불간섭). */
	public void recordFailure(String email, String ip) {
		try {
			increment(KEY_ACCT_IP + hash(email + "|" + ip));
			increment(KEY_IP + hash(ip));
		} catch (RuntimeException e) {
			log.error("[LOGIN-GUARD] redis unavailable on record", e);
		}
	}

	/** 로그인 성공 시 호출 — 계정+IP 키만 리셋(IP 집계는 유지: 한 계정 성공이 IP 전체 면죄부가 되지 않게). */
	public void recordSuccess(String email, String ip) {
		try {
			redis.delete(KEY_ACCT_IP + hash(email + "|" + ip));
		} catch (RuntimeException e) {
			log.error("[LOGIN-GUARD] redis unavailable on reset", e);
		}
	}

	private long count(String key) {
		String value = redis.opsForValue().get(key);
		if (value == null) {
			return 0L;
		}
		try {
			return Long.parseLong(value);
		} catch (NumberFormatException e) {
			return 0L;
		}
	}

	private void increment(String key) {
		redis.execute(INCR_WITH_TTL, List.of(key), String.valueOf(windowSeconds));
	}

	/** 이메일/IP 정규화 후 SHA-256 hex — Redis 키에 PII 미노출 + 키 길이 고정. */
	private static String hash(String raw) {
		String norm = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(norm.getBytes(StandardCharsets.UTF_8));
			StringBuilder sb = new StringBuilder(digest.length * 2);
			for (byte b : digest) {
				sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
			}
			return sb.toString();
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 unavailable", e);
		}
	}
}
