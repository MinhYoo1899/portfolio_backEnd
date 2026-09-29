package egovframework.eeum.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import egovframework.eeum.account.service.email.impl.EmailVerificationCodec;

/**
 * 이메일 인증코드 순수 로직 검증 — SecureRandom 6자리 형식 + HMAC 일치/불일치(Redis 무관).
 */
class EmailVerificationCodecVerification {

	private static final String SECRET = "test-secret-at-least-32-bytes-long-0123456789";
	private static final String PURPOSE = "SIGNUP_EMAIL_VERIFY";
	private static final Pattern SIX_DIGITS = Pattern.compile("\\d{6}");

	private final EmailVerificationCodec codec = new EmailVerificationCodec(SECRET);

	@Test
	@DisplayName("코드 생성 — 항상 6자리 숫자(0 패딩)")
	void generateCodeIsSixDigits() {
		for (int i = 0; i < 1000; i++) {
			String code = codec.generateCode();
			assertEquals(6, code.length(), "코드 길이 6");
			assertTrue(SIX_DIGITS.matcher(code).matches(), "6자리 숫자: " + code);
		}
	}

	@Test
	@DisplayName("HMAC — 동일 입력이면 동일 hex, 일치 비교 true")
	void hmacDeterministicAndMatches() {
		String h1 = codec.hmac(PURPOSE, 7L, "a@b.com", "012345");
		String h2 = codec.hmac(PURPOSE, 7L, "a@b.com", "012345");
		assertEquals(h1, h2);
		assertTrue(EmailVerificationCodec.matches(h1, h2));
	}

	@Test
	@DisplayName("HMAC — 코드/이메일/purpose/userSn 중 하나라도 다르면 hex 달라짐")
	void hmacDiffersOnAnyInput() {
		String base = codec.hmac(PURPOSE, 7L, "a@b.com", "012345");
		assertNotEquals(base, codec.hmac(PURPOSE, 7L, "a@b.com", "999999")); // 코드
		assertNotEquals(base, codec.hmac(PURPOSE, 8L, "a@b.com", "012345")); // userSn
		assertNotEquals(base, codec.hmac(PURPOSE, 7L, "x@y.com", "012345")); // email
		assertNotEquals(base, codec.hmac("OTHER", 7L, "a@b.com", "012345")); // purpose
		assertFalse(EmailVerificationCodec.matches(base, codec.hmac(PURPOSE, 7L, "a@b.com", "999999")));
	}

	@Test
	@DisplayName("HMAC — 이메일은 대소문자/공백 정규화(동일 취급)")
	void emailNormalized() {
		assertEquals(
				codec.hmac(PURPOSE, 7L, "a@b.com", "012345"),
				codec.hmac(PURPOSE, 7L, "  A@B.COM ", "012345"));
	}

	@Test
	@DisplayName("매칭 — null 입력은 false(NPE 없이)")
	void matchesNullSafe() {
		assertFalse(EmailVerificationCodec.matches(null, "x"));
		assertFalse(EmailVerificationCodec.matches("x", null));
	}

	@Test
	@DisplayName("생성자 — 32바이트 미만 secret 거부(빈/공백/null 포함)")
	void shortSecretRejected() {
		assertThrows(IllegalArgumentException.class, () -> new EmailVerificationCodec(""));
		assertThrows(IllegalArgumentException.class, () -> new EmailVerificationCodec("   "));
		assertThrows(IllegalArgumentException.class, () -> new EmailVerificationCodec(null));
		assertThrows(IllegalArgumentException.class, () -> new EmailVerificationCodec("short-31-bytes-0123456789012345")); // 31자
	}
}
