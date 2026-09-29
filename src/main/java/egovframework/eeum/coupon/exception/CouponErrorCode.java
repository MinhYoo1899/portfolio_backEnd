package egovframework.eeum.coupon.exception;

import org.springframework.http.HttpStatus;

import egovframework.eeum.base.exception.ErrorCode;

/**
 * 쿠폰 도메인 오류코드.
 */
public enum CouponErrorCode implements ErrorCode {

	/** 쿠폰 없음/삭제됨. */
	COUPON_NOT_FOUND("COUPON_NOT_FOUND", "쿠폰을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),

	/** 할인유형코드가 공통코드(COUPON_DISCOUNT_TYPE)에 없음. */
	COUPON_DISCOUNT_TYPE_INVALID("COUPON_DISCOUNT_TYPE_INVALID", "할인 유형 코드가 올바르지 않습니다.", HttpStatus.BAD_REQUEST),

	/** 할인값 범위 오류(정률 1~100, 정액 1 이상). */
	COUPON_DISCOUNT_VALUE_INVALID("COUPON_DISCOUNT_VALUE_INVALID", "할인 값이 올바르지 않습니다.", HttpStatus.BAD_REQUEST),

	/** 수취인 코드 미지원(현 단계 ALL만). */
	COUPON_TARGET_TYPE_INVALID("COUPON_TARGET_TYPE_INVALID", "지원하지 않는 수취인입니다.", HttpStatus.BAD_REQUEST),

	/** 사용기간 역전(종료일 < 시작일). */
	COUPON_DATE_RANGE_INVALID("COUPON_DATE_RANGE_INVALID", "사용 종료일은 시작일보다 빠를 수 없습니다.", HttpStatus.BAD_REQUEST);

	private final String code;
	private final String defaultMessage;
	private final HttpStatus httpStatus;

	CouponErrorCode(String code, String defaultMessage, HttpStatus httpStatus) {
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
