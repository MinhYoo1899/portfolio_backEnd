package egovframework.eeum.base.security;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import egovframework.eeum.base.exception.BusinessException;
import egovframework.eeum.base.exception.CommonErrorCode;

/**
 * 비밀번호 정책 검증 — 8~64자, 2종 이상, BCrypt 72바이트(멀티바이트) 한계.
 */
class PasswordPolicyVerification {

	private final PasswordPolicy policy = new PasswordPolicy();

	@ParameterizedTest
	@ValueSource(strings = {
			"abcd1234",        // 영문+숫자 8자
			"pw12!@#$",        // 영문+숫자+특수
			"abcd!@#$",        // 영문+특수
			"12345678901234567890123456789012345678901234567890123456789012ab" // 64자 경계
	})
	@DisplayName("정책 통과 — 8~64자 + 2종 이상")
	void accepts(String password) {
		assertDoesNotThrow(() -> policy.validate(password));
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"ab1!",            // 너무 짧음(4자)
			"abcdefgh",        // 영문 1종만
			"12345678",        // 숫자 1종만
			"!!!!!!!!"         // 특수 1종만
	})
	@DisplayName("정책 위반 — 길이 미달 또는 1종만 → VALIDATION_ERROR")
	void rejects(String password) {
		BusinessException e = assertThrows(BusinessException.class, () -> policy.validate(password));
		org.junit.jupiter.api.Assertions.assertEquals(CommonErrorCode.VALIDATION_ERROR, e.getErrorCode());
	}

	@Test
	@DisplayName("정책 위반 — null/빈값")
	void rejectsBlank() {
		assertThrows(BusinessException.class, () -> policy.validate(null));
		assertThrows(BusinessException.class, () -> policy.validate("   "));
	}

	@Test
	@DisplayName("정책 위반 — 65자 초과(상한)")
	void rejectsTooLong() {
		String over = "a1".repeat(33); // 66자
		assertThrows(BusinessException.class, () -> policy.validate(over));
	}

	@Test
	@DisplayName("정책 위반 — 64자 이내라도 UTF-8 72바이트 초과(한글 등 멀티바이트)면 거부")
	void rejectsOver72Bytes() {
		// 한글 25자(=75바이트) + 숫자 1 → 26자(길이 OK)지만 75바이트 > 72 → 거부
		String multibyte = "가".repeat(25) + "1";
		assertThrows(BusinessException.class, () -> policy.validate(multibyte));
	}

	@Test
	@DisplayName("영문은 ASCII 기준 — 한글은 특수로 분류(한글+숫자=2종 통과, 한글만=1종 거부)")
	void koreanCountedAsSpecialNotLetter() {
		assertDoesNotThrow(() -> policy.validate("가나다라마123")); // 8자: 특수(한글)+숫자 = 2종
		assertThrows(BusinessException.class, () -> policy.validate("가나다라마바사아")); // 8자: 특수 1종 → 거부
	}
}
