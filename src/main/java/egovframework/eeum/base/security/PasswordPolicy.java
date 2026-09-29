package egovframework.eeum.base.security;

import java.nio.charset.StandardCharsets;

import org.springframework.stereotype.Component;

import egovframework.eeum.base.exception.BusinessException;
import egovframework.eeum.base.exception.CommonErrorCode;

/**
 * 비밀번호 정책 공통 검증 (가입/변경/재설정 공용 — codex 교차검토 권고로 단일화).
 *
 * <p>정책(확정): 8~64자, 영문·숫자·특수문자 중 2종 이상. BCrypt 72바이트 한계 처리(초과분이 조용히 무시되어
 * 보안이 약화되는 것 방지 — 멀티바이트 비밀번호는 64자 이내라도 72바이트를 넘을 수 있음).
 *
 * <p>NIST SP 800-63B 권고는 복잡도 강제보다 길이·차단목록 중심이나, 가입 화면이 이미 "2종 이상"을
 * 약속하고 있어 일관성을 위해 복잡도 2종을 강제한다. (breached-password 차단목록은 SHOULD — 추후)
 */
@Component
public class PasswordPolicy {

	private static final int MIN_LENGTH = 8;
	private static final int MAX_LENGTH = 64;
	/** BCrypt 입력 한계(72바이트). 초과 시 뒷부분이 무시되어 사실상 약한 비밀번호가 되므로 거부. */
	private static final int MAX_BYTES = 72;
	private static final int MIN_CHAR_KINDS = 2;

	/** 정책 위반 시 {@link BusinessException}(VALIDATION_ERROR, 400)을 던진다. */
	public void validate(String password) {
		if (password == null || password.isBlank()) {
			throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, "비밀번호를 입력해 주세요.");
		}
		int length = password.length();
		if (length < MIN_LENGTH || length > MAX_LENGTH) {
			throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, "비밀번호는 " + MIN_LENGTH + "~" + MAX_LENGTH + "자여야 합니다.");
		}
		if (password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
			throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, "비밀번호가 너무 깁니다(UTF-8 " + MAX_BYTES + "바이트 이내).");
		}
		if (characterKinds(password) < MIN_CHAR_KINDS) {
			throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, "비밀번호는 영문·숫자·특수문자 중 2종 이상을 포함해야 합니다.");
		}
	}

	/**
	 * 포함된 문자 종류 수(영문/숫자/그 외=특수) — 0~3.
	 * "영문"은 ASCII {@code [A-Za-z]} 로 본다(설명과 정합 — {@code Character.isLetter}는 한글·중문도 letter 로 세어
	 * "영문" 의미와 어긋나므로 ASCII 로 한정. 한글 등은 특수로 분류됨 — codex 3차 SHOULD).
	 */
	private int characterKinds(String password) {
		boolean hasLetter = false;
		boolean hasDigit = false;
		boolean hasSpecial = false;
		for (int i = 0; i < password.length(); i++) {
			char c = password.charAt(i);
			if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')) {
				hasLetter = true;
			} else if (c >= '0' && c <= '9') {
				hasDigit = true;
			} else {
				hasSpecial = true;
			}
		}
		return (hasLetter ? 1 : 0) + (hasDigit ? 1 : 0) + (hasSpecial ? 1 : 0);
	}
}
