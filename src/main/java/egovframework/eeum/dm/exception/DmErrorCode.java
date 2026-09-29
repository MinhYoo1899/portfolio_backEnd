package egovframework.eeum.dm.exception;

import org.springframework.http.HttpStatus;

import egovframework.eeum.base.exception.ErrorCode;

/**
 * DM 발송 도메인 오류코드.
 */
public enum DmErrorCode implements ErrorCode {

	/** DM 없음/삭제됨. */
	DM_NOT_FOUND("DM_NOT_FOUND", "DM을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),

	/** 채널 코드 미지원(ALIMTALK/SMS 외). */
	DM_CHANNEL_INVALID("DM_CHANNEL_INVALID", "발송 채널이 올바르지 않습니다.", HttpStatus.BAD_REQUEST),

	/** 대상 코드 미지원(ALL/SELECTED 외). */
	DM_TARGET_INVALID("DM_TARGET_INVALID", "발송 대상이 올바르지 않습니다.", HttpStatus.BAD_REQUEST),

	/** 예약 발송 일시 오류(SCHEDULED 인데 미래 아님/누락) 또는 발송시점 코드 오류. */
	DM_SCHEDULED_AT_INVALID("DM_SCHEDULED_AT_INVALID", "예약 발송 일시가 올바르지 않습니다.", HttpStatus.BAD_REQUEST),

	/** 발송/예약 시 내용 필수(임시저장은 완화). */
	DM_CONTENT_REQUIRED("DM_CONTENT_REQUIRED", "발송하려면 내용이 필요합니다.", HttpStatus.BAD_REQUEST),

	/** 이미지 URL이 자체 파일 인프라 경로가 아님. */
	DM_IMAGE_INVALID("DM_IMAGE_INVALID", "허용되지 않은 이미지 경로입니다.", HttpStatus.BAD_REQUEST);

	private final String code;
	private final String defaultMessage;
	private final HttpStatus httpStatus;

	DmErrorCode(String code, String defaultMessage, HttpStatus httpStatus) {
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
