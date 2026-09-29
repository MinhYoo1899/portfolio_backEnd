package egovframework.eeum.base.exception;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.fasterxml.jackson.databind.ObjectMapper;

import egovframework.eeum.base.response.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 전역 예외 처리. 모든 예외를 공통 envelope({@link ApiResponse}) + HTTP status 로 변환한다.
 * (architecture D5/D20, conventions.md §2. codex 교차검토 반영: 401/403·요청검증 예외 확대, 오류 result 타입 명확화)
 *
 * <p>참고: 인증 미통과(401)·인가 거부(403)의 1차 처리는 SecurityFilterChain 의
 * {@code ApiAuthenticationEntryPoint}/{@code ApiAccessDeniedHandler}가 담당하며,
 * 여기서는 메서드 보안(@PreAuthorize) 등으로 컨트롤러까지 전파된 {@link AccessDeniedException}을 보강 처리한다.
 */
@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

	/** 406 직접 쓰기용 (Accept 협상과 무관하게 envelope JSON 보장) */
	private final ObjectMapper objectMapper;

	/**
	 * 업무 예외 → ErrorCode 기반 변환. (응답 메시지는 사용자 안전 메시지, cause 는 로그에만)
	 *
	 * <p>예외가 {@link ErrorDetail} 을 구현하면 구조화 데이터를 {@code result} 로 함께 내려준다
	 * (미구현이면 {@code result=null} — 기존 동작 유지). status/resultCode/resultMessage 는 불변.
	 */
	@ExceptionHandler(BusinessException.class)
	public ResponseEntity<ApiResponse<?>> handleBusiness(BusinessException e) {
		ErrorCode errorCode = e.getErrorCode();
		if (errorCode.httpStatus().is5xxServerError()) {
			log.error("BusinessException(5xx): {}", e.getMessage(), e);
		} else {
			log.warn("BusinessException: {} - {}", errorCode.code(), e.getMessage());
		}
		Object detail = (e instanceof ErrorDetail d) ? d.detail() : null;
		return ResponseEntity.status(errorCode.httpStatus())
				.body(ApiResponse.of(errorCode.code(), e.getMessage(), detail));
	}

	/** 메서드 보안(@PreAuthorize 등) 인가 거부 → 403. */
	@ExceptionHandler(AccessDeniedException.class)
	public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException e) {
		log.warn("AccessDenied: {}", e.getMessage());
		return error(CommonErrorCode.FORBIDDEN, CommonErrorCode.FORBIDDEN.defaultMessage());
	}

	/** @Valid @RequestBody 검증 실패 → 400 + fieldErrors. */
	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiResponse<ValidationErrorResult>> handleMethodArgumentNotValid(
			MethodArgumentNotValidException e) {
		return validationError(e.getBindingResult().getFieldErrors().stream()
				.map(fe -> new FieldErrorDetail(fe.getField(), fe.getDefaultMessage()))
				.toList());
	}

	/** 폼/쿼리 바인딩 검증 실패 → 400 + fieldErrors. */
	@ExceptionHandler(BindException.class)
	public ResponseEntity<ApiResponse<ValidationErrorResult>> handleBind(BindException e) {
		return validationError(e.getBindingResult().getFieldErrors().stream()
				.map(fe -> new FieldErrorDetail(fe.getField(), fe.getDefaultMessage()))
				.toList());
	}

	/** @Validated 파라미터/경로변수 제약 위반 → 400 + fieldErrors. */
	@ExceptionHandler(ConstraintViolationException.class)
	public ResponseEntity<ApiResponse<ValidationErrorResult>> handleConstraintViolation(
			ConstraintViolationException e) {
		return validationError(e.getConstraintViolations().stream()
				.map(v -> new FieldErrorDetail(String.valueOf(v.getPropertyPath()), v.getMessage()))
				.toList());
	}

	/** 필수 요청 파라미터 누락 → 400. */
	@ExceptionHandler(MissingServletRequestParameterException.class)
	public ResponseEntity<ApiResponse<ValidationErrorResult>> handleMissingParam(
			MissingServletRequestParameterException e) {
		return validationError(List.of(new FieldErrorDetail(e.getParameterName(), "필수 파라미터가 누락되었습니다.")));
	}

	/** 파라미터 타입 변환 실패 → 400. */
	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<ApiResponse<ValidationErrorResult>> handleTypeMismatch(
			MethodArgumentTypeMismatchException e) {
		return validationError(List.of(new FieldErrorDetail(e.getName(), "값의 형식이 올바르지 않습니다.")));
	}

	/** 요청 본문 파싱 실패(잘못된 JSON 등) → 400. */
	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ApiResponse<Void>> handleNotReadable(HttpMessageNotReadableException e) {
		log.warn("HttpMessageNotReadable: {}", e.getMessage());
		return error(CommonErrorCode.VALIDATION_ERROR, "요청 본문을 해석할 수 없습니다.");
	}

	/** 존재하지 않는 경로/리소스 → 404. (재검토 반영: 표준 MVC 예외 매핑) */
	@ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
	public ResponseEntity<ApiResponse<Void>> handleNoHandler(Exception e) {
		return error(CommonErrorCode.NOT_FOUND, "요청한 경로를 찾을 수 없습니다.");
	}

	/** 허용되지 않은 HTTP 메서드 → 405. */
	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
		return error(CommonErrorCode.METHOD_NOT_ALLOWED, CommonErrorCode.METHOD_NOT_ALLOWED.defaultMessage());
	}

	/**
	 * 응답 가능한 미디어 타입 없음 → 406.
	 * ResponseEntity 반환 시 Spring 이 Accept(예: XML)와 JSON 의 불일치로 본문을 비워버리므로,
	 * Accept 와 무관하게 envelope JSON 을 응답에 직접 기록한다 (401/403 핸들러와 동일 패턴 — codex 후속 권고 검증으로 발견).
	 */
	@ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
	public void handleNotAcceptable(HttpMediaTypeNotAcceptableException e, HttpServletResponse response)
			throws IOException {
		if (response.isCommitted()) {
			return; // 이미 커밋된 응답은 덮어쓸 수 없음 (codex 권고 가드)
		}
		CommonErrorCode code = CommonErrorCode.NOT_ACCEPTABLE;
		response.setStatus(code.httpStatus().value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		response.addHeader("Vary", "Accept"); // 누적 추가 — setHeader 는 CORS 등이 넣은 기존 Vary 를 덮어씀 (외부 검토 반영)
		objectMapper.writeValue(response.getWriter(), ApiResponse.of(code.code(), code.defaultMessage(), null));
	}

	/** 지원하지 않는 요청 미디어 타입 → 415. */
	@ExceptionHandler(HttpMediaTypeNotSupportedException.class)
	public ResponseEntity<ApiResponse<Void>> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException e) {
		return error(CommonErrorCode.UNSUPPORTED_MEDIA_TYPE, CommonErrorCode.UNSUPPORTED_MEDIA_TYPE.defaultMessage());
	}

	/** 멀티파트 업로드 크기 초과(서블릿 전역 한도) → 413. (파일 도메인 50MB 검증과 별개의 외곽 한도) */
	@ExceptionHandler(MaxUploadSizeExceededException.class)
	public ResponseEntity<ApiResponse<Void>> handleMaxUploadSize(MaxUploadSizeExceededException e) {
		log.warn("MaxUploadSizeExceeded: {}", e.getMessage());
		return error(CommonErrorCode.PAYLOAD_TOO_LARGE, CommonErrorCode.PAYLOAD_TOO_LARGE.defaultMessage());
	}

	/** 미처리 예외 → 500. (내부 원인은 로그에만, 응답은 표준 메시지) */
	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
		log.error("Unhandled exception", e);
		return error(CommonErrorCode.INTERNAL_ERROR, CommonErrorCode.INTERNAL_ERROR.defaultMessage());
	}

	// ---- helpers ----

	private ResponseEntity<ApiResponse<Void>> error(ErrorCode code, String message) {
		return ResponseEntity.status(code.httpStatus()).body(ApiResponse.of(code.code(), message, null));
	}

	private ResponseEntity<ApiResponse<ValidationErrorResult>> validationError(List<FieldErrorDetail> fieldErrors) {
		CommonErrorCode code = CommonErrorCode.VALIDATION_ERROR;
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
				.body(ApiResponse.of(code.code(), code.defaultMessage(), new ValidationErrorResult(fieldErrors)));
	}
}
