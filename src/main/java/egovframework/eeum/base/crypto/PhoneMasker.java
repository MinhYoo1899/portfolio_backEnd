package egovframework.eeum.base.crypto;

/**
 * 휴대폰 번호 응답 마스킹 순수함수 (crypto-standard.md §3 — 노출 시 마스킹).
 *
 * <p>암호화 저장 필드(예: {@code PRTNR_MST.PIC_MBL_TELNO})를 복호화해 응답에 실을 때, <b>전체값 대신 마스킹값만</b>
 * 반환하기 위한 유틸. 저장(암호화)과 노출(마스킹)을 한 모듈(base.crypto)에 둔다.
 *
 * <ul>
 *   <li>표준 휴대폰(숫자 10~11자리): {@code 010-****-5678} (앞 3 + 마스킹 + 뒤 4).</li>
 *   <li>{@code null → null}, {@code "" → ""} (없음/클리어 그대로).</li>
 *   <li>비표준·짧은 값(숫자 10/11자리 아님): {@code ****} (아무것도 노출하지 않음 — 보수적).</li>
 * </ul>
 *
 * <p>전체값을 반환하는 메서드는 제공하지 않는다(전체값 노출 API 금지 — crypto-standard §3).
 */
public final class PhoneMasker {

	private static final String FULLY_MASKED = "****";

	private PhoneMasker() {
	}

	/**
	 * 휴대폰 번호를 마스킹한다.
	 *
	 * @param phone 평문 휴대폰 번호(복호화된 값) — null/빈문자열 허용
	 * @return 마스킹된 문자열(전체값 아님). null→null, ""→"".
	 */
	public static String mask(String phone) {
		if (phone == null) {
			return null;
		}
		String trimmed = phone.trim();
		if (trimmed.isEmpty()) {
			return trimmed;
		}
		String digits = trimmed.replaceAll("\\D", "");
		if (digits.length() == 10 || digits.length() == 11) {
			String prefix = digits.substring(0, 3);
			String last4 = digits.substring(digits.length() - 4);
			return prefix + "-****-" + last4;
		}
		// 비표준/짧은 값 — 길이·자릿수 힌트도 최소화하여 전부 마스킹(보수적).
		return FULLY_MASKED;
	}
}
