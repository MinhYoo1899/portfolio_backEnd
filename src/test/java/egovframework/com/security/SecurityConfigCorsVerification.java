package egovframework.com.security;

import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import egovframework.eeum.base.security.ApiAccessDeniedHandler;
import egovframework.eeum.base.security.ApiAuthenticationEntryPoint;
import egovframework.eeum.idempotency.IdempotencyFilter;

/**
 * CORS preflight 계약 검증 — 실제 {@link SecurityConfig#corsConfigurationSource()} 를 CorsFilter 에 얹은
 * standalone MockMvc 로 브라우저 preflight(OPTIONS)를 재현한다(풀 컨텍스트 불요).
 *
 * <p>핵심 회귀: ①`Idempotency-Key` 허용(미허용이면 SPA 의 예약 생성 멱등 헤더가 preflight 에서 거부 —
 * codex 교차검토 Blocking) ②기존 허용 3헤더 유지 ③미허용 헤더/미허용 Origin 은 여전히 거부(명시 목록 원칙).
 */
class SecurityConfigCorsVerification {

	private static final String ORIGIN = "http://localhost:3000";

	private final MockMvc mockMvc = buildMockMvc();

	/** 실제 SecurityConfig 인스턴스에서 CORS 소스만 취해 필터로 얹는다(핸들러·JWT 필드는 CORS 와 무관 — mock). */
	private static MockMvc buildMockMvc() {
		SecurityConfig config = new SecurityConfig(mock(ApiAuthenticationEntryPoint.class),
				mock(ApiAccessDeniedHandler.class), mock(IdempotencyFilter.class));
		ReflectionTestUtils.setField(config, "allowedOrigins", ORIGIN);
		CorsConfigurationSource source = config.corsConfigurationSource();
		return MockMvcBuilders.standaloneSetup(new PreflightProbeController())
				.addFilters(new CorsFilter(source))
				.build();
	}

	/** preflight 대상 경로용 최소 컨트롤러 — preflight 는 CorsFilter 가 핸들러 진입 전에 응답한다. */
	@RestController
	static class PreflightProbeController {
		@PostMapping("/reservations")
		String probe() {
			return "ok";
		}
	}

	@Test
	@DisplayName("preflight — Idempotency-Key 요청 헤더 허용(허용 헤더 에코 + 자격증명/Origin 정상)")
	void preflight_allowsIdempotencyKeyHeader() throws Exception {
		mockMvc.perform(options("/reservations")
						.header(HttpHeaders.ORIGIN, ORIGIN)
						.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
						.header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Idempotency-Key"))
				.andExpect(status().isOk())
				.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ORIGIN))
				.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"))
				.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS,
						Matchers.equalToIgnoringCase("Idempotency-Key")));
	}

	@Test
	@DisplayName("preflight — 기존 허용 3헤더(Authorization/Content-Type/X-Requested-With) 회귀 유지")
	void preflight_keepsExistingAllowedHeaders() throws Exception {
		mockMvc.perform(options("/reservations")
						.header(HttpHeaders.ORIGIN, ORIGIN)
						.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
						.header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS,
								"Authorization, Content-Type, X-Requested-With"))
				.andExpect(status().isOk())
				.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, Matchers.allOf(
						Matchers.containsStringIgnoringCase("Authorization"),
						Matchers.containsStringIgnoringCase("Content-Type"),
						Matchers.containsStringIgnoringCase("X-Requested-With"))));
	}

	@Test
	@DisplayName("preflight — 미허용 요청 헤더는 거부(403, 명시 목록 원칙 — 와일드카드 아님)")
	void preflight_rejectsUnknownHeader() throws Exception {
		mockMvc.perform(options("/reservations")
						.header(HttpHeaders.ORIGIN, ORIGIN)
						.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
						.header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "X-Not-Allowed"))
				.andExpect(status().isForbidden());
	}

	@Test
	@DisplayName("preflight — 미허용 Origin 은 거부(403, 명시 Origin 목록 유지)")
	void preflight_rejectsForeignOrigin() throws Exception {
		mockMvc.perform(options("/reservations")
						.header(HttpHeaders.ORIGIN, "http://evil.example")
						.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
						.header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Idempotency-Key"))
				.andExpect(status().isForbidden());
	}
}
