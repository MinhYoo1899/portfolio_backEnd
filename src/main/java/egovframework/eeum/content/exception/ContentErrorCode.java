package egovframework.eeum.content.exception;

import org.springframework.http.HttpStatus;

import egovframework.eeum.base.exception.ErrorCode;

/**
 * 콘텐츠(메인배너 등) 도메인 오류코드.
 */
public enum ContentErrorCode implements ErrorCode {

	/** 콘텐츠(배너) 없음/삭제됨. */
	CONTENT_NOT_FOUND("CONTENT_NOT_FOUND", "콘텐츠를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),

	/** 연결대상코드가 공통코드(CONTENT_LINK_TARGET)에 없음(또는 코드 없이 값만 지정). */
	CONTENT_LINK_TARGET_INVALID("CONTENT_LINK_TARGET_INVALID", "연결 대상 코드가 올바르지 않습니다.", HttpStatus.BAD_REQUEST),

	/** 연결대상코드는 지정됐으나 연결대상값이 비어 있음. */
	CONTENT_LINK_TARGET_VALUE_REQUIRED("CONTENT_LINK_TARGET_VALUE_REQUIRED", "연결 대상 값이 필요합니다.",
			HttpStatus.BAD_REQUEST),

	/** 연결대상값 형식/대상 오류(PRODUCT houseId 미존재/삭제, URL http|https 아님). */
	CONTENT_LINK_TARGET_VALUE_INVALID("CONTENT_LINK_TARGET_VALUE_INVALID", "연결 대상 값이 올바르지 않습니다.",
			HttpStatus.BAD_REQUEST),

	/** 노출기간 역전(종료일 < 시작일). */
	CONTENT_DATE_RANGE_INVALID("CONTENT_DATE_RANGE_INVALID", "노출 종료일은 시작일보다 빠를 수 없습니다.", HttpStatus.BAD_REQUEST),

	/** 이미지 URL이 자체 파일 인프라 반환 경로가 아님(외부 임의 URL 금지). */
	CONTENT_IMAGE_INVALID("CONTENT_IMAGE_INVALID", "허용되지 않은 이미지 경로입니다.", HttpStatus.BAD_REQUEST),

	/** 노출위치코드가 공통코드(AD_BANNER_POSITION)에 없음/누락(광고배너). */
	CONTENT_POSITION_INVALID("CONTENT_POSITION_INVALID", "노출 위치 코드가 올바르지 않습니다.", HttpStatus.BAD_REQUEST),

	/** 팝업 노출위치코드가 공통코드(POPUP_EXPOSE_LOCATION)에 없음/누락. */
	CONTENT_POPUP_LOCATION_INVALID("CONTENT_POPUP_LOCATION_INVALID", "팝업 노출 위치 코드가 올바르지 않습니다.",
			HttpStatus.BAD_REQUEST),

	/** 팝업 노출대상코드가 공통코드(POPUP_EXPOSE_TARGET)에 없음/누락. */
	CONTENT_POPUP_TARGET_INVALID("CONTENT_POPUP_TARGET_INVALID", "팝업 노출 대상 코드가 올바르지 않습니다.",
			HttpStatus.BAD_REQUEST),

	/** 추천고택 분류코드가 공통코드(REC_HOUSE_CATEGORY)에 없음/누락. */
	CONTENT_REC_CATEGORY_INVALID("CONTENT_REC_CATEGORY_INVALID", "추천 분류 코드가 올바르지 않습니다.", HttpStatus.BAD_REQUEST),

	/** 추천고택 대상 고택이 존재하지 않거나 삭제됨. */
	CONTENT_REC_HOUSE_INVALID("CONTENT_REC_HOUSE_INVALID", "추천 대상 고택이 올바르지 않습니다.", HttpStatus.BAD_REQUEST),

	/** 즐길거리 카테고리코드가 공통코드(REGION_CONTENT_CATEGORY)에 없음/누락. */
	CONTENT_REGION_CATEGORY_INVALID("CONTENT_REGION_CATEGORY_INVALID", "카테고리 코드가 올바르지 않습니다.",
			HttpStatus.BAD_REQUEST),

	/** 즐길거리 유형코드가 공통코드(REGION_CONTENT_TYPE)에 없음/누락. */
	CONTENT_REGION_TYPE_INVALID("CONTENT_REGION_TYPE_INVALID", "유형 코드가 올바르지 않습니다.", HttpStatus.BAD_REQUEST),

	/** 즐길거리 지역(시도)코드가 REGION 에 없음/누락. */
	CONTENT_REGION_SIDO_INVALID("CONTENT_REGION_SIDO_INVALID", "지역(시도) 코드가 올바르지 않습니다.", HttpStatus.BAD_REQUEST);

	private final String code;
	private final String defaultMessage;
	private final HttpStatus httpStatus;

	ContentErrorCode(String code, String defaultMessage, HttpStatus httpStatus) {
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
