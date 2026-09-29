package egovframework.eeum.account.service.email.impl;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * 이메일 인증코드의 순수 로직 — 6자리 코드 생성 + HMAC 계산(Redis 의존 없음, 단위테스트 대상).
 *
 * <p>보안(codex MUST):
 * <ul>
 *   <li>코드 = {@link SecureRandom} 6자리(000000~999999, 0 패딩) — 추측 가능한 PRNG 금지.</li>
 *   <li>저장값 = {@code HMAC-SHA256(secret, purpose|userSn|email|code)} hex — 평문 코드 미저장.</li>
 *   <li>HMAC 입력에 purpose 를 포함해 동일 secret 재사용(도메인 혼용)을 완화한다.</li>
 *   <li>secret 은 UTF-8 32바이트 이상 강제(별도 secret 설정 시에도 JWT 수준 강도 보장 — codex MUST).</li>
 * </ul>
 *
 * <p>challengeId 는 쓰지 않는다 — 챌린지 키가 사용자당 1개({@code ...:{userSn}})라 재발급 시 키 교체로 이전 코드가
 * 무효화되고, HMAC 강도는 secret 이 책임진다. (검증 시 cid 선조회로 생기던 경합도 제거 — codex SHOULD.)
 */
public final class EmailVerificationCodec {

	private static final String HMAC_ALGORITHM = "HmacSHA256";
	private static final int CODE_BOUND = 1_000_000; // 6자리(0~999999)
	private static final int MIN_SECRET_BYTES = 32;
	private static final char SEP = '|';

	private final byte[] secret;
	private final SecureRandom secureRandom = new SecureRandom();

	public EmailVerificationCodec(String secret) {
		byte[] bytes = secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
		if (bytes.length < MIN_SECRET_BYTES) {
			throw new IllegalArgumentException(
					"email verification HMAC secret must be >= " + MIN_SECRET_BYTES + " bytes (got " + bytes.length + ")");
		}
		this.secret = bytes;
	}

	/** SecureRandom 6자리 코드(0 패딩). */
	public String generateCode() {
		int n = secureRandom.nextInt(CODE_BOUND);
		return String.format("%06d", n);
	}

	/** {@code HMAC-SHA256(secret, purpose|userSn|email|code)} 의 hex. */
	public String hmac(String purpose, long userSn, String email, String code) {
		String message = purpose + SEP + userSn + SEP + normalizeEmail(email) + SEP + code;
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

	private static String normalizeEmail(String email) {
		return email == null ? "" : email.trim().toLowerCase(java.util.Locale.ROOT);
	}

	private static String toHex(byte[] bytes) {
		StringBuilder sb = new StringBuilder(bytes.length * 2);
		for (byte b : bytes) {
			sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
		}
		return sb.toString();
	}
}
