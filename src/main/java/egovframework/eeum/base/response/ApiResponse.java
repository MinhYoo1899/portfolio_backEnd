package egovframework.eeum.base.response;

/**
 * 공통 API 응답 envelope.
 *
 * <p>형태는 전자정부 표준 {@code ResultVO}와 동일({@code resultCode}/{@code resultMessage}/{@code result})하되,
 * DTO/VO 필수 규칙에 따라 {@code result}는 타입드(Map 금지)다. (architecture D5/D20, conventions.md)
 *
 * @param <T> 결과 데이터 타입(타입드 DTO/VO)
 */
public record ApiResponse<T>(
		String resultCode,
		String resultMessage,
		T result) {

	/** 성공 결과코드. */
	public static final String SUCCESS_CODE = "SUCCESS";
	private static final String DEFAULT_SUCCESS_MESSAGE = "정상 처리되었습니다.";

	/** 데이터 포함 성공 응답. */
	public static <T> ApiResponse<T> ok(T result) {
		return new ApiResponse<>(SUCCESS_CODE, DEFAULT_SUCCESS_MESSAGE, result);
	}

	/** 메시지 + 데이터 성공 응답. */
	public static <T> ApiResponse<T> ok(String resultMessage, T result) {
		return new ApiResponse<>(SUCCESS_CODE, resultMessage, result);
	}

	/** 데이터 없는 성공 응답. */
	public static ApiResponse<Void> ok() {
		return new ApiResponse<>(SUCCESS_CODE, DEFAULT_SUCCESS_MESSAGE, null);
	}

	/** 임의 결과코드 응답(주로 오류 — GlobalExceptionHandler 에서 사용). */
	public static <T> ApiResponse<T> of(String resultCode, String resultMessage, T result) {
		return new ApiResponse<>(resultCode, resultMessage, result);
	}
}
