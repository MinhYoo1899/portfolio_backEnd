package egovframework.eeum.base.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import egovframework.eeum.base.response.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

/**
 * 요청→envelope 통합 검증 (MockMvc standalone — 컨트롤러+GlobalExceptionHandler 조합).
 * codex 교차검토 후속 권고의 핵심: 406(Accept 협상 실패) 시에도 envelope 이 JSON 으로 직렬화되는지.
 */
class EnvelopeStatusCodeVerification {

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.standaloneSetup(new EnvelopeProbeController())
				.setControllerAdvice(new GlobalExceptionHandler(
						new com.fasterxml.jackson.databind.ObjectMapper()))
				.build();
	}

	@Test
	@DisplayName("검증 실패 → 400 VALIDATION_ERROR + fieldErrors")
	void validation_400_withFieldErrors() throws Exception {
		mockMvc.perform(post("/envelope-probe/insert")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"probeNm\":\"\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.resultCode").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.result.fieldErrors[0].field").value("probeNm"));
	}

	@Test
	@DisplayName("잘못된 JSON 본문 → 400 VALIDATION_ERROR")
	void malformedBody_400() throws Exception {
		mockMvc.perform(post("/envelope-probe/insert")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{broken"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.resultCode").value("VALIDATION_ERROR"));
	}

	@Test
	@DisplayName("허용되지 않은 메서드 → 405 envelope")
	void wrongMethod_405() throws Exception {
		mockMvc.perform(get("/envelope-probe/insert"))
				.andExpect(status().isMethodNotAllowed())
				.andExpect(jsonPath("$.resultCode").value("METHOD_NOT_ALLOWED"));
	}

	@Test
	@DisplayName("지원하지 않는 Content-Type → 415 envelope")
	void unsupportedContentType_415() throws Exception {
		mockMvc.perform(post("/envelope-probe/insert")
						.contentType(MediaType.TEXT_PLAIN)
						.content("plain"))
				.andExpect(status().isUnsupportedMediaType())
				.andExpect(jsonPath("$.resultCode").value("UNSUPPORTED_MEDIA_TYPE"));
	}

	@Test
	@DisplayName("Accept 협상 실패(XML 요구) → 406 + envelope 이 JSON 으로 직렬화되어야 함 (핵심 권고)")
	void notAcceptable_406_envelopeStillSerialized() throws Exception {
		mockMvc.perform(get("/envelope-probe/detail")
						.accept(MediaType.APPLICATION_XML))
				.andExpect(status().isNotAcceptable())
				.andExpect(jsonPath("$.resultCode").value("NOT_ACCEPTABLE"));
	}

	// ------------------------------------------------------------------
	// 검증용 프로브 컨트롤러 (테스트 전용 — 운영 코드 아님)
	// ------------------------------------------------------------------

	@RestController
	@RequestMapping("/envelope-probe")
	static class EnvelopeProbeController {

		record ProbeRequest(@NotBlank(message = "이름을 입력해 주세요.") String probeNm) {
		}

		@PostMapping(value = "/insert", consumes = MediaType.APPLICATION_JSON_VALUE)
		public ApiResponse<Void> insert(@Valid @RequestBody ProbeRequest request) {
			return ApiResponse.ok();
		}

		@GetMapping(value = "/detail", produces = MediaType.APPLICATION_JSON_VALUE)
		public ApiResponse<ProbeRequest> detail() {
			return ApiResponse.ok(new ProbeRequest("이음"));
		}
	}
}
