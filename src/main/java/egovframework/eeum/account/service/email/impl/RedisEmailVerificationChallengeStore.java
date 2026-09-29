package egovframework.eeum.account.service.email.impl;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import egovframework.eeum.account.exception.AccountErrorCode;
import egovframework.eeum.account.service.email.EmailVerificationChallengeStore;
import egovframework.eeum.base.exception.BusinessException;
import egovframework.eeum.base.exception.CommonErrorCode;
import jakarta.annotation.PostConstruct;

/**
 * Redis 기반 이메일 인증 챌린지 저장소.
 *
 * <p>키:
 * <ul>
 *   <li>{@code email:verify:challenge:{userSn}} (Hash) — email·purpose·hmac·attempts, TTL=ttl-seconds. 1회성.</li>
 *   <li>{@code email:verify:cooldown:{userSn}} (String) — 재발송 쿨다운, TTL=resend-cooldown-seconds.</li>
 *   <li>{@code email:verify:daily:{userSn}:{yyyyMMdd}} (String) — 일일 발송 카운터, 최초 INCR 시 EXPIRE.</li>
 * </ul>
 *
 * <p>보안/원자성(codex MUST): 평문 코드는 저장/로그 금지 — Hash 에는 HMAC hex 만. 발급은 쿨다운 확인 →
 * 일일한도 증가/초과 rollback → 쿨다운 set → 챌린지 저장 → TTL 설정을 <b>단일 Lua</b>로 원자 처리한다
 * (쿨다운을 먼저 봐서 거부 요청이 일일 카운터를 소진하지 않게 하고, putAll/expire 분리로 TTL 없는 챌린지가
 * 남는 것을 막는다). 검증도 시도증가·비교·소비를 단일 Lua 로 처리한다.
 */
@Component
public class RedisEmailVerificationChallengeStore implements EmailVerificationChallengeStore {

	private static final String KEY_CHALLENGE = "email:verify:challenge:";
	private static final String KEY_COOLDOWN = "email:verify:cooldown:";
	private static final String KEY_DAILY = "email:verify:daily:";

	private static final String F_EMAIL = "email";
	private static final String F_PURPOSE = "purpose";
	private static final String F_HMAC = "hmac";
	private static final String F_ATTEMPTS = "attempts";

	private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.ofPattern("yyyyMMdd", Locale.ROOT);

	/**
	 * 발급(원자) — KEYS[1]=cooldown, [2]=daily, [3]=challenge.
	 * ARGV[1]=cooldownTtl, [2]=dailyTtl, [3]=dailyLimit, [4]=challengeTtl, [5]=hmac, [6]=email, [7]=purpose.
	 * 반환: -1=쿨다운 활성, -2=일일한도 초과, 1=발급 성공.
	 */
	private static final RedisScript<Long> ISSUE_SCRIPT = new DefaultRedisScript<>(
			"if redis.call('EXISTS', KEYS[1]) == 1 then return -1 end "
					+ "local d = redis.call('INCR', KEYS[2]) "
					+ "if d == 1 then redis.call('EXPIRE', KEYS[2], ARGV[2]) end "
					+ "if d > tonumber(ARGV[3]) then redis.call('DECR', KEYS[2]) return -2 end "
					+ "redis.call('SET', KEYS[1], '1', 'EX', ARGV[1]) "
					+ "redis.call('DEL', KEYS[3]) "
					+ "redis.call('HSET', KEYS[3], '" + F_EMAIL + "', ARGV[6], '" + F_PURPOSE + "', ARGV[7], '"
					+ F_HMAC + "', ARGV[5], '" + F_ATTEMPTS + "', '0') "
					+ "redis.call('EXPIRE', KEYS[3], ARGV[4]) "
					+ "return 1",
			Long.class);

	/**
	 * 검증(원자) — 챌린지 없으면 -2(EXPIRED). 있으면 attempts INCR 후 초과면 DEL+(-1=TOO_MANY),
	 * hmac·email·purpose 일치면 DEL+1(OK), 불일치면 0(INVALID).
	 * ARGV[1]=maxAttempts, [2]=hmac, [3]=email, [4]=purpose.
	 */
	private static final RedisScript<Long> VERIFY_SCRIPT = new DefaultRedisScript<>(
			"if redis.call('EXISTS', KEYS[1]) == 0 then return -2 end "
					+ "local n = redis.call('HINCRBY', KEYS[1], '" + F_ATTEMPTS + "', 1) "
					+ "if n > tonumber(ARGV[1]) then redis.call('DEL', KEYS[1]) return -1 end "
					+ "local h = redis.call('HGET', KEYS[1], '" + F_HMAC + "') "
					+ "local e = redis.call('HGET', KEYS[1], '" + F_EMAIL + "') "
					+ "local p = redis.call('HGET', KEYS[1], '" + F_PURPOSE + "') "
					+ "if h == ARGV[2] and e == ARGV[3] and p == ARGV[4] then redis.call('DEL', KEYS[1]) return 1 end "
					+ "return 0",
			Long.class);

	private final StringRedisTemplate redis;
	private final EmailVerificationCodec codec;

	/** 코드 유효시간(초). */
	@Value("${eeum.email.verification.ttl-seconds:300}")
	private long ttlSeconds;
	/** 검증 시도 제한(초과 시 챌린지 폐기). */
	@Value("${eeum.email.verification.max-attempts:5}")
	private int maxAttempts;
	/** 재발송 쿨다운(초). */
	@Value("${eeum.email.verification.resend-cooldown-seconds:60}")
	private long resendCooldownSeconds;
	/** 일일 발송 한도(userSn 기준). */
	@Value("${eeum.email.verification.daily-limit:5}")
	private int dailyLimit;

	public RedisEmailVerificationChallengeStore(
			StringRedisTemplate redis,
			@Value("${eeum.email.verification.hmac-secret:${Globals.jwt.secret}}") String hmacSecret) {
		this.redis = redis;
		this.codec = new EmailVerificationCodec(hmacSecret);
	}

	/** 설정값 양수 검증 — 오설정(0/음수) 시 발급/검증이 조용히 오작동하지 않게 기동 거부(codex SHOULD). */
	@PostConstruct
	void validateConfig() {
		if (ttlSeconds <= 0 || resendCooldownSeconds <= 0 || maxAttempts <= 0 || dailyLimit <= 0) {
			throw new IllegalStateException("이메일 인증 설정값은 모두 양수여야 합니다 — "
					+ "ttl-seconds=" + ttlSeconds + ", resend-cooldown-seconds=" + resendCooldownSeconds
					+ ", max-attempts=" + maxAttempts + ", daily-limit=" + dailyLimit);
		}
	}

	@Override
	public String issueCode(long userSn, String email, String purpose) {
		String code = codec.generateCode();
		String hmac = codec.hmac(purpose, userSn, email, code);
		String dailyKey = KEY_DAILY + userSn + ":" + LocalDate.now(ZoneOffset.UTC).format(YYYYMMDD);

		Long r = redis.execute(ISSUE_SCRIPT,
				List.of(KEY_COOLDOWN + userSn, dailyKey, KEY_CHALLENGE + userSn),
				String.valueOf(resendCooldownSeconds),
				String.valueOf(secondsUntilUtcMidnight()),
				String.valueOf(dailyLimit),
				String.valueOf(ttlSeconds),
				hmac, normalizeEmail(email), purpose);
		long result = r == null ? 0L : r;
		if (result == -1L) {
			throw new BusinessException(AccountErrorCode.EMAIL_VERIFICATION_COOLDOWN);
		}
		if (result == -2L) {
			throw new BusinessException(AccountErrorCode.EMAIL_VERIFICATION_DAILY_EXCEEDED);
		}
		if (result != 1L) {
			// 1/-1/-2 외(null·예상밖) — 인프라 오류로 보고 fail-closed(챌린지 없이 메일 발송 방지, codex SHOULD).
			throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE);
		}
		// 평문 코드는 호출측이 즉시 발송 포트로 넘기고 메모리 밖으로 내보내지 않는다.
		return code;
	}

	@Override
	public VerifyResult verify(long userSn, String email, String purpose, String code) {
		String hmac = codec.hmac(purpose, userSn, email, code);
		Long r = redis.execute(VERIFY_SCRIPT, List.of(KEY_CHALLENGE + userSn),
				String.valueOf(maxAttempts), hmac, normalizeEmail(email), purpose);
		long result = r == null ? -2L : r;
		if (result == 1L) {
			return VerifyResult.OK;
		}
		if (result == -1L) {
			return VerifyResult.TOO_MANY;
		}
		if (result == -2L) {
			return VerifyResult.EXPIRED;
		}
		return VerifyResult.INVALID;
	}

	/** UTC 기준 자정까지 남은 초 — 일일 카운터 EXPIRE(날짜 경계에 맞춤). */
	private static long secondsUntilUtcMidnight() {
		long now = Instant.now().getEpochSecond();
		long midnight = LocalDate.now(ZoneOffset.UTC).plusDays(1).atStartOfDay(ZoneOffset.UTC).toEpochSecond();
		long remain = midnight - now;
		return remain > 0 ? remain : 1L;
	}

	private static String normalizeEmail(String email) {
		return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
	}
}
