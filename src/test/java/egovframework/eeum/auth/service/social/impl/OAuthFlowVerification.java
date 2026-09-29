package egovframework.eeum.auth.service.social.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import egovframework.eeum.account.service.account.AccountService;
import egovframework.eeum.auth.exception.AuthErrorCode;
import egovframework.eeum.auth.service.auth.dao.AuthMapper;
import egovframework.eeum.auth.service.social.OAuthCallbackOutcome;
import egovframework.eeum.auth.service.social.OAuthResultStore;
import egovframework.eeum.auth.service.social.OAuthResultStore.OAuthResultData;
import egovframework.eeum.auth.service.social.OAuthStateStore;
import egovframework.eeum.auth.service.social.OAuthStateStore.OAuthStateData;
import egovframework.eeum.auth.service.social.OAuthWebProperties;
import egovframework.eeum.auth.service.social.SocialSignupStore;
import egovframework.eeum.auth.service.token.AuthRevocationStore;
import egovframework.eeum.auth.service.token.AuthTokenIssuer;
import egovframework.eeum.auth.social.AuthorizationCodeSocialProvider;
import egovframework.eeum.auth.social.DirectIdTokenSocialProvider;
import egovframework.eeum.auth.social.OAuthAuthorizeContext;
import egovframework.eeum.auth.social.PkceCodec;
import egovframework.eeum.auth.social.SocialUser;
import egovframework.eeum.base.exception.BusinessException;

/**
 * 웹 Authorization Code 흐름 서비스 검증 — redirect allowlist 거부, state 무효/nonce 불일치 시 실패 리다이렉트(상세 비노출).
 */
class OAuthFlowVerification {

	/** 흐름 바인딩 쿠키 원문 + 그 해시(state/result 에 저장) — 정상 흐름은 둘이 일치해야 통과. */
	private static final String FLOW = "flow-binding-token-x";
	private static final String BIND_HASH = PkceCodec.codeChallengeS256(FLOW);

	private final AccountService accountService = mock(AccountService.class);
	private final AuthMapper authMapper = mock(AuthMapper.class);
	private final AuthTokenIssuer authTokenIssuer = mock(AuthTokenIssuer.class);
	private final AuthRevocationStore authRevocationStore = mock(AuthRevocationStore.class);
	private final SocialSignupStore signupStore = mock(SocialSignupStore.class);
	private final OAuthStateStore stateStore = mock(OAuthStateStore.class);
	private final OAuthResultStore resultStore = mock(OAuthResultStore.class);
	private final AuthorizationCodeSocialProvider googleProvider = mock(AuthorizationCodeSocialProvider.class);

	private OAuthWebProperties props() {
		OAuthWebProperties p = new OAuthWebProperties();
		p.setCallbackBaseUrl("https://api.example");
		p.getWeb().getRedirects().put("default", "https://front.example/oauth/callback");
		return p;
	}

	private SocialAuthServiceImpl service(OAuthWebProperties props) {
		when(googleProvider.providerCd()).thenReturn("GOOGLE");
		return new SocialAuthServiceImpl(
				List.<DirectIdTokenSocialProvider>of(), List.of(googleProvider), accountService, authMapper,
				authTokenIssuer, authRevocationStore, signupStore, stateStore, resultStore, props);
	}

	@Test
	@DisplayName("authorize — allowlist 에 없는 redirectKey 는 거부(오픈리다이렉트 차단)")
	void authorizeRejectsUnknownRedirectKey() {
		SocialAuthServiceImpl service = service(props());
		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.authorize("GOOGLE", "LOGIN", "WEB", "evil-key"));
		assertEquals(AuthErrorCode.OAUTH_STATE_INVALID.code(), ex.getErrorCode().code());
		// state 발급·provider 호출 전에 거부
		verify(stateStore, never()).issue(any());
	}

	@Test
	@DisplayName("authorize — 지원하지 않는 provider 거부")
	void authorizeRejectsUnsupportedProvider() {
		SocialAuthServiceImpl service = service(props());
		assertThrows(BusinessException.class, () -> service.authorize("NAVER", "LOGIN", "WEB", "default"));
	}

	@Test
	@DisplayName("authorize — 정상: state 저장 후 provider authorize URI 반환")
	void authorizeIssuesStateAndBuildsUri() {
		SocialAuthServiceImpl service = service(props());
		when(stateStore.issue(any())).thenReturn("state-token");
		when(googleProvider.buildAuthorizeUri(any(OAuthAuthorizeContext.class)))
				.thenReturn(URI.create("https://issuer/authorize?state=state-token"));

		URI uri = service.authorize("GOOGLE", "LOGIN", "WEB", "default").authorizeUri();
		assertEquals("https://issuer/authorize?state=state-token", uri.toString());
		verify(stateStore).issue(any());
	}

	@Test
	@DisplayName("callback — state 무효면 default allowlist URL 로 실패(상세 비노출, code 교환 미시도)")
	void callbackStateInvalidFailsToDefault() {
		SocialAuthServiceImpl service = service(props());
		when(stateStore.consume("bad-state")).thenReturn(null);

		OAuthCallbackOutcome outcome = service.handleCallback("GOOGLE", "code-x", "bad-state", null, FLOW);
		assertTrue(outcome.failed());
		assertEquals("https://front.example/oauth/callback", outcome.redirectUrl());
		verify(googleProvider, never()).exchangeCode(anyString(), anyString(), anyString(), anyString(), anyString());
	}

	@Test
	@DisplayName("callback — 흐름 바인딩 쿠키 불일치/누락이면 실패(code 교환 미시도, 로그인 CSRF 차단)")
	void callbackFlowBindingMismatchFails() {
		SocialAuthServiceImpl service = service(props());
		when(stateStore.consume("st")).thenReturn(
				new OAuthStateData("GOOGLE", "LOGIN", "WEB", "verifier", "nonce", "default", BIND_HASH));

		// 흐름을 시작한 브라우저가 아님(쿠키 불일치) → 실패, 교환 미시도
		OAuthCallbackOutcome mismatch = service.handleCallback("GOOGLE", "code-x", "st", null, "wrong-cookie");
		assertTrue(mismatch.failed());
		assertEquals("https://front.example/oauth/callback", mismatch.redirectUrl());
		// 쿠키 누락도 동일
		when(stateStore.consume("st2")).thenReturn(
				new OAuthStateData("GOOGLE", "LOGIN", "WEB", "verifier", "nonce", "default", BIND_HASH));
		assertTrue(service.handleCallback("GOOGLE", "code-x", "st2", null, null).failed());
		verify(googleProvider, never()).exchangeCode(anyString(), anyString(), anyString(), anyString(), anyString());
	}

	@Test
	@DisplayName("callback — provider error 파라미터면 실패 리다이렉트(code 교환 미시도)")
	void callbackProviderErrorFails() {
		SocialAuthServiceImpl service = service(props());
		when(stateStore.consume("st")).thenReturn(
				new OAuthStateData("GOOGLE", "LOGIN", "WEB", "verifier", "nonce", "default", BIND_HASH));

		OAuthCallbackOutcome outcome = service.handleCallback("GOOGLE", null, "st", "access_denied", FLOW);
		assertTrue(outcome.failed());
		verify(googleProvider, never()).exchangeCode(anyString(), anyString(), anyString(), anyString(), anyString());
	}

	@Test
	@DisplayName("callback — exchangeCode nonce 불일치(BusinessException)면 실패 리다이렉트(상세 비노출)")
	void callbackNonceMismatchFails() {
		SocialAuthServiceImpl service = service(props());
		when(stateStore.consume("st")).thenReturn(
				new OAuthStateData("GOOGLE", "LOGIN", "WEB", "verifier", "nonce", "default", BIND_HASH));
		when(googleProvider.exchangeCode(eq("code-x"), anyString(), eq("verifier"), eq("nonce"), anyString()))
				.thenThrow(new BusinessException(AuthErrorCode.AUTH_SOCIAL_TOKEN_INVALID));

		OAuthCallbackOutcome outcome = service.handleCallback("GOOGLE", "code-x", "st", null, FLOW);
		assertTrue(outcome.failed());
		assertEquals("https://front.example/oauth/callback", outcome.redirectUrl());
	}

	@Test
	@DisplayName("callback — 신규 사용자면 SIGNUP 결과 저장 후 result 토큰 반환(성공)")
	void callbackNewUserIssuesSignupResult() {
		SocialAuthServiceImpl service = service(props());
		when(stateStore.consume("st")).thenReturn(
				new OAuthStateData("GOOGLE", "LOGIN", "WEB", "verifier", "nonce", "default", BIND_HASH));
		when(googleProvider.exchangeCode(eq("code-x"), anyString(), eq("verifier"), eq("nonce"), anyString()))
				.thenReturn(new SocialUser("GOOGLE", "sub-1", "a@b.com", true, "홍길동"));
		when(accountService.findUserSnBySocial("GOOGLE", "sub-1")).thenReturn(null);
		when(signupStore.issue(any())).thenReturn("signup-tok");
		when(resultStore.issue(any())).thenReturn("result-tok");

		OAuthCallbackOutcome outcome = service.handleCallback("GOOGLE", "code-x", "st", null, FLOW);
		assertFalse(outcome.failed());
		assertEquals("result-tok", outcome.resultToken());
		verify(signupStore).issue(any());
	}

	@Test
	@DisplayName("result — 흐름 바인딩 불일치면 OAUTH_RESULT_INVALID(공격자 result 토큰 피해자 소비 차단)")
	void exchangeResultFlowBindingMismatchRejected() {
		SocialAuthServiceImpl service = service(props());
		when(resultStore.consume("rt")).thenReturn(OAuthResultData.login(7L, "WEB", BIND_HASH));

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.exchangeResult("rt", "wrong-cookie"));
		assertEquals(AuthErrorCode.OAUTH_RESULT_INVALID.code(), ex.getErrorCode().code());
		verify(authTokenIssuer, never()).issue(any(), any(), anyString(), anyLong());
	}
}
