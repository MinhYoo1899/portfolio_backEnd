package egovframework.eeum.base.crypto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Base64;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 검색해시 FieldHasher(HMAC-SHA256, crypto-standard.md §3) 검증 — hex64·결정성·키 의존·fail-closed.
 */
class FieldHasherVerification {

	private static String key(byte fill) {
		byte[] raw = new byte[32];
		java.util.Arrays.fill(raw, fill);
		return Base64.getEncoder().encodeToString(raw);
	}

	private final FieldHasher hasher = new FieldHasher(new FieldHmacKeyProvider(key((byte) 1)));

	@Test
	@DisplayName("출력 = hex 소문자 64자, 같은 입력 → 같은 해시(결정성)")
	void hexLower64AndDeterministic() {
		String h1 = hasher.hmacHex("01012345678");
		String h2 = hasher.hmacHex("01012345678");
		assertEquals(h1, h2);
		assertEquals(64, h1.length());
		assertTrue(h1.matches("[0-9a-f]{64}"), "hex 소문자 64자");
	}

	@Test
	@DisplayName("다른 입력 → 다른 해시")
	void differentInputDiffers() {
		assertNotEquals(hasher.hmacHex("01012345678"), hasher.hmacHex("01087654321"));
	}

	@Test
	@DisplayName("다른 키 → 다른 해시(키 의존)")
	void keyDependent() {
		FieldHasher other = new FieldHasher(new FieldHmacKeyProvider(key((byte) 2)));
		assertNotEquals(hasher.hmacHex("01012345678"), other.hmacHex("01012345678"));
	}

	@Test
	@DisplayName("키 미설정 — 해시 계산 시 fail-closed(FieldCryptoException)")
	void unsetKeyFailsClosed() {
		FieldHasher noKey = new FieldHasher(new FieldHmacKeyProvider(""));
		assertThrows(FieldCryptoException.class, () -> noKey.hmacHex("01012345678"));
	}

	@Test
	@DisplayName("null/빈 입력 — 정규화 전 우회 차단(FieldCryptoException)")
	void nullOrEmptyRejected() {
		assertThrows(FieldCryptoException.class, () -> hasher.hmacHex(null));
		assertThrows(FieldCryptoException.class, () -> hasher.hmacHex(""));
	}
}
