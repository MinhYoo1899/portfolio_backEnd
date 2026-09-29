package egovframework.eeum.auth.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 소셜 로그인 응답 — 두 결과를 {@code status}로 구분.
 *
 * <ul>
 *   <li>{@code LOGIN}: 기존 연결 계정 → 우리 토큰({@code token}) 발급(로그인 완료)</li>
 *   <li>{@code SIGNUP_REQUIRED}: 신규 → 약관 동의 후 {@code POST /auth/social-signup} 호출용 {@code signup} 티켓</li>
 * </ul>
 *
 * @param status LOGIN | SIGNUP_REQUIRED
 * @param token  status=LOGIN 일 때의 토큰(웹은 refresh 쿠키로 나가므로 body refreshToken=null)
 * @param signup status=SIGNUP_REQUIRED 일 때의 가입 티켓
 */
@Schema(description = "소셜 로그인 응답")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SocialLoginResponse(
		@Schema(description = "결과", example = "LOGIN") String status,
		@Schema(description = "로그인 토큰(status=LOGIN)") AuthTokenResponse token,
		@Schema(description = "가입 티켓(status=SIGNUP_REQUIRED)") SignupTicket signup) {

	public static final String STATUS_LOGIN = "LOGIN";
	public static final String STATUS_SIGNUP_REQUIRED = "SIGNUP_REQUIRED";

	/** 신규 가입 티켓 — 약관 동의 완료 호출에 쓸 1회용 토큰 + 표시용 정보. */
	@Schema(description = "가입 티켓")
	public record SignupTicket(
			@Schema(description = "가입 완료(/auth/social-signup) 호출용 1회용 토큰") String signupToken,
			@Schema(description = "provider 이메일(표시·프리필용)") String email,
			@Schema(description = "provider 이름(표시·프리필용)") String name) {
	}

	public static SocialLoginResponse login(AuthTokenResponse token) {
		return new SocialLoginResponse(STATUS_LOGIN, token, null);
	}

	public static SocialLoginResponse signupRequired(String signupToken, String email, String name) {
		return new SocialLoginResponse(STATUS_SIGNUP_REQUIRED, null, new SignupTicket(signupToken, email, name));
	}
}
