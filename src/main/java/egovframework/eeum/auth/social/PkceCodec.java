package egovframework.eeum.auth.social;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * PKCE(RFC 7636) 순수 로직 — code_verifier 생성 + S256 code_challenge 산출.
 *
 * <p>verifier 는 43~128자 URL-safe Base64(무패딩, SecureRandom 32바이트 → 43자), challenge=BASE64URL(SHA-256(verifier)).
 * 외부 의존 없는 정적 유틸(단위 검증 용이).
 */
public final class PkceCodec {

	private static final SecureRandom SECURE_RANDOM = new SecureRandom();
	private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();

	private PkceCodec() {
	}

	/** code_verifier 생성 — SecureRandom 32바이트의 URL-safe Base64(무패딩, 43자 — RFC 7636 43~128 범위). */
	public static String generateCodeVerifier() {
		byte[] bytes = new byte[32];
		SECURE_RANDOM.nextBytes(bytes);
		return URL_ENCODER.encodeToString(bytes);
	}

	/** S256 code_challenge = BASE64URL(SHA-256(ASCII(verifier))). */
	public static String codeChallengeS256(String codeVerifier) {
		try {
			MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
			byte[] digest = sha256.digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
			return URL_ENCODER.encodeToString(digest);
		} catch (NoSuchAlgorithmException e) {
			// SHA-256 은 모든 JVM 필수 — 실제로 도달 불가
			throw new IllegalStateException("SHA-256 unavailable", e);
		}
	}
}
