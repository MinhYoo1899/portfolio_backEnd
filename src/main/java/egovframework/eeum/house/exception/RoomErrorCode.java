package egovframework.eeum.house.exception;

import org.springframework.http.HttpStatus;

import egovframework.eeum.base.exception.ErrorCode;

/**
 * 객실(ROOM) 도메인 오류코드. 부모 고택 소유권 실패는 {@link HouseErrorCode} 재사용.
 */
public enum RoomErrorCode implements ErrorCode {

	/** 객실 없음/삭제됨/타 소유. */
	ROOM_NOT_FOUND("ROOM_NOT_FOUND", "객실을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),

	/** 같은 고택 내 객실명 중복. */
	ROOM_NAME_DUPLICATE("ROOM_NAME_DUPLICATE", "이미 등록한 객실명입니다.", HttpStatus.CONFLICT),

	/** 객실 이미지 없음/삭제됨/타 객실 소속. */
	ROOM_IMAGE_NOT_FOUND("ROOM_IMAGE_NOT_FOUND", "객실 이미지를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),

	/** 캘린더 기간 설정 오류(시작>종료, 범위 초과 등). */
	CALENDAR_INVALID_RANGE("CALENDAR_INVALID_RANGE", "캘린더 기간 설정이 올바르지 않습니다.", HttpStatus.BAD_REQUEST),

	/** 이미 판매된 수량보다 적게 재고를 설정할 수 없음. */
	CALENDAR_TOTAL_BELOW_SOLD("CALENDAR_TOTAL_BELOW_SOLD", "이미 판매된 수량보다 적게 설정할 수 없습니다.", HttpStatus.CONFLICT),

	/** 열기(open) 요청에 캘린더 행이 없는 날짜 포함 — 원가격/원수량이 없어 열 수 없음(가격 설정 선행 필요). */
	CALENDAR_ROW_NOT_FOUND("CALENDAR_ROW_NOT_FOUND", "설정되지 않은 날짜는 열 수 없습니다(가격 설정이 필요합니다).", HttpStatus.NOT_FOUND),

	/** 인원 설정 오류(기준>최대 등). */
	ROOM_PERSON_INVALID("ROOM_PERSON_INVALID", "최대인원은 기준인원 이상이어야 합니다.", HttpStatus.BAD_REQUEST),

	/** 임시조치(SUSPENDED)된 객실은 판매자가 직접 상태를 되돌릴 수 없음. */
	ROOM_SUSPENDED("ROOM_SUSPENDED", "임시중지된 객실은 운영자만 해제할 수 있습니다.", HttpStatus.CONFLICT),

	/** 침구타입코드가 COMM_CD BEDDING_TYPE 에 없거나 중복. */
	ROOM_BEDDING_TYPE_INVALID("ROOM_BEDDING_TYPE_INVALID", "침구타입이 올바르지 않습니다.", HttpStatus.BAD_REQUEST),

	/** 추가옵션명 중복(같은 객실 내, trim/대소문자 무시). */
	ROOM_OPTION_DUPLICATE("ROOM_OPTION_DUPLICATE", "중복된 옵션명이 있습니다.", HttpStatus.BAD_REQUEST),

	/** 추가옵션 값 오류(옵션명 공백 등). */
	ROOM_OPTION_INVALID("ROOM_OPTION_INVALID", "추가옵션 값이 올바르지 않습니다.", HttpStatus.BAD_REQUEST),

	/** 냉난방시설 코드가 COMM_CD(COOLING_TYPE/HEATING_TYPE)에 없거나 그룹 불일치/중복. */
	ROOM_CLIMATE_CD_INVALID("ROOM_CLIMATE_CD_INVALID", "냉난방시설 코드가 올바르지 않습니다.", HttpStatus.BAD_REQUEST);

	private final String code;
	private final String defaultMessage;
	private final HttpStatus httpStatus;

	RoomErrorCode(String code, String defaultMessage, HttpStatus httpStatus) {
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
