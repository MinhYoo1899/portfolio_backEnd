package egovframework.eeum.base.crypto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import egovframework.eeum.base.exception.BusinessException;
import egovframework.eeum.base.exception.CommonErrorCode;

/**
 * 휴대전화번호 정규화(crypto-standard.md §3) 검증 — 숫자추출·선행82→0·최종 01+10~11자리 검증(불일치 거부).
 */
class PhoneNumbersVerification {

	@Test
	@DisplayName("하이픈/공백 제거 — 010-1234-5678 → 01012345678")
	void stripsSeparators() {
		assertEquals("01012345678", PhoneNumbers.normalizeMobile("010-1234-5678"));
		assertEquals("01012345678", PhoneNumbers.normalizeMobile(" 010 1234 5678 "));
	}

	@Test
	@DisplayName("선행 국가코드 82 → 0 치환 — +82 10-1234-5678 → 01012345678")
	void replacesLeading82() {
		assertEquals("01012345678", PhoneNumbers.normalizeMobile("+82 10-1234-5678"));
		assertEquals("01012345678", PhoneNumbers.normalizeMobile("821012345678"));
	}

	@Test
	@DisplayName("10자리·11자리 모두 허용(01 시작)")
	void allows10And11Digits() {
		assertEquals("0101234567", PhoneNumbers.normalizeMobile("010-123-4567"));   // 10자리
		assertEquals("01012345678", PhoneNumbers.normalizeMobile("010-1234-5678")); // 11자리
	}

	@Test
	@DisplayName("01 미시작/자릿수 불일치/빈값 — 저장 거부(VALIDATION_ERROR)")
	void rejectsInvalid() {
		for (String bad : new String[] {"02-123-4567", "010-123-456", "0212345678", "1012345678", "", "   ", "abcd"}) {
			BusinessException e = assertThrows(BusinessException.class, () -> PhoneNumbers.normalizeMobile(bad));
			assertEquals(CommonErrorCode.VALIDATION_ERROR, e.getErrorCode());
		}
		assertThrows(BusinessException.class, () -> PhoneNumbers.normalizeMobile(null));
	}
}
