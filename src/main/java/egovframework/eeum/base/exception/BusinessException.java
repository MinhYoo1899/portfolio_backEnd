package egovframework.eeum.base.exception;

import lombok.Getter;

/**
 * 업무 예외 베이스. {@link ErrorCode}를 담아 {@code GlobalExceptionHandler}가 envelope + HTTP status 로 변환한다.
 * 서비스/컨트롤러는 이 예외(또는 도메인 하위 예외)를 던진다.
 */
@Getter
public class BusinessException extends RuntimeException {

	private final transient ErrorCode errorCode;

	public BusinessException(ErrorCode errorCode) {
		super(errorCode.defaultMessage());
		this.errorCode = errorCode;
	}

	/** 사용자 노출용 메시지를 지정. (메시지는 반드시 사용자 안전한 내용만 — 내부정보·민감정보 금지) */
	public BusinessException(ErrorCode errorCode, String message) {
		super(message);
		this.errorCode = errorCode;
	}

	/** 원인 예외 보존(장애 분석용). cause 는 응답에 노출하지 않고 로그에만 남는다. */
	public BusinessException(ErrorCode errorCode, String message, Throwable cause) {
		super(message, cause);
		this.errorCode = errorCode;
	}

	// ---- 공통 오류 편의 팩토리 (conventions.md 예외 계층) ----

	public static BusinessException unauthorized() {
		return new BusinessException(CommonErrorCode.UNAUTHORIZED);
	}

	public static BusinessException forbidden() {
		return new BusinessException(CommonErrorCode.FORBIDDEN);
	}

	public static BusinessException notFound(String message) {
		return new BusinessException(CommonErrorCode.NOT_FOUND, message);
	}

	public static BusinessException conflict(String message) {
		return new BusinessException(CommonErrorCode.CONFLICT, message);
	}
}
