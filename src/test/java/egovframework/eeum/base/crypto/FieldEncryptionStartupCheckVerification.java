package egovframework.eeum.base.crypto;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Base64;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Phase C fail-fast({@link FieldEncryptionStartupCheck}) 검증 — 키 설정 시 통과, 미설정 시 부팅 단계 예외.
 * (테스트는 명시 키를 주입한다 — 실키 커밋 금지.)
 */
class FieldEncryptionStartupCheckVerification {

	private static final String KEY_V1 = Base64.getEncoder().encodeToString(new byte[32]); // 테스트 전용

	@Test
	@DisplayName("키 설정됨 — verifyKeyConfigured 통과(부팅 OK)")
	void passesWhenKeyConfigured() {
		FieldEncryptionStartupCheck check = new FieldEncryptionStartupCheck(new FieldEncryptionKeyProvider(KEY_V1));
		assertDoesNotThrow(check::verifyKeyConfigured);
	}

	@Test
	@DisplayName("키 미설정 — verifyKeyConfigured 가 FieldCryptoException 으로 부팅 실패 승격")
	void failsFastWhenKeyAbsent() {
		FieldEncryptionStartupCheck check = new FieldEncryptionStartupCheck(new FieldEncryptionKeyProvider(""));
		assertThrows(FieldCryptoException.class, check::verifyKeyConfigured);
	}
}
