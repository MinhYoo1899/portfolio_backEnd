package egovframework.eeum.base.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 보안 envelope 핸들러(401/403) 단위 검증 — SecurityFilterChain 단계의 표준 응답.
 */
class SecurityEnvelopeHandlerVerification {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	@DisplayName("인증 실패 → 401 + UNAUTHORIZED envelope(JSON)")
	void entryPoint_401_envelope() throws Exception {
		MockHttpServletResponse response = new MockHttpServletResponse();

		new ApiAuthenticationEntryPoint(objectMapper).commence(
				new MockHttpServletRequest(), response,
				new InsufficientAuthenticationException("no token"));

		assertThat(response.getStatus()).isEqualTo(401);
		assertThat(response.getContentType()).contains("application/json");
		assertThat(response.getContentAsString()).contains("\"resultCode\":\"UNAUTHORIZED\"");
	}

	@Test
	@DisplayName("인가 거부 → 403 + FORBIDDEN envelope(JSON)")
	void accessDenied_403_envelope() throws Exception {
		MockHttpServletResponse response = new MockHttpServletResponse();

		new ApiAccessDeniedHandler(objectMapper).handle(
				new MockHttpServletRequest(), response,
				new AccessDeniedException("denied"));

		assertThat(response.getStatus()).isEqualTo(403);
		assertThat(response.getContentType()).contains("application/json");
		assertThat(response.getContentAsString()).contains("\"resultCode\":\"FORBIDDEN\"");
	}
}
