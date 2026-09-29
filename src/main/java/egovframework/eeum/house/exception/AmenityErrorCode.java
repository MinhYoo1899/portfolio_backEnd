package egovframework.eeum.house.exception;

import org.springframework.http.HttpStatus;

import egovframework.eeum.base.exception.ErrorCode;

/**
 * 편의시설(AMENITY) 도메인 오류코드.
 */
public enum AmenityErrorCode implements ErrorCode {

	/** 편의시설 없음/삭제됨. */
	AMENITY_NOT_FOUND("AMENITY_NOT_FOUND", "편의시설을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),

	/** 편의시설명 중복(활성). */
	AMENITY_NAME_DUPLICATE("AMENITY_NAME_DUPLICATE", "이미 등록된 편의시설명입니다.", HttpStatus.CONFLICT),

	/** 유효하지 않은 분류코드(공통코드 AMENITY_CTGRY 미존재). */
	AMENITY_INVALID_CATEGORY("AMENITY_INVALID_CATEGORY", "유효하지 않은 편의시설 분류입니다.", HttpStatus.BAD_REQUEST);

	private final String code;
	private final String defaultMessage;
	private final HttpStatus httpStatus;

	AmenityErrorCode(String code, String defaultMessage, HttpStatus httpStatus) {
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
