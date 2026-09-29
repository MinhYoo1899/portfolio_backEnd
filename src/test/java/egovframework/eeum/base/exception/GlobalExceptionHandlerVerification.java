package egovframework.eeum.base.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.servlet.NoHandlerFoundException;

import com.fasterxml.jackson.databind.ObjectMapper;

import egovframework.eeum.base.response.ApiResponse;

/**
 * GlobalExceptionHandler 의 상태코드↔resultCode 매핑 단위 검증 (codex 교차검토 후속 권고).
 */
class GlobalExceptionHandlerVerification {

	private final GlobalExceptionHandler handler = new GlobalExceptionHandler(new ObjectMapper());

	@Test
	@DisplayName("존재하지 않는 경로 → 404 NOT_FOUND envelope")
	void noHandler_404() {
		ResponseEntity<ApiResponse<Void>> response =
				handler.handleNoHandler(new NoHandlerFoundException("GET", "/nowhere", new HttpHeaders()));
		assertThat(response.getStatusCode().value()).isEqualTo(404);
		assertThat(response.getBody().resultCode()).isEqualTo("NOT_FOUND");
	}

	@Test
	@DisplayName("허용되지 않은 메서드 → 405 METHOD_NOT_ALLOWED envelope")
	void methodNotSupported_405() {
		ResponseEntity<ApiResponse<Void>> response =
				handler.handleMethodNotSupported(new HttpRequestMethodNotSupportedException("PUT"));
		assertThat(response.getStatusCode().value()).isEqualTo(405);
		assertThat(response.getBody().resultCode()).isEqualTo("METHOD_NOT_ALLOWED");
	}

	@Test
	@DisplayName("Accept 협상 실패 → 406 NOT_ACCEPTABLE envelope (직접 쓰기 — Accept 무관 JSON 보장)")
	void notAcceptable_406() throws Exception {
		MockHttpServletResponse response = new MockHttpServletResponse();
		handler.handleNotAcceptable(
				new HttpMediaTypeNotAcceptableException("no acceptable representation"), response);
		assertThat(response.getStatus()).isEqualTo(406);
		assertThat(response.getContentType()).contains("application/json");
		assertThat(response.getContentAsString()).contains("\"resultCode\":\"NOT_ACCEPTABLE\"");
	}

	@Test
	@DisplayName("지원하지 않는 Content-Type → 415 UNSUPPORTED_MEDIA_TYPE envelope")
	void mediaTypeNotSupported_415() {
		ResponseEntity<ApiResponse<Void>> response =
				handler.handleMediaTypeNotSupported(new HttpMediaTypeNotSupportedException("text/plain not supported"));
		assertThat(response.getStatusCode().value()).isEqualTo(415);
		assertThat(response.getBody().resultCode()).isEqualTo("UNSUPPORTED_MEDIA_TYPE");
	}

	@Test
	@DisplayName("본문 파싱 실패 → 400 VALIDATION_ERROR envelope")
	void notReadable_400() {
		ResponseEntity<ApiResponse<Void>> response = handler.handleNotReadable(
				new HttpMessageNotReadableException("bad json", new MockHttpInputMessage(new byte[0])));
		assertThat(response.getStatusCode().value()).isEqualTo(400);
		assertThat(response.getBody().resultCode()).isEqualTo("VALIDATION_ERROR");
	}

	@Test
	@DisplayName("업무 예외 → ErrorCode 의 상태/코드 그대로")
	void business_mapsErrorCode() {
		ResponseEntity<ApiResponse<?>> response =
				handler.handleBusiness(BusinessException.conflict("상태 충돌"));
		assertThat(response.getStatusCode().value()).isEqualTo(409);
		assertThat(response.getBody().resultCode()).isEqualTo("CONFLICT");
		assertThat(response.getBody().resultMessage()).isEqualTo("상태 충돌");
		assertThat(response.getBody().result()).isNull(); // ErrorDetail 미구현 예외는 result=null(기존 동작 유지)
	}

	@Test
	@DisplayName("메서드 보안 인가 거부 → 403 FORBIDDEN envelope")
	void accessDenied_403() {
		ResponseEntity<ApiResponse<Void>> response =
				handler.handleAccessDenied(new AccessDeniedException("denied"));
		assertThat(response.getStatusCode().value()).isEqualTo(403);
		assertThat(response.getBody().resultCode()).isEqualTo("FORBIDDEN");
	}

	@Test
	@DisplayName("미처리 예외 → 500 INTERNAL_ERROR — 내부 메시지 미노출 (※ 콘솔의 ERROR 스택트레이스는 의도적 — 핸들러의 error 기록 동작 포함 검증)")
	void unhandled_500_hidesInternalMessage() {
		ResponseEntity<ApiResponse<Void>> response =
				handler.handleException(new RuntimeException("internal secret detail"));
		assertThat(response.getStatusCode().value()).isEqualTo(500);
		assertThat(response.getBody().resultCode()).isEqualTo("INTERNAL_ERROR");
		assertThat(response.getBody().resultMessage()).doesNotContain("internal secret detail");
	}
}
