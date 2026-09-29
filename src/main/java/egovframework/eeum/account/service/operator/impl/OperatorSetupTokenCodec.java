package egovframework.eeum.account.service.operator.impl;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * 운영자 설정토큰의 순수 로직 — 토큰 생성/파싱 + HMAC 계산(Redis 의존 없음, 단위테스트 대상).
 *
 * <p>{@link egovframework.eeum.account.service.email.impl.EmailVerificationCodec} 를 본떴다(보안 동일 원칙):
 * <ul>
 *   <li>토큰 형식 = {@code {userSn}.{secret}} — setup-password 는 token 만 받으므로(body 에 userSn 없음)
 *       토큰이 대상 userSn 을 운반한다. secret = {@link SecureRandom} 32바이트 URL-safe Base64(추측 불가).</li>
 *   <li>저장값 = {@code HMAC-SHA256(serverSecret, userSn|tokenSecret)} hex — 평문 토큰 미저장(로그/저장 금지).</li>
 *   <li>HMAC secret 은 UTF-8 32바이트 이상 강제(JWT 수준 강도 — 별도 secret 설정 시에도).</li>
 * </ul>
 */
public final class OperatorSetupTokenCodec {

	private static final String HMAC_ALGORITHM = "HmacSHA256";
	private static final int TOKEN_BYTES = 32;
	private static final int MIN_SECRET_BYTES = 32;
	private static final char HMAC_SEP = '|';
	private static final char TOKEN_SEP = '.';

	private final byte[] secret;
	private final SecureRandom secureRandom = new SecureRandom();
	private final Base64.Encoder urlEncoder = Base64.getUrlEncoder().withoutPadding();

	public OperatorSetupTokenCodec(String secret) {
		byte[] bytes = secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
		if (bytes.length < MIN_SECRET_BYTES) {
			throw new IllegalArgumentException(
					"operator setup token HMAC secret must be >= " + MIN_SECRET_BYTES + " bytes (got " + bytes.length + ")");
		}
		this.secret = bytes;
	}

	/** 대상 userSn 을 운반하는 설정토큰 {@code {userSn}.{random}} 생성. */
	public IssuedToken generate(long userSn) {
		byte[] raw = new byte[TOKEN_BYTES];
		secureRandom.nextBytes(raw);
		String tokenSecret = urlEncoder.encodeToString(raw);
		String token = userSn + String.valueOf(TOKEN_SEP) + tokenSecret;
		return new IssuedToken(token, hmac(userSn, tokenSecret));
	}

	/** 토큰 파싱 — {@code {userSn}.{secret}}. 형식 오류면 null(무효 처리). */
	public ParsedToken parse(String token) {
		if (token == null) {
			return null;
		}
		int idx = token.indexOf(TOKEN_SEP);
		if (idx <= 0 || idx == token.length() - 1) {
			return null;
		}
		long userSn;
		try {
			userSn = Long.parseLong(token.substring(0, idx));
		} catch (NumberFormatException e) {
			return null;
		}
		String tokenSecret = token.substring(idx + 1);
		return new ParsedToken(userSn, tokenSecret);
	}

	/** {@code HMAC-SHA256(serverSecret, userSn|tokenSecret)} 의 hex. */
	public String hmac(long userSn, String tokenSecret) {
		String message = userSn + String.valueOf(HMAC_SEP) + (tokenSecret == null ? "" : tokenSecret);
		try {
			Mac mac = Mac.getInstance(HMAC_ALGORITHM);
			mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
			return toHex(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
		} catch (java.security.GeneralSecurityException e) {
			throw new IllegalStateException("HMAC-SHA256 unavailable", e);
		}
	}

	/** 상수시간 비교 — 타이밍 공격 완화. */
	public static boolean matches(String expectedHmacHex, String actualHmacHex) {
		if (expectedHmacHex == null || actualHmacHex == null) {
			return false;
		}
		return java.security.MessageDigest.isEqual(
				expectedHmacHex.getBytes(StandardCharsets.UTF_8),
				actualHmacHex.getBytes(StandardCharsets.UTF_8));
	}

	private static String toHex(byte[] bytes) {
		StringBuilder sb = new StringBuilder(bytes.length * 2);
		for (byte b : bytes) {
			sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
		}
		return sb.toString();
	}

	/** 발급 결과 — 평문 토큰(1회 노출) + 저장용 HMAC. */
	public record IssuedToken(String token, String hmac) {
	}

	/** 파싱 결과 — 대상 userSn + 토큰 secret 부분. */
	public record ParsedToken(long userSn, String tokenSecret) {
	}
}
