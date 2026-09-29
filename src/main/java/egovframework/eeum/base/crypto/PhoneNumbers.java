package egovframework.eeum.base.crypto;

import egovframework.eeum.base.exception.BusinessException;
import egovframework.eeum.base.exception.CommonErrorCode;

/**
 * 휴대전화번호 정규화 순수함수 (crypto-standard.md §3 — 해시 입력 전 필수 정규화).
 *
 * <p>정규화 절차:
 * <ol>
 *   <li>숫자만 추출(하이픈/공백/+ 제거).</li>
 *   <li>선행 국가코드 {@code 82} → {@code 0} 치환 (예: {@code +82 10-1234-5678} → {@code 821012345678} → {@code 01012345678}).</li>
 *   <li>최종값이 {@code 01}로 시작하는 10~11자리인지 검증 — 불일치 시 저장 거부(해시 오염 방지, {@link CommonErrorCode#VALIDATION_ERROR}).</li>
 * </ol>
 *
 * <p>정규화 결과(숫자만, 하이픈 없음)를 암호화 저장값과 해시 입력 모두에 <b>동일하게</b> 사용한다(불일치 = 중복검사 깨짐).
 * 평문 전화번호는 예외 메시지/로그에 담지 않는다.
 */
public final class PhoneNumbers {

	private PhoneNumbers() {
	}

	/**
	 * 휴대전화번호를 정규화한다(crypto-standard §3). 유효하지 않으면 {@link BusinessException}(검증 오류).
	 *
	 * @param raw 입력 전화번호(하이픈/공백/국가코드 허용) — null/blank 불가(호출측이 필수/선택 판단)
	 * @return 정규화된 번호(숫자만, {@code 01}로 시작하는 10~11자리)
	 */
	public static String normalizeMobile(String raw) {
		if (raw == null || raw.isBlank()) {
			throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, "휴대전화번호를 입력해 주세요.");
		}
		String digits = raw.replaceAll("\\D", "");
		if (digits.startsWith("82")) {
			digits = "0" + digits.substring(2);
		}
		if (!digits.matches("01\\d{8,9}")) {
			// 자릿수/형식 힌트 최소화 — 값은 로그/메시지 미포함
			throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, "휴대전화번호 형식이 올바르지 않습니다.");
		}
		return digits;
	}
}
