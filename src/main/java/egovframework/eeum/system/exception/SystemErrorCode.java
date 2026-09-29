package egovframework.eeum.system.exception;

import org.springframework.http.HttpStatus;

import egovframework.eeum.base.exception.ErrorCode;

/**
 * 시스템 설정 도메인 오류코드.
 */
public enum SystemErrorCode implements ErrorCode {

	/** 시스템 설정 행 없음(초기 seed 누락 — 정상 운영에선 발생 안 함). */
	SYSTEM_SETTING_NOT_FOUND("SYSTEM_SETTING_NOT_FOUND", "시스템 설정을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),

	/** 자동 로그아웃 값 미허용(10/30/60/120 외). */
	SYSTEM_AUTO_LOGOUT_INVALID("SYSTEM_AUTO_LOGOUT_INVALID", "자동 로그아웃 값이 올바르지 않습니다.", HttpStatus.BAD_REQUEST),

	/** 비밀번호 변경주기 값 미허용(NONE/M1/M3/M6 외). */
	SYSTEM_PWD_CYCLE_INVALID("SYSTEM_PWD_CYCLE_INVALID", "비밀번호 변경주기 값이 올바르지 않습니다.", HttpStatus.BAD_REQUEST);

	private final String code;
	private final String defaultMessage;
	private final HttpStatus httpStatus;

	SystemErrorCode(String code, String defaultMessage, HttpStatus httpStatus) {
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
