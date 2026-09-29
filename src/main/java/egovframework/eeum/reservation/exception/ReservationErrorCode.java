package egovframework.eeum.reservation.exception;

import org.springframework.http.HttpStatus;

import egovframework.eeum.base.exception.ErrorCode;

/**
 * 예약(RESERVATION) 도메인 오류코드. ({@link egovframework.eeum.house.exception.RoomErrorCode} 스타일)
 */
public enum ReservationErrorCode implements ErrorCode {

	/** 캘린더 행 없음/마감(CLOSED) — 판매 불가한 숙박일 포함(없는 행 = 판매 불가). */
	RESERVATION_ROOM_UNAVAILABLE("RESERVATION_ROOM_UNAVAILABLE", "선택한 날짜는 예약할 수 없습니다.", HttpStatus.CONFLICT),

	/** 임시점유(hold) 용량 부족 — 요청 수량만큼 남은 재고가 없음. */
	RESERVATION_SOLD_OUT("RESERVATION_SOLD_OUT", "선택한 날짜의 잔여 객실이 없습니다.", HttpStatus.CONFLICT),

	/** 기간 오류 — 체크아웃<=체크인이거나 과거 날짜. */
	RESERVATION_INVALID_RANGE("RESERVATION_INVALID_RANGE", "예약 기간이 올바르지 않습니다.", HttpStatus.BAD_REQUEST),

	/** 예약 없음/타 소유(예약자 본인 아님). */
	RESERVATION_NOT_FOUND("RESERVATION_NOT_FOUND", "예약을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),

	/** 멱등키 UNIQUE 위반 — 같은 예약자의 (RESERVER_USER_SN, IDEMPOTENCY_KEY) 스코프에서 이미 처리된 생성 요청(전역 아님). */
	RESERVATION_DUPLICATE("RESERVATION_DUPLICATE", "이미 처리된 예약 요청입니다.", HttpStatus.CONFLICT),

	/** 게스트 구성 오류 — 예약자(RESERVER) 정확히 1명이 아님. */
	RESERVATION_GUEST_INVALID("RESERVATION_GUEST_INVALID", "예약자 정보가 올바르지 않습니다.", HttpStatus.BAD_REQUEST),

	/** 주문번호 생성 재시도 소진 — 서버측 채번 충돌이 반복(사실상 도달 불가). 잠시 후 재시도 유도. */
	RESERVATION_ORDER_NO_CONFLICT("RESERVATION_ORDER_NO_CONFLICT", "예약번호 생성에 실패했습니다. 잠시 후 다시 시도해 주세요.",
			HttpStatus.INTERNAL_SERVER_ERROR),

	/** 불법 상태전이 — 확정(RESERVED)은 HOLD/PAYMENT_PENDING 에서만 허용(CANCELLED/EXPIRED/USED 등에서 시도 금지). */
	RESERVATION_INVALID_STATE("RESERVATION_INVALID_STATE", "현재 상태에서는 예약을 확정할 수 없습니다.", HttpStatus.CONFLICT);

	private final String code;
	private final String defaultMessage;
	private final HttpStatus httpStatus;

	ReservationErrorCode(String code, String defaultMessage, HttpStatus httpStatus) {
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
