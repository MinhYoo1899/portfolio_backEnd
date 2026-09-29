package egovframework.eeum.base.crypto;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Base64;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * HMAC 키 공급자 검증 — base64 32바이트 검증, 미설정 시 부팅 무해+사용 시 fail-closed, 형식오류 시 생성 예외.
 */
class FieldHmacKeyProviderVerification {

	private static String validKey() {
		return Base64.getEncoder().encodeToString(new byte[32]);
	}

	@Test
	@DisplayName("유효 키(base64 32B) — configured, 키 반환")
	void validKeyConfigured() {
		FieldHmacKeyProvider p = new FieldHmacKeyProvider(validKey());
		assertTrue(p.isConfigured());
		assertNotNull(p.hmacKey());
	}

	@Test
	@DisplayName("미설정('') — 부팅 무해(configured=false), 사용 시 fail-closed")
	void unsetLenientBootFailClosedOnUse() {
		FieldHmacKeyProvider p = new FieldHmacKeyProvider("");
		assertFalse(p.isConfigured());
		assertThrows(FieldCryptoException.class, p::hmacKey);
	}

	@Test
	@DisplayName("형식 오류(비base64/길이 불일치) — 생성 시 예외(의도적 오설정)")
	void malformedKeyThrowsOnConstruction() {
		assertThrows(FieldCryptoException.class, () -> new FieldHmacKeyProvider("not*base64*"));
		// 16바이트(길이 부족)
		assertThrows(FieldCryptoException.class,
				() -> new FieldHmacKeyProvider(Base64.getEncoder().encodeToString(new byte[16])));
	}
}
