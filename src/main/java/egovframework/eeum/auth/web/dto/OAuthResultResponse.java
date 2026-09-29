package egovframework.eeum.auth.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * OAuth result 교환 응답 — 웹 Authorization Code 흐름 결과를 {@code status}로 구분.
 *
 * <ul>
 *   <li>{@code LOGIN}: 기존 연결 계정 → 토큰({@code token})(웹은 refresh 쿠키, 앱은 body refresh)</li>
 *   <li>{@code SIGNUP_REQUIRED}: 신규 → 약관 동의 후 {@code POST /auth/social-signup} 호출용 가입 티켓</li>
 * </ul>
 *
 * @param status LOGIN | SIGNUP_REQUIRED
 * @param token  status=LOGIN 일 때 토큰
 * @param signup status=SIGNUP_REQUIRED 일 때 가입 티켓
 */
@Schema(description = "OAuth result 교환 응답")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OAuthResultResponse(
		@Schema(description = "결과", example = "LOGIN") String status,
		@Schema(description = "로그인 토큰(status=LOGIN)") AuthTokenResponse token,
		@Schema(description = "가입 티켓(status=SIGNUP_REQUIRED)") SocialLoginResponse.SignupTicket signup) {

	public static final String STATUS_LOGIN = "LOGIN";
	public static final String STATUS_SIGNUP_REQUIRED = "SIGNUP_REQUIRED";

	public static OAuthResultResponse login(AuthTokenResponse token) {
		return new OAuthResultResponse(STATUS_LOGIN, token, null);
	}

	public static OAuthResultResponse signupRequired(String signupToken, String email, String name) {
		return new OAuthResultResponse(STATUS_SIGNUP_REQUIRED, null,
				new SocialLoginResponse.SignupTicket(signupToken, email, name));
	}
}
