package egovframework.eeum.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import egovframework.eeum.account.service.operator.impl.OperatorSetupTokenCodec;

/**
 * 운영자 설정토큰 순수 로직 검증 — 토큰 형식({userSn}.{secret}) + HMAC 일치/불일치 + 파싱(Redis 무관).
 */
class OperatorSetupTokenCodecVerification {

	private static final String SECRET = "test-secret-at-least-32-bytes-long-0123456789";

	private final OperatorSetupTokenCodec codec = new OperatorSetupTokenCodec(SECRET);

	@Test
	@DisplayName("발급 — 토큰은 {userSn}.{secret} 형식이고 저장 HMAC 과 파싱이 일관")
	void generateCarriesUserSnAndHmacMatches() {
		OperatorSetupTokenCodec.IssuedToken issued = codec.generate(42L);
		assertTrue(issued.token().startsWith("42."), "토큰 prefix=userSn");
		OperatorSetupTokenCodec.ParsedToken parsed = codec.parse(issued.token());
		assertNotNull(parsed);
		assertEquals(42L, parsed.userSn());
		// 파싱한 secret 으로 다시 HMAC 하면 발급 시 저장값과 동일
		assertEquals(issued.hmac(), codec.hmac(parsed.userSn(), parsed.tokenSecret()));
		assertTrue(OperatorSetupTokenCodec.matches(issued.hmac(), codec.hmac(parsed.userSn(), parsed.tokenSecret())));
	}

	@Test
	@DisplayName("발급 — 매번 다른 토큰/HMAC(SecureRandom)")
	void generateIsRandom() {
		assertNotEquals(codec.generate(1L).token(), codec.generate(1L).token());
	}

	@Test
	@DisplayName("HMAC — userSn/secret 중 하나라도 다르면 hex 달라짐")
	void hmacDiffersOnAnyInput() {
		String base = codec.hmac(7L, "abc");
		assertNotEquals(base, codec.hmac(8L, "abc"));
		assertNotEquals(base, codec.hmac(7L, "abd"));
		assertFalse(OperatorSetupTokenCodec.matches(base, codec.hmac(8L, "abc")));
	}

	@Test
	@DisplayName("파싱 — 형식 오류(구분자 없음/숫자 아님/빈 secret)는 null")
	void parseRejectsMalformed() {
		assertNull(codec.parse(null));
		assertNull(codec.parse("nodot"));
		assertNull(codec.parse("abc.secret")); // userSn 숫자 아님
		assertNull(codec.parse("12."));        // secret 비어있음
		assertNull(codec.parse(".secret"));    // userSn 비어있음
	}

	@Test
	@DisplayName("매칭 — null 입력은 false(NPE 없이)")
	void matchesNullSafe() {
		assertFalse(OperatorSetupTokenCodec.matches(null, "x"));
		assertFalse(OperatorSetupTokenCodec.matches("x", null));
	}

	@Test
	@DisplayName("생성자 — 32바이트 미만 secret 거부")
	void shortSecretRejected() {
		assertThrows(IllegalArgumentException.class, () -> new OperatorSetupTokenCodec(""));
		assertThrows(IllegalArgumentException.class, () -> new OperatorSetupTokenCodec(null));
		assertThrows(IllegalArgumentException.class, () -> new OperatorSetupTokenCodec("short-31-bytes-0123456789012345"));
	}
}
