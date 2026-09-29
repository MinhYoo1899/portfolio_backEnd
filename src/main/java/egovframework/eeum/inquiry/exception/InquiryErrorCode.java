package egovframework.eeum.inquiry.exception;

import org.springframework.http.HttpStatus;

import egovframework.eeum.base.exception.ErrorCode;

/**
 * 문의(CS Q&A) 도메인 오류코드.
 */
public enum InquiryErrorCode implements ErrorCode {

	/** 문의글 없음/삭제됨/열람권한 없음(비밀글·블라인드 비인가 — 열거 방지 위해 단일 404). */
	INQUIRY_NOT_FOUND("INQUIRY_NOT_FOUND", "문의글을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),

	/** 본인 글이 아님(수정/삭제). */
	INQUIRY_FORBIDDEN("INQUIRY_FORBIDDEN", "이 문의글에 대한 권한이 없습니다.", HttpStatus.FORBIDDEN),

	/** 답변완료 후 수정 불가. */
	INQUIRY_LOCKED("INQUIRY_LOCKED", "답변이 등록되어 수정할 수 없습니다.", HttpStatus.CONFLICT),

	/** 답변 없음/삭제됨. */
	INQUIRY_ANSWER_NOT_FOUND("INQUIRY_ANSWER_NOT_FOUND", "답변을 찾을 수 없습니다.", HttpStatus.NOT_FOUND);

	private final String code;
	private final String defaultMessage;
	private final HttpStatus httpStatus;

	InquiryErrorCode(String code, String defaultMessage, HttpStatus httpStatus) {
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
