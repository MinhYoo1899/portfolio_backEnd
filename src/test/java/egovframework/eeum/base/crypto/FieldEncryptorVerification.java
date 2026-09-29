package egovframework.eeum.base.crypto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Base64;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 필드 암호화 모듈(Phase A) 단위 검증 — AES-256-GCM round-trip / 랜덤 IV / AAD 바인딩 / 포맷·키 검증 /
 * null·blank 정책 / 키 미설정·미노출. (TypeHandler·Partner wiring 없음)
 */
class FieldEncryptorVerification {

	private static final String FIELD_ID = "PRTNR_MST.PIC_MBL_TELNO";
	private static final String OTHER_FIELD_ID = "USER_ACNT.MBL_TELNO";
	private static final String PLAIN = "01012345678";

	/** 테스트 전용 32바이트 키(base64). 실제 키 아님 — 테스트 코드에만 존재. */
	private static final String KEY_V1 = Base64.getEncoder().encodeToString(new byte[32]);
	private static final String KEY_16 = Base64.getEncoder().encodeToString(new byte[16]);

	private final FieldEncryptionKeyProvider keyProvider = new FieldEncryptionKeyProvider(KEY_V1);
	private final FieldEncryptor encryptor = new FieldEncryptor(keyProvider);

	@Test
	@DisplayName("round-trip — encrypt 후 decrypt 하면 원문 복원")
	void roundTrip() {
		String ct = encryptor.encrypt(PLAIN, FIELD_ID);
		assertEquals(PLAIN, encryptor.decrypt(ct, FIELD_ID));
	}

	@Test
	@DisplayName("같은 평문도 매번 다른 ciphertext (랜덤 IV)")
	void randomIvDiffersEachTime() {
		String a = encryptor.encrypt(PLAIN, FIELD_ID);
		String b = encryptor.encrypt(PLAIN, FIELD_ID);
		assertNotEquals(a, b);
		assertEquals(PLAIN, encryptor.decrypt(a, FIELD_ID));
		assertEquals(PLAIN, encryptor.decrypt(b, FIELD_ID));
	}

	@Test
	@DisplayName("포맷 v1: 프리픽스 + isCipherText 판별")
	void formatPrefix() {
		String ct = encryptor.encrypt(PLAIN, FIELD_ID);
		assertTrue(ct.startsWith("v1:"));
		assertTrue(encryptor.isCipherText(ct));
		assertFalse(encryptor.isCipherText("01012345678")); // 평문은 암호문 아님
		assertFalse(encryptor.isCipherText(null));
		assertFalse(encryptor.isCipherText(""));
	}

	@Test
	@DisplayName("AAD(필드ID)가 다르면 복호화 실패 — 컬럼 간 암호문 이식 차단")
	void aadMismatchFails() {
		String ct = encryptor.encrypt(PLAIN, FIELD_ID);
		assertThrows(FieldCryptoException.class, () -> encryptor.decrypt(ct, OTHER_FIELD_ID));
	}

	@Test
	@DisplayName("잘못된 포맷 복호화 실패 (프리픽스 없음/base64 아님/길이 부족)")
	void invalidFormatFails() {
		assertThrows(FieldCryptoException.class, () -> encryptor.decrypt("not-a-cipher", FIELD_ID));
		assertThrows(FieldCryptoException.class, () -> encryptor.decrypt("v1:!!!not-base64!!!", FIELD_ID));
		assertThrows(FieldCryptoException.class, () -> encryptor.decrypt("v1:", FIELD_ID));
		// IV(12바이트)보다 짧은 본문
		assertThrows(FieldCryptoException.class,
				() -> encryptor.decrypt("v1:" + Base64.getEncoder().encodeToString(new byte[8]), FIELD_ID));
	}

	@Test
	@DisplayName("변조된 ciphertext 복호화 실패 (인증태그 불일치)")
	void tamperedCipherFails() {
		String ct = encryptor.encrypt(PLAIN, FIELD_ID);
		String tampered = ct.substring(0, ct.length() - 1) + (ct.endsWith("A") ? "B" : "A");
		assertThrows(FieldCryptoException.class, () -> encryptor.decrypt(tampered, FIELD_ID));
	}

	@Test
	@DisplayName("잘못된 키 길이(16바이트)는 생성 시 거부 (32바이트 아님)")
	void invalidKeyLengthRejected() {
		assertThrows(FieldCryptoException.class, () -> new FieldEncryptionKeyProvider(KEY_16));
		assertThrows(FieldCryptoException.class, () -> new FieldEncryptionKeyProvider("not-base64!!!"));
	}

	@Test
	@DisplayName("키 미설정이어도 생성/부팅은 깨지지 않음 — 사용 시점에만 예외 (Phase A 정책)")
	void keyAbsentDoesNotBreakConstruction() {
		FieldEncryptionKeyProvider empty = new FieldEncryptionKeyProvider(""); // 부팅 fail-fast 아님
		assertFalse(empty.isConfigured());
		FieldEncryptor enc = new FieldEncryptor(empty);
		assertThrows(FieldCryptoException.class, () -> enc.encrypt(PLAIN, FIELD_ID)); // 사용 시점 예외
		assertThrows(FieldCryptoException.class, empty::requireConfigured);            // 명시 검증은 예외
	}

	@Test
	@DisplayName("null/빈문자열 정책 — 통과(없음), 암호화하지 않음")
	void nullAndBlankPassThrough() {
		assertNull(encryptor.encrypt(null, FIELD_ID));
		assertNull(encryptor.decrypt(null, FIELD_ID));
		assertEquals("", encryptor.encrypt("", FIELD_ID));
		assertEquals("", encryptor.decrypt("", FIELD_ID));
	}

	@Test
	@DisplayName("fieldId(AAD) 공백이면 예외")
	void blankFieldIdRejected() {
		assertThrows(FieldCryptoException.class, () -> encryptor.encrypt(PLAIN, " "));
		assertThrows(FieldCryptoException.class, () -> encryptor.encrypt(PLAIN, null));
	}

	@Test
	@DisplayName("예외 메시지에 평문·키 원문이 포함되지 않음 (로그/노출 방지)")
	void exceptionMessageHasNoSecret() {
		String ct = encryptor.encrypt(PLAIN, FIELD_ID);
		String tampered = ct.substring(0, ct.length() - 1) + (ct.endsWith("A") ? "B" : "A");
		FieldCryptoException e = assertThrows(FieldCryptoException.class, () -> encryptor.decrypt(tampered, FIELD_ID));
		assertFalse(e.getMessage().contains(PLAIN));
		assertFalse(e.getMessage().contains(KEY_V1));
	}

	@Test
	@DisplayName("다른 키로 복호화 실패 (wrong key)")
	void wrongKeyFails() {
		String ct = encryptor.encrypt(PLAIN, FIELD_ID);
		byte[] otherRaw = new byte[32];
		java.util.Arrays.fill(otherRaw, (byte) 7);
		FieldEncryptor other = new FieldEncryptor(
				new FieldEncryptionKeyProvider(Base64.getEncoder().encodeToString(otherRaw)));
		assertThrows(FieldCryptoException.class, () -> other.decrypt(ct, FIELD_ID));
	}

	@Test
	@DisplayName("미지원 키버전 복호화 실패 (v2 미설정)")
	void unsupportedVersionFails() {
		String ct = encryptor.encrypt(PLAIN, FIELD_ID); // v1:
		String forgedVersion = "v2:" + ct.substring(3);
		assertThrows(FieldCryptoException.class, () -> encryptor.decrypt(forgedVersion, FIELD_ID));
	}

	@Test
	@DisplayName("IV+태그 미만 길이 — 포맷 실패 (12 <= body < 28)")
	void shortBodyFails() {
		assertThrows(FieldCryptoException.class,
				() -> encryptor.decrypt("v1:" + Base64.getEncoder().encodeToString(new byte[20]), FIELD_ID));
	}
}
