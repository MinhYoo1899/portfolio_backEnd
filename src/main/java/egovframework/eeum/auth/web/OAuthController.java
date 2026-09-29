package egovframework.eeum.auth.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import egovframework.eeum.auth.exception.AuthErrorCode;
import egovframework.eeum.auth.service.social.OAuthAuthorizeResult;
import egovframework.eeum.auth.service.social.OAuthCallbackOutcome;
import egovframework.eeum.auth.service.social.OAuthFlowResult;
import egovframework.eeum.auth.service.social.OAuthResultStore.OAuthResultData;
import egovframework.eeum.auth.service.social.SocialAuthService;
import egovframework.eeum.auth.web.dto.AuthTokenResponse;
import egovframework.eeum.auth.web.dto.OAuthResultRequest;
import egovframework.eeum.auth.web.dto.OAuthResultResponse;
import egovframework.eeum.base.exception.BusinessException;
import egovframework.eeum.base.response.ApiResponse;
import egovframework.eeum.base.security.OAuthFlowCookieSupport;
import egovframework.eeum.base.security.RefreshCookieSupport;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 소셜 로그인 — 웹 Authorization Code + PKCE 흐름(Phase 2, 구글·카카오).
 *
 * <p>흐름: {@code GET /auth/oauth/{provider}/authorize}(→302 provider) →
 * {@code GET /auth/oauth/{provider}/callback}(→302 프론트 {@code ?result=}|{@code ?error=oauth_failed}) →
 * {@code POST /auth/oauth/result}(result 토큰 교환 → 토큰 발급/가입 티켓).
 *
 * <p>토큰/code/state 원문은 로그·응답에 노출하지 않으며, 콜백 리다이렉트 대상은 allowlist 에서만 해석한다(오픈리다이렉트 차단).
 */
@Tag(name = "auth", description = "소셜 로그인 (웹 Authorization Code + PKCE)")
@RestController
@RequestMapping("/auth/oauth")
@RequiredArgsConstructor
public class OAuthController {

	private static final String DEFAULT_PURPOSE = "LOGIN";
	private static final String DEFAULT_CLIENT_TYPE = "WEB";
	private static final String DEFAULT_REDIRECT_KEY = "default";

	private final SocialAuthService socialAuthService;
	private final RefreshCookieSupport refreshCookieSupport;
	private final OAuthFlowCookieSupport oauthFlowCookieSupport;

	@Operation(summary = "소셜 authorize 시작", description = "provider(GOOGLE|KAKAO) authorize URL로 302 리다이렉트. state/nonce/PKCE는 서버가 발급·저장합니다. Phase2는 purpose=LOGIN만 지원.")
	@GetMapping("/{provider}/authorize")
	public ResponseEntity<Void> authorize(
			@PathVariable("provider") String provider,
			@RequestParam(value = "purpose", defaultValue = DEFAULT_PURPOSE) String purpose,
			@RequestParam(value = "clientType", defaultValue = DEFAULT_CLIENT_TYPE) String clientType,
			@RequestParam(value = "redirectKey", defaultValue = DEFAULT_REDIRECT_KEY) String redirectKey,
			HttpServletResponse httpResponse) {

		String providerCd = normalizeProvider(provider);
		String normalizedClientType = "APP".equalsIgnoreCase(clientType) ? "APP" : "WEB";
		OAuthAuthorizeResult authorizeResult =
				socialAuthService.authorize(providerCd, purpose, normalizedClientType, redirectKey);
		// 흐름 바인딩 토큰을 HttpOnly 쿠키로 — 콜백/결과가 같은 브라우저인지 대조(로그인 CSRF 차단)
		oauthFlowCookieSupport.setFlowCookie(httpResponse, authorizeResult.flowBindingToken());
		return ResponseEntity.status(HttpStatus.FOUND).location(authorizeResult.authorizeUri()).build();
	}

	@Operation(summary = "소셜 콜백", description = "provider가 호출하는 콜백. 성공 시 프론트로 302(?result=resultToken), 실패 시 프론트로 302(?error=oauth_failed). 내부 상세는 노출하지 않습니다.")
	@GetMapping("/{provider}/callback")
	public ResponseEntity<Void> callback(
			@PathVariable("provider") String provider,
			@RequestParam(value = "code", required = false) String code,
			@RequestParam(value = "state", required = false) String state,
			@RequestParam(value = "error", required = false) String error,
			HttpServletRequest httpRequest) {

		String providerCd = normalizeProvider(provider);
		String flowBinding = oauthFlowCookieSupport.readFlowCookie(httpRequest);
		OAuthCallbackOutcome outcome = socialAuthService.handleCallback(providerCd, code, state, error, flowBinding);

		UriComponentsBuilder target = UriComponentsBuilder.fromUriString(outcome.redirectUrl());
		if (outcome.failed()) {
			target.queryParam("error", "oauth_failed");
		} else {
			target.queryParam("result", outcome.resultToken());
		}
		// result 토큰이 쿼리에 실리므로 외부 referrer 유출 차단(codex SHOULD)
		return ResponseEntity.status(HttpStatus.FOUND)
				.location(target.build(true).toUri())
				.header("Referrer-Policy", "no-referrer")
				.build();
	}

	@Operation(summary = "OAuth result 교환", description = "콜백이 전달한 result 토큰을 토큰/가입 티켓으로 교환. LOGIN이면 토큰(웹=refresh 쿠키, 앱=body refresh), 신규면 SIGNUP_REQUIRED + 가입 티켓.")
	@PostMapping("/result")
	public ApiResponse<OAuthResultResponse> result(@Valid @RequestBody OAuthResultRequest request,
			HttpServletRequest httpRequest, HttpServletResponse httpResponse) {

		String flowBinding = oauthFlowCookieSupport.readFlowCookie(httpRequest);
		OAuthFlowResult flow = socialAuthService.exchangeResult(request.resultToken(), flowBinding);
		// 1회성 — 흐름 바인딩 쿠키 즉시 만료(재사용 차단)
		oauthFlowCookieSupport.expireFlowCookie(httpResponse);
		OAuthResultData result = flow.result();

		if (OAuthResultData.TYPE_SIGNUP.equals(result.type())) {
			return ApiResponse.ok(
					OAuthResultResponse.signupRequired(result.signupToken(), result.email(), result.name()));
		}
		// LOGIN — clientType(state 시점 결정)으로 refresh 전달 방식 분기
		boolean isApp = "APP".equals(result.clientType());
		AuthTokenResponse delivered = deliverRefresh(flow.tokens(), isApp, httpResponse);
		return ApiResponse.ok(OAuthResultResponse.login(delivered));
	}

	// ------------------------------------------------------------------

	/** provider 경로 정규화 — GOOGLE/KAKAO/NAVER 만 허용(대소문자 무관), 그 외 400. */
	private String normalizeProvider(String provider) {
		if (provider == null) {
			throw new BusinessException(AuthErrorCode.OAUTH_PROVIDER_UNSUPPORTED);
		}
		String upper = provider.toUpperCase();
		if (!"GOOGLE".equals(upper) && !"KAKAO".equals(upper) && !"NAVER".equals(upper)) {
			throw new BusinessException(AuthErrorCode.OAUTH_PROVIDER_UNSUPPORTED);
		}
		return upper;
	}

	/** 웹이면 refresh 를 쿠키로 옮기고 body 에서는 제거, 앱이면 body 그대로(AuthController 와 동일 정책). */
	private AuthTokenResponse deliverRefresh(AuthTokenResponse tokens, boolean isApp, HttpServletResponse response) {
		if (isApp) {
			return tokens;
		}
		refreshCookieSupport.setRefreshCookie(response, tokens.refreshToken());
		return tokens.withoutRefreshToken();
	}
}
