package egovframework.eeum.terms.exception;

import org.springframework.http.HttpStatus;

import egovframework.eeum.base.exception.ErrorCode;

/**
 * 약관 도메인 오류코드 (DOMAIN_REASON 형식 — conventions.md).
 */
public enum TermsErrorCode implements ErrorCode {

	/** 약관 없음(또는 시행중·사용중·미삭제 아님). */
	TERMS_NOT_FOUND("TERMS_NOT_FOUND", "약관을 찾을 수 없습니다.", HttpStatus.NOT_FOUND);

	private final String code;
	private final String defaultMessage;
	private final HttpStatus httpStatus;

	TermsErrorCode(String code, String defaultMessage, HttpStatus httpStatus) {
		this.code = code;
		this.defaultMessage = defaultMessage;
		this.httpStatus = httpStatus;
	}

	@Override
	public String code() {
		return code;
	}

	@Override
	public String defaultMessage() {
		return defaultMessage;
	}

	@Override
	public HttpStatus httpStatus() {
		return httpStatus;
	}
}
