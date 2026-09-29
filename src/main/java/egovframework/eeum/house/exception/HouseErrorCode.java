package egovframework.eeum.house.exception;

import org.springframework.http.HttpStatus;

import egovframework.eeum.base.exception.ErrorCode;

/**
 * 상품(고택/객실) 도메인 오류코드.
 */
public enum HouseErrorCode implements ErrorCode {

	/** 활성 파트너가 아님(상품 등록/관리 불가). */
	HOUSE_NOT_PARTNER("HOUSE_NOT_PARTNER", "상품을 등록·관리할 수 있는 판매자 계정이 아닙니다.", HttpStatus.FORBIDDEN),

	/** 고택 없음/삭제됨. */
	HOUSE_NOT_FOUND("HOUSE_NOT_FOUND", "고택을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),

	/** 본인 소유 고택이 아님. */
	HOUSE_FORBIDDEN("HOUSE_FORBIDDEN", "이 고택에 대한 권한이 없습니다.", HttpStatus.FORBIDDEN),

	/** 같은 파트너 내 고택명 중복. */
	HOUSE_NAME_DUPLICATE("HOUSE_NAME_DUPLICATE", "이미 등록한 고택명입니다.", HttpStatus.CONFLICT),

	/** 고택 이미지 없음/삭제됨/타 고택 소속. */
	HOUSE_IMAGE_NOT_FOUND("HOUSE_IMAGE_NOT_FOUND", "고택 이미지를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),

	/** 임시조치(SUSPENDED)된 고택은 판매자가 직접 상태를 되돌릴 수 없음. */
	HOUSE_SUSPENDED("HOUSE_SUSPENDED", "임시중지된 고택은 운영자만 해제할 수 있습니다.", HttpStatus.CONFLICT),

	/** 예약확정방식 코드가 COMM_CD RSVT_CNFIRM 에 없음. */
	HOUSE_RSVT_CNFIRM_INVALID("HOUSE_RSVT_CNFIRM_INVALID", "예약확정방식 코드가 올바르지 않습니다.", HttpStatus.BAD_REQUEST),

	/** 결제수단 코드가 COMM_CD PAY_MTHD 에 없음(또는 중복). */
	HOUSE_PAY_METHOD_INVALID("HOUSE_PAY_METHOD_INVALID", "결제수단 코드가 올바르지 않습니다.", HttpStatus.BAD_REQUEST),

	/** 게시(발행) 필수조건 미충족 — 메시지에 빠진 항목 명시. */
	HOUSE_NOT_PUBLISHABLE("HOUSE_NOT_PUBLISHABLE", "게시에 필요한 정보가 부족합니다.", HttpStatus.CONFLICT),

	/** 엑셀 내보내기 상한(500건) 초과 — 검색조건을 좁혀야 함. */
	HOUSE_EXPORT_TOO_MANY("HOUSE_EXPORT_TOO_MANY", "내보내기는 최대 500건까지 가능합니다. 검색조건을 좁혀 주세요.", HttpStatus.CONFLICT),

	/** 엑셀 파일 생성 실패(내부 오류). */
	HOUSE_EXPORT_FAILED("HOUSE_EXPORT_FAILED", "엑셀 생성 중 오류가 발생했습니다.", HttpStatus.INTERNAL_SERVER_ERROR),

	/** 상세 drawer 조회 기간 오류(역전 또는 최대 92일 초과). */
	HOUSE_DRAWER_RANGE_INVALID("HOUSE_DRAWER_RANGE_INVALID", "조회 기간이 올바르지 않습니다(최대 92일).",
			HttpStatus.BAD_REQUEST),

	/** 지역코드 오류(미존재 시도/시군구, 또는 시군구가 지정 시도 소속 아님, 또는 시군구만 지정). */
	HOUSE_REGION_INVALID("HOUSE_REGION_INVALID", "지역코드가 올바르지 않습니다.", HttpStatus.BAD_REQUEST),

	/** 검수 상태 전이 불가(현재 상태가 요청 전이의 소스상태가 아님 — 경합 포함). */
	HOUSE_REVIEW_STATE_INVALID("HOUSE_REVIEW_STATE_INVALID", "현재 상태에서 요청한 검수 전이를 수행할 수 없습니다.",
			HttpStatus.CONFLICT),

	/** 정기휴관 요일 코드 오류(MON~SUN 아님). */
	HOUSE_CLOSED_WEEKDAY_INVALID("HOUSE_CLOSED_WEEKDAY_INVALID", "정기휴관 요일 코드가 올바르지 않습니다.",
			HttpStatus.BAD_REQUEST);

	private final String code;
	private final String defaultMessage;
	private final HttpStatus httpStatus;

	HouseErrorCode(String code, String defaultMessage, HttpStatus httpStatus) {
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
