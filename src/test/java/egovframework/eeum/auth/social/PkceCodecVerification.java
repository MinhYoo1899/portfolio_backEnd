package egovframework.eeum.auth.social;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * PKCE(RFC 7636) 순수 로직 검증 — verifier 형식·범위 + S256 challenge 정합(외부 의존 없음).
 */
class PkceCodecVerification {

	private static final Pattern URL_SAFE = Pattern.compile("[A-Za-z0-9_-]+");

	@Test
	@DisplayName("code_verifier — URL-safe 무패딩, 43~128자(RFC 7636 범위)")
	void verifierFormatAndLength() {
		for (int i = 0; i < 500; i++) {
			String v = PkceCodec.generateCodeVerifier();
			assertTrue(URL_SAFE.matcher(v).matches(), "URL-safe: " + v);
			assertTrue(v.length() >= 43 && v.length() <= 128, "43~128자: " + v.length());
		}
	}

	@Test
	@DisplayName("code_verifier — 매 호출 달라짐(난수)")
	void verifierIsRandom() {
		assertNotEquals(PkceCodec.generateCodeVerifier(), PkceCodec.generateCodeVerifier());
	}

	@Test
	@DisplayName("S256 challenge = BASE64URL(SHA-256(verifier)) — 알려진 값과 일치")
	void challengeMatchesManualSha256() throws NoSuchAlgorithmException {
		String verifier = PkceCodec.generateCodeVerifier();
		String challenge = PkceCodec.codeChallengeS256(verifier);

		MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
		byte[] digest = sha256.digest(verifier.getBytes(StandardCharsets.US_ASCII));
		String expected = Base64.getUrlEncoder().withoutPadding().encodeToString(digest);

		assertEquals(expected, challenge);
		assertTrue(URL_SAFE.matcher(challenge).matches(), "challenge URL-safe");
	}

	@Test
	@DisplayName("S256 — RFC 7636 부록 B 표준 테스트벡터와 일치")
	void challengeMatchesRfcTestVector() {
		// RFC 7636 Appendix B
		String verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";
		String expectedChallenge = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM";
		assertEquals(expectedChallenge, PkceCodec.codeChallengeS256(verifier));
	}
}
