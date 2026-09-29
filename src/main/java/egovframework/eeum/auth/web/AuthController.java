package egovframework.eeum.auth.web;

import org.springframework.http.HttpHeaders;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import egovframework.eeum.auth.service.auth.AuthService;
import egovframework.eeum.auth.service.social.SocialAuthService;
import egovframework.eeum.auth.web.dto.AuthTokenResponse;
import egovframework.eeum.auth.web.dto.AuthUserInfo;
import egovframework.eeum.auth.web.dto.LoginRequest;
import egovframework.eeum.auth.web.dto.RefreshRequest;
import egovframework.eeum.auth.web.dto.SocialLoginRequest;
import egovframework.eeum.auth.web.dto.SocialLoginResponse;
import egovframework.eeum.auth.web.dto.SocialSignupRequest;
import egovframework.eeum.auth.vo.AuthUser;
import egovframework.eeum.base.exception.BusinessException;
import egovframework.eeum.base.response.ApiResponse;
import egovframework.eeum.base.security.RefreshCookieSupport;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 인증 API — 로그인/재발급/로그아웃/현재 사용자.
 *
 * <p>refresh 전달(확정 정책): 웹=HttpOnly Secure 쿠키({@code REFRESH_TOKEN}, path=/auth),
 * 앱=응답 body. 요청의 {@code clientType}으로 구분(기본 WEB).
 */
@Tag(name = "auth", description = "인증 (로그인/재발급/로그아웃)")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

	private final AuthService authService;
	private final SocialAuthService socialAuthService;
	private final RefreshCookieSupport refreshCookieSupport;

	@Operation(summary = "로그인", description = "이메일+비밀번호 로그인. chnlCd(USER/PARTNER/OPERATOR)는 필수 — 계정에 해당 채널 로그인 자격이 없으면 거부됩니다. 웹(clientType=WEB)은 refresh가 HttpOnly 쿠키로, 앱(APP)은 body로 발급됩니다. PENDING(승인대기)도 로그인 가능 — user.acntStts로 분기하세요.")
	@PostMapping("/login")
	public ApiResponse<AuthTokenResponse> login(@Valid @RequestBody LoginRequest request,
			HttpServletRequest httpRequest, HttpServletResponse httpResponse) {

		AuthTokenResponse tokens = authService.login(request, clientIp(httpRequest), userAgent(httpRequest));
		return ApiResponse.ok(deliverRefresh(tokens, request.isApp(), httpResponse));
	}

	@Operation(summary = "토큰 재발급(회전)", description = "body의 chnlCd(원 로그인 채널)는 웹/앱 공통 필수. refresh 토큰은 웹=쿠키(body의 refreshToken 생략), 앱=body. 사용된 refresh는 폐기되고 새 쌍이 발급됩니다.")
	@PostMapping("/refresh")
	public ApiResponse<AuthTokenResponse> refresh(@Valid @RequestBody RefreshRequest request,
			HttpServletRequest httpRequest, HttpServletResponse httpResponse) {

		String cookieToken = refreshCookieSupport.readRefreshCookie(httpRequest);
		boolean fromCookie = cookieToken != null && !cookieToken.isBlank();
		String refreshToken = fromCookie ? cookieToken : request.refreshToken();

		if (refreshToken == null || refreshToken.isBlank()) {
			throw BusinessException.unauthorized();
		}

		AuthTokenResponse tokens = authService.refresh(refreshToken, request.chnlCd());
		// 쿠키로 들어온 클라이언트는 웹 — 새 refresh 도 쿠키로 회전
		return ApiResponse.ok(deliverRefresh(tokens, !fromCookie, httpResponse));
	}

	@Operation(summary = "로그아웃", description = "refresh 세션 폐기(멱등). 웹은 쿠키, 앱은 body refreshToken. 둘 다 없고 access 인증만 있으면 해당 사용자 전체 세션 폐기.")
	@PostMapping("/logout")
	public ApiResponse<Void> logout(@RequestBody(required = false) RefreshRequest request,
			@AuthenticationPrincipal AuthUser user,
			HttpServletRequest httpRequest, HttpServletResponse httpResponse) {

		String cookieToken = refreshCookieSupport.readRefreshCookie(httpRequest);
		String refreshToken = (cookieToken != null && !cookieToken.isBlank()) ? cookieToken
				: (request == null ? null : request.refreshToken());

		authService.logout(refreshToken, user == null ? null : user.userSn());
		refreshCookieSupport.expireRefreshCookie(httpResponse);
		return ApiResponse.ok();
	}

	@Operation(summary = "현재 사용자", description = "access 토큰 기준 현재 사용자 + 권한코드 목록. 라우트 가드/메뉴 분기용.")
	@GetMapping("/me")
	public ApiResponse<AuthUserInfo> me(@AuthenticationPrincipal AuthUser user) {
		if (user == null) {
			throw BusinessException.unauthorized();
		}
		return ApiResponse.ok(AuthUserInfo.from(user));
	}

	@Operation(summary = "소셜 로그인", description = "provider(GOOGLE) id_token 검증 → 기존 연결 계정이면 토큰 발급(status=LOGIN), 신규면 가입 티켓 반환(status=SIGNUP_REQUIRED). 소셜은 USER 채널 전용. 웹(WEB)은 refresh가 HttpOnly 쿠키로 발급됩니다.")
	@PostMapping("/social-login")
	public ApiResponse<SocialLoginResponse> socialLogin(@Valid @RequestBody SocialLoginRequest request,
			HttpServletRequest httpRequest, HttpServletResponse httpResponse) {

		SocialLoginResponse result = socialAuthService.login(request, clientIp(httpRequest), userAgent(httpRequest));
		if (result.token() == null) {
			return ApiResponse.ok(result); // SIGNUP_REQUIRED — 토큰 없음
		}
		AuthTokenResponse delivered = deliverRefresh(result.token(), request.isApp(), httpResponse);
		return ApiResponse.ok(SocialLoginResponse.login(delivered));
	}

	@Operation(summary = "소셜 가입 완료", description = "social-login의 SIGNUP_REQUIRED 응답으로 받은 signupToken + 약관 동의로 계정 생성 후 토큰 발급. 웹(WEB)은 refresh가 HttpOnly 쿠키로 발급됩니다.")
	@PostMapping("/social-signup")
	public ApiResponse<AuthTokenResponse> socialSignup(@Valid @RequestBody SocialSignupRequest request,
			HttpServletRequest httpRequest, HttpServletResponse httpResponse) {

		AuthTokenResponse tokens = socialAuthService.signup(request, clientIp(httpRequest), userAgent(httpRequest));
		return ApiResponse.ok(deliverRefresh(tokens, request.isApp(), httpResponse));
	}

	// ------------------------------------------------------------------
	// refresh 쿠키 처리
	// ------------------------------------------------------------------

	/** 웹이면 refresh 를 쿠키로 옮기고 body 에서는 제거, 앱이면 body 그대로. */
	private AuthTokenResponse deliverRefresh(AuthTokenResponse tokens, boolean isApp, HttpServletResponse response) {
		if (isApp) {
			return tokens;
		}
		refreshCookieSupport.setRefreshCookie(response, tokens.refreshToken());
		return tokens.withoutRefreshToken();
	}

	// ------------------------------------------------------------------
	// 요청 메타 (프록시 헤더 처리는 인프라 확정 시 X-Forwarded-For 신뢰 설정과 함께)
	// ------------------------------------------------------------------

	private String clientIp(HttpServletRequest request) {
		return request.getRemoteAddr();
	}

	private String userAgent(HttpServletRequest request) {
		String ua = request.getHeader(HttpHeaders.USER_AGENT);
		return ua != null && ua.length() > 500 ? ua.substring(0, 500) : ua;
	}
}
