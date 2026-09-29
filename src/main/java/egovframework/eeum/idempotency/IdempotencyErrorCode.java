package egovframework.eeum.idempotency;

import org.springframework.http.HttpStatus;

import egovframework.eeum.base.exception.ErrorCode;

/**
 * 멱등키(Idempotency-Key) 처리 오류코드. (공통 {@link ErrorCode} 계약 — code()=name(), CommonErrorCode 패턴)
 *
 * <p>필터가 예외로 던지지 않고 응답을 직접 기록하므로(JwtAuthenticationFilter 방식) GlobalExceptionHandler 경유는 없다.
 */
public enum IdempotencyErrorCode implements ErrorCode {

	/** 같은 Idempotency-Key 인데 요청 본문 해시가 다름(키 오·재사용). */
	IDEMPOTENCY_KEY_REUSED(HttpStatus.CONFLICT, "같은 Idempotency-Key 로 서로 다른 요청 본문이 전송되었습니다."),

	/** 동일 키의 선행 요청이 아직 처리 중(또는 만료 경합). 잠시 후 재시도. */
	IDEMPOTENCY_IN_PROGRESS(HttpStatus.CONFLICT, "동일 요청이 처리 중입니다. 잠시 후 다시 시도해 주세요."),

	/** 멱등 저장소(Redis) 장애 — fail-closed 로 요청 처리 차단(중복 생성 방지). */
	IDEMPOTENCY_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "요청 중복 방지 기능을 일시적으로 사용할 수 없습니다. 잠시 후 다시 시도해 주세요.");

	private final HttpStatus httpStatus;
	private final String defaultMessage;

	IdempotencyErrorCode(HttpStatus httpStatus, String defaultMessage) {
		this.httpStatus = httpStatus;
		this.defaultMessage = defaultMessage;
	}

	@Override
	public String code() {
		return name();
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
