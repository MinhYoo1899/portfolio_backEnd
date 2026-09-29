package egovframework.eeum.auth.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 로그인 요청 DTO.
 *
 * @param emlAddr    이메일주소(로그인 ID)
 * @param pswd       비밀번호(평문 — HTTPS 전제, 서버에서 BCrypt 비교)
 * @param clientType 클라이언트 유형: WEB(refresh 를 HttpOnly 쿠키로) / APP(refresh 를 응답 body 로). 기본 WEB.
 * @param chnlCd     로그인 채널(어느 SPA 인지): USER/PARTNER/OPERATOR — 계정의 채널 자격(CHNL_LOGIN_*)과
 *                   대조해 불허 시 토큰 발급 거부. 보안 결정값이라 기본값 없이 필수(codex 교차검토).
 */
@Schema(description = "로그인 요청")
public record LoginRequest(

		@Schema(description = "이메일주소(로그인 ID)", example = "user@eeum.kr")
		@NotBlank(message = "이메일을 입력해 주세요.")
		@Email(message = "이메일 형식이 아닙니다.")
		String emlAddr,

		@Schema(description = "비밀번호")
		@NotBlank(message = "비밀번호를 입력해 주세요.")
		String pswd,

		@Schema(description = "클라이언트 유형(WEB=쿠키 refresh / APP=body refresh)", example = "WEB", defaultValue = "WEB")
		@Pattern(regexp = "WEB|APP", message = "clientType은 WEB 또는 APP만 허용됩니다.")
		String clientType,

		// @Pattern 은 null 을 통과시키므로 @NotBlank 병행 필수 (codex 교차검토)
		@Schema(description = "로그인 채널(USER=사용자앱 / PARTNER=파트너웹 / OPERATOR=운영자웹)", example = "USER")
		@NotBlank(message = "로그인 채널(chnlCd)을 입력해 주세요.")
		@Pattern(regexp = "USER|PARTNER|OPERATOR", message = "chnlCd는 USER/PARTNER/OPERATOR만 허용됩니다.")
		String chnlCd) {

	/** clientType 미지정 시 WEB. (요청 필드 아님 — Swagger 스키마 노출 금지) */
	@Schema(hidden = true)
	public boolean isApp() {
		return "APP".equals(clientType);
	}
}
