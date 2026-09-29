package egovframework.eeum.base.crypto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 휴대폰 마스킹({@link PhoneMasker}) 검증 — 표준/하이픈/짧은값/비표준/null.
 */
class PhoneMaskerVerification {

	@Test
	@DisplayName("표준 휴대폰(하이픈 없음/있음/구분자 혼합) — 010-****-5678")
	void standardMobile() {
		assertEquals("010-****-5678", PhoneMasker.mask("01012345678"));
		assertEquals("010-****-5678", PhoneMasker.mask("010-1234-5678"));
		assertEquals("010-****-5678", PhoneMasker.mask(" 010 1234 5678 "));
	}

	@Test
	@DisplayName("10자리(예: 지역번호) — 앞3-****-뒤4")
	void tenDigits() {
		assertEquals("021-****-5678", PhoneMasker.mask("0212345678"));
	}

	@Test
	@DisplayName("null/빈문자열 — 그대로(null→null, \"\"→\"\", 공백→\"\")")
	void nullAndEmpty() {
		assertNull(PhoneMasker.mask(null));
		assertEquals("", PhoneMasker.mask(""));
		assertEquals("", PhoneMasker.mask("   "));
	}

	@Test
	@DisplayName("비표준/짧은 값 — 보수적으로 전부 마스킹(****), 전체값·자릿수 비노출")
	void nonStandardConservative() {
		assertEquals("****", PhoneMasker.mask("123"));
		assertEquals("****", PhoneMasker.mask("12345"));
		assertEquals("****", PhoneMasker.mask("821012345678")); // 국가코드 포함 12자리
		assertEquals("****", PhoneMasker.mask("abc"));          // 숫자 없음
	}
}
