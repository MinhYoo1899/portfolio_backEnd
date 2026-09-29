package egovframework.eeum.auth.web.dto;

import java.util.List;

import egovframework.eeum.account.web.dto.TrmsAgreItem;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 소셜 신규 가입 완료 요청 — {@code /auth/social-login} 의 SIGNUP_REQUIRED 응답으로 받은 가입 티켓 + 약관 동의.
 *
 * <p>채널은 서버가 USER 로 고정한다(소셜=USER 채널 전용) — 요청에서 받지 않는다.
 *
 * @param signupToken  social-login 이 발급한 1회용 가입 토큰
 * @param trmsAgreList 약관 동의 목록(필수약관 전부 Y)
 * @param clientType   WEB/APP (기본 WEB)
 * @param ncnm         별명(선택)
 */
@Schema(description = "소셜 가입 완료 요청")
public record SocialSignupRequest(

		@Schema(description = "social-login 이 준 가입 토큰")
		@NotBlank(message = "가입 토큰(signupToken)이 필요합니다.")
		String signupToken,

		@Schema(description = "약관 동의 목록(필수약관 전부 Y 필요)")
		@NotEmpty(message = "약관 동의 정보가 필요합니다.")
		@Valid
		List<TrmsAgreItem> trmsAgreList,

		@Schema(description = "클라이언트 유형(WEB/APP)", example = "WEB", defaultValue = "WEB")
		@Pattern(regexp = "WEB|APP", message = "clientType은 WEB 또는 APP만 허용됩니다.")
		String clientType,

		@Schema(description = "별명(선택)", example = "한옥지기")
		@Size(max = 30, message = "별명은 30자 이내여야 합니다.")
		String ncnm) {

	@Schema(hidden = true)
	public boolean isApp() {
		return "APP".equals(clientType);
	}

	/** 약관 동의 채널코드(공통코드 AGRE_CHNL: WEB/APP) — 미지정 시 WEB. */
	public String agreChnlOrDefault() {
		return "APP".equals(clientType) ? "APP" : "WEB";
	}
}
