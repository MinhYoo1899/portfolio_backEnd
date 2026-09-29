package egovframework.eeum.base.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * PII 마스커 순수 로직 검증 — 마스킹 대상은 좁게 가리고, 과마스킹 회귀(corrId/주문번호/이름류 훼손)를 방지한다.
 */
class PiiMaskerVerification {

	// --- 마스킹 됨 ---

	@Test
	@DisplayName("이메일 — 첫 글자 + 도메인만 유지")
	void masksEmail() {
		assertEquals("u***@example.com", PiiMasker.mask("user@example.com"));
		assertEquals("j***@eeum.co.kr", PiiMasker.mask("john.doe@eeum.co.kr"));
	}

	@Test
	@DisplayName("휴대폰(국내) — 010-****-1234")
	void masksKoreanPhone() {
		assertEquals("010-****-1234", PiiMasker.mask("010-1234-1234"));
		assertEquals("010-****-5678", PiiMasker.mask("01012345678"));
	}

	@Test
	@DisplayName("휴대폰(+82) 마스킹")
	void masksIntlPhone() {
		assertFalse(PiiMasker.mask("call +82-10-1234-5678 now").contains("1234-5678"));
		assertTrue(PiiMasker.mask("call +82-10-1234-5678 now").contains("***"));
	}

	@Test
	@DisplayName("주민번호(대시) 마스킹")
	void masksRrn() {
		assertEquals("******-*******", PiiMasker.mask("900101-1234567"));
	}

	@Test
	@DisplayName("Bearer 토큰 — 토큰값만 가림")
	void masksBearer() {
		assertEquals("Authorization: bearer ***",
				PiiMasker.mask("Authorization: bearer eyJhbGciOiJIUzI1NiJ9.abc.def"));
	}

	@Test
	@DisplayName("key 기반 민감값 — password/token/cookie/ci/di")
	void masksSensitiveKeys() {
		assertEquals("{\"password\":\"***\"}", PiiMasker.mask("{\"password\":\"hunter2\"}"));
		assertEquals("refreshToken=***", PiiMasker.mask("refreshToken=abc.def.ghi"));
		assertTrue(PiiMasker.mask("Cookie: SESSION=deadbeef").contains("***"));
		assertEquals("ci=***", PiiMasker.mask("ci=ABCDEF1234567890"));
		assertEquals("di=***", PiiMasker.mask("di=ZZZ999"));
	}

	@Test
	@DisplayName("카드(Luhn 통과) — 마지막 4자리만 남김")
	void masksValidCard() {
		// 4111 1111 1111 1111 은 Luhn 통과(테스트 카드)
		assertEquals("***1111", PiiMasker.mask("4111111111111111"));
		assertEquals("card=***1111", PiiMasker.mask("card=4111-1111-1111-1111"));
	}

	// --- 마스킹 안 됨 (과마스킹 회귀 방지) ---

	@Test
	@DisplayName("corrId(UUID) 는 보존 — 절대 깨지면 안 됨")
	void keepsUuid() {
		String uuid = "550e8400-e29b-41d4-a716-446655440000";
		assertEquals(uuid, PiiMasker.mask(uuid));
	}

	@Test
	@DisplayName("32자리 hex 보존")
	void keepsHex() {
		String hex = "9f86d081884c7d659a2feaa0c55ad015";
		assertEquals(hex, PiiMasker.mask(hex));
	}

	@Test
	@DisplayName("주문번호 같은 12~16자리 비-Luhn 숫자 보존")
	void keepsNonLuhnNumbers() {
		// Luhn 불통과 주문번호
		String order = "1234567890123456";
		assertEquals(order, PiiMasker.mask(order));
		assertEquals("ORD-202606230001", PiiMasker.mask("ORD-202606230001"));
	}

	@Test
	@DisplayName("productName/fileName/roleName 보존 — name 광역 매칭 금지")
	void keepsNameFields() {
		String s = "{\"productName\":\"한옥스위트\",\"fileName\":\"photo.jpg\",\"roleName\":\"MEMBER\"}";
		assertEquals(s, PiiMasker.mask(s));
	}

	@Test
	@DisplayName("일반 한글 문장 보존 — 한글 이름 자유탐지 금지")
	void keepsKoreanSentence() {
		String s = "예약이 정상적으로 생성되었습니다 홍길동님 환영합니다";
		assertEquals(s, PiiMasker.mask(s));
	}

	// --- 혼합 ---

	@Test
	@DisplayName("혼합 — 여러 PII 가 섞인 메시지도 각각 마스킹, corrId 는 보존")
	void masksMixed() {
		String input = "corrId=550e8400-e29b-41d4-a716-446655440000 user u***-check=user@example.com "
				+ "phone=010-1234-5678 password=secret1 card=4111111111111111 order=1234567890123456";
		String out = PiiMasker.mask(input);
		assertTrue(out.contains("550e8400-e29b-41d4-a716-446655440000"), "corrId 보존");
		assertTrue(out.contains("u***@example.com"), "이메일 마스킹");
		assertTrue(out.contains("010-****-5678"), "휴대폰 마스킹");
		assertTrue(out.contains("password=***"), "password 마스킹");
		assertTrue(out.contains("***1111"), "카드 마스킹");
		assertTrue(out.contains("1234567890123456"), "비-Luhn 주문번호 보존");
	}

	@Test
	@DisplayName("null/빈 입력 안전")
	void nullSafe() {
		assertEquals(null, PiiMasker.mask(null));
		assertEquals("", PiiMasker.mask(""));
	}
}
