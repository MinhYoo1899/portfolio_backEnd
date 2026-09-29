package egovframework.eeum.notice.exception;

import org.springframework.http.HttpStatus;

import egovframework.eeum.base.exception.ErrorCode;

/**
 * 공지사항 도메인 오류코드.
 */
public enum NoticeErrorCode implements ErrorCode {

	/** 공지 없음/삭제됨. */
	NOTICE_NOT_FOUND("NOTICE_NOT_FOUND", "공지를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),

	/** 대상 코드 미지원. */
	NOTICE_TARGET_INVALID("NOTICE_TARGET_INVALID", "대상 코드가 올바르지 않습니다.", HttpStatus.BAD_REQUEST),

	/** 예약 발송 일시 오류(SCHEDULED 인데 미래 일시 아님/누락). */
	NOTICE_SCHEDULED_AT_INVALID("NOTICE_SCHEDULED_AT_INVALID", "예약 발송 일시가 올바르지 않습니다.", HttpStatus.BAD_REQUEST),

	/** 발행/예약 시 내용 필수(임시저장은 완화). */
	NOTICE_CONTENT_REQUIRED("NOTICE_CONTENT_REQUIRED", "발행하려면 내용이 필요합니다.", HttpStatus.BAD_REQUEST),

	/** 종료 불가(게시중 상태 아님). */
	NOTICE_NOT_PUBLISHED("NOTICE_NOT_PUBLISHED", "게시중인 공지만 종료할 수 있습니다.", HttpStatus.CONFLICT);

	private final String code;
	private final String defaultMessage;
	private final HttpStatus httpStatus;

	NoticeErrorCode(String code, String defaultMessage, HttpStatus httpStatus) {
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
