package egovframework.eeum.auth.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 소셜 로그인 요청 DTO — provider 발급 id_token 을 검증해 우리 토큰을 발급/가입 분기.
 *
 * <p>채널은 서버가 USER 로 고정한다(소셜=USER 채널 전용, 전략 §2-3) — 요청에서 받지 않는다.
 *
 * @param provider   소셜제공자코드 (현재 GOOGLE — 카카오·네이버는 후속)
 * @param idToken    provider 가 발급한 OIDC id_token
 * @param clientType WEB(refresh 쿠키) / APP(refresh body), 기본 WEB
 */
@Schema(description = "소셜 로그인 요청")
public record SocialLoginRequest(

		@Schema(description = "소셜제공자(GOOGLE/KAKAO)", example = "GOOGLE")
		@NotBlank(message = "소셜 제공자(provider)는 필수입니다.")
		@Pattern(regexp = "GOOGLE|KAKAO", message = "지원하지 않는 소셜 제공자입니다.")
		String provider,

		@Schema(description = "provider 발급 id_token")
		@NotBlank(message = "idToken은 필수입니다.")
		String idToken,

		@Schema(description = "클라이언트 유형(WEB=쿠키 refresh / APP=body refresh)", example = "WEB", defaultValue = "WEB")
		@Pattern(regexp = "WEB|APP", message = "clientType은 WEB 또는 APP만 허용됩니다.")
		String clientType) {

	@Schema(hidden = true)
	public boolean isApp() {
		return "APP".equals(clientType);
	}
}
