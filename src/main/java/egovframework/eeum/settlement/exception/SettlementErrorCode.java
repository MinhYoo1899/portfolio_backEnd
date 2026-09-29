package egovframework.eeum.settlement.exception;

import org.springframework.http.HttpStatus;

import egovframework.eeum.base.exception.ErrorCode;

/**
 * 정산 도메인 오류코드.
 */
public enum SettlementErrorCode implements ErrorCode {

	/** 정산 설정 행 없음(초기 seed 누락 — 정상 운영에선 발생 안 함). */
	SETTLEMENT_SETTING_NOT_FOUND("SETTLEMENT_SETTING_NOT_FOUND", "정산 설정을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),

	/** 정산 회차(YYYY-MM) 형식 오류. */
	SETTLEMENT_ROUND_INVALID("SETTLEMENT_ROUND_INVALID", "정산 회차 형식이 올바르지 않습니다.", HttpStatus.BAD_REQUEST),

	/** 설정값 범위 오류(수수료율 0~100, 일수/금액 음수 등). */
	SETTLEMENT_SETTING_INVALID("SETTLEMENT_SETTING_INVALID", "정산 설정 값이 올바르지 않습니다.", HttpStatus.BAD_REQUEST),

	/** 이미 정산 완료된 회차·파트너(중복 실행). */
	SETTLEMENT_ALREADY_DONE("SETTLEMENT_ALREADY_DONE", "이미 정산 완료된 대상입니다.", HttpStatus.CONFLICT),

	/** 정산 대상 없음(해당 회차 파트너 판매 집계 없음). */
	SETTLEMENT_TARGET_NOT_FOUND("SETTLEMENT_TARGET_NOT_FOUND", "정산 대상을 찾을 수 없습니다.", HttpStatus.NOT_FOUND);

	private final String code;
	private final String defaultMessage;
	private final HttpStatus httpStatus;

	SettlementErrorCode(String code, String defaultMessage, HttpStatus httpStatus) {
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
