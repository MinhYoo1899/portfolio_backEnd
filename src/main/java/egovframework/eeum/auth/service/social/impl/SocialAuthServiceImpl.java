package egovframework.eeum.auth.service.social.impl;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import egovframework.eeum.account.service.account.AccountService;
import egovframework.eeum.auth.exception.AuthErrorCode;
import egovframework.eeum.auth.service.auth.dao.AuthMapper;
import egovframework.eeum.auth.service.social.OAuthAuthorizeResult;
import egovframework.eeum.auth.service.social.OAuthCallbackOutcome;
import egovframework.eeum.auth.service.social.OAuthFlowResult;
import egovframework.eeum.auth.service.social.OAuthResultStore;
import egovframework.eeum.auth.service.social.OAuthResultStore.OAuthResultData;
import egovframework.eeum.auth.service.social.OAuthStateStore;
import egovframework.eeum.auth.service.social.OAuthStateStore.OAuthStateData;
import egovframework.eeum.auth.service.social.OAuthWebProperties;
import egovframework.eeum.auth.service.social.SocialAuthService;
import egovframework.eeum.auth.service.social.SocialSignupStore;
import egovframework.eeum.auth.service.token.AuthRevocationStore;
import egovframework.eeum.auth.service.token.AuthTokenIssuer;
import egovframework.eeum.auth.social.AuthorizationCodeSocialProvider;
import egovframework.eeum.auth.social.DirectIdTokenSocialProvider;
import egovframework.eeum.auth.social.OAuthAuthorizeContext;
import egovframework.eeum.auth.social.PkceCodec;
import egovframework.eeum.auth.social.SocialUser;
import egovframework.eeum.auth.vo.AuthAccountVO;
import egovframework.eeum.auth.web.dto.AuthTokenResponse;
import egovframework.eeum.auth.web.dto.SocialLoginRequest;
import egovframework.eeum.auth.web.dto.SocialLoginResponse;
import egovframework.eeum.auth.web.dto.SocialSignupRequest;
import egovframework.eeum.base.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;

/**
 * 소셜 로그인/가입 구현. 토큰 발급은 이메일 로그인과 동일({@link AuthTokenIssuer}), 계정 생성은 account 도메인 위임.
 *
 * <p>소셜은 USER 채널 전용(전략 §2-3). 동일 이메일 자동연결 금지(account 도메인이 거부).
 * 신규 사용자는 약관 동의 전 계정을 만들지 않고(pending 토큰) 가입 완료 호출에서 생성한다.
 */
@Slf4j
@Service
public class SocialAuthServiceImpl implements SocialAuthService {

	/** 소셜 로그인 채널 — USER 고정. */
	private static final String CHNL_USER = "USER";
	private static final String CHNL_LOGIN_USER = "CHNL_LOGIN_USER";
	private static final String STTS_SUSPENDED = "SUSPENDED";
	private static final String STTS_REJECTED = "REJECTED";
	/** Phase 2 authorize 용도 — 현재 LOGIN 만(LINK 는 후속). */
	private static final String PURPOSE_LOGIN = "LOGIN";

	private final Map<String, DirectIdTokenSocialProvider> providers;
	private final Map<String, AuthorizationCodeSocialProvider> codeProviders;
	private final AccountService accountService;
	private final AuthMapper authMapper;
	private final AuthTokenIssuer authTokenIssuer;
	private final AuthRevocationStore authRevocationStore;
	private final SocialSignupStore signupStore;
	private final OAuthStateStore oauthStateStore;
	private final OAuthResultStore oauthResultStore;
	private final OAuthWebProperties oauthWebProperties;

	public SocialAuthServiceImpl(List<DirectIdTokenSocialProvider> providerList,
			List<AuthorizationCodeSocialProvider> codeProviderList, AccountService accountService,
			AuthMapper authMapper, AuthTokenIssuer authTokenIssuer, AuthRevocationStore authRevocationStore,
			SocialSignupStore signupStore, OAuthStateStore oauthStateStore, OAuthResultStore oauthResultStore,
			OAuthWebProperties oauthWebProperties) {
		this.providers = providerList.stream()
				.collect(Collectors.toUnmodifiableMap(DirectIdTokenSocialProvider::providerCd, p -> p));
		this.codeProviders = codeProviderList.stream()
				.collect(Collectors.toUnmodifiableMap(AuthorizationCodeSocialProvider::providerCd, p -> p));
		this.accountService = accountService;
		this.authMapper = authMapper;
		this.authTokenIssuer = authTokenIssuer;
		this.authRevocationStore = authRevocationStore;
		this.signupStore = signupStore;
		this.oauthStateStore = oauthStateStore;
		this.oauthResultStore = oauthResultStore;
		this.oauthWebProperties = oauthWebProperties;
	}

	@Override
	public SocialLoginResponse login(SocialLoginRequest request, String cntnIp, String userAgnt) {
		DirectIdTokenSocialProvider provider = providers.get(request.provider());
		if (provider == null) {
			throw new BusinessException(AuthErrorCode.AUTH_SOCIAL_TOKEN_INVALID);
		}
		SocialUser social = provider.verify(request.idToken());
		return handleVerifiedSocialUser(social);
	}

	/**
	 * 검증된 소셜 신원으로 로그인/가입 분기 — 기존 소셜계정이면 토큰 발급, 없으면 가입 티켓(pending) 발급.
	 *
	 * <p>id_token 직전달 경로({@link #login})와 (후속) 서버 시작 Authorization Code 흐름이 공유하는 공통 분기다
	 * (검증 방식만 다르고, 검증된 {@link SocialUser} 이후 처리는 동일 — codex 전략).
	 */
	private SocialLoginResponse handleVerifiedSocialUser(SocialUser social) {
		Long userSn = accountService.findUserSnBySocial(social.providerCd(), social.providerUserId());
		if (userSn == null) {
			// 신규 — 약관 동의 전 계정 생성 금지. 검증된 신원을 pending 으로 보관하고 가입 티켓 발급.
			String signupToken = signupStore.issue(social);
			return SocialLoginResponse.signupRequired(signupToken, social.email(), social.name());
		}

		AuthTokenResponse tokens = issueForActiveUser(userSn);
		authMapper.updateLastLgnDt(userSn);
		return SocialLoginResponse.login(tokens);
	}

	@Override
	public AuthTokenResponse signup(SocialSignupRequest request, String cntnIp, String userAgnt) {
		SocialUser social = signupStore.consume(request.signupToken());
		if (social == null) {
			// 가입 티켓 만료/재사용/위조 — 소셜 인증부터 다시
			throw new BusinessException(AuthErrorCode.AUTH_SOCIAL_TOKEN_INVALID);
		}

		long userSn = accountService.createSocialAccount(
				social.providerCd(), social.providerUserId(), social.email(), social.name(),
				social.emailVerified(), request.trmsAgreList(), request.agreChnlOrDefault(), cntnIp, userAgnt);

		// 생성 직후에도 상태·채널 자격을 재검증 후 발급(도메인 보증이 깨지면 토큰이 바로 나가지 않게, codex 권고)
		return issueForActiveUser(userSn);
	}

	// ------------------------------------------------------------------
	// 웹 Authorization Code + PKCE 흐름 (Phase 2)
	// ------------------------------------------------------------------

	@Override
	public OAuthAuthorizeResult authorize(String providerCd, String purpose, String clientType, String redirectKey) {
		AuthorizationCodeSocialProvider provider = codeProviders.get(providerCd);
		if (provider == null) {
			throw new BusinessException(AuthErrorCode.OAUTH_PROVIDER_UNSUPPORTED);
		}
		// Phase 2 는 LOGIN 만(LINK 후속)
		if (!PURPOSE_LOGIN.equals(purpose)) {
			throw new BusinessException(AuthErrorCode.OAUTH_PROVIDER_UNSUPPORTED);
		}
		// redirectKey 는 allowlist 에 있어야 함(오픈리다이렉트 차단 — 시작 단계에서 조기 거부)
		if (oauthWebProperties.redirectUrl(redirectKey) == null) {
			throw new BusinessException(AuthErrorCode.OAUTH_STATE_INVALID);
		}

		String nonce = PkceCodec.generateCodeVerifier(); // 난수(URL-safe) — nonce 도 충분 엔트로피
		String codeVerifier = PkceCodec.generateCodeVerifier();
		String codeChallenge = PkceCodec.codeChallengeS256(codeVerifier);
		String redirectUri = oauthWebProperties.callbackUri(providerCd);

		// 흐름 바인딩 — 원문 토큰은 HttpOnly 쿠키로만 내려가고, state 에는 해시만 저장한다.
		// 콜백/결과 소비 시 쿠키 해시를 대조해 "흐름을 시작한 브라우저"만 통과시킨다(로그인 CSRF 차단, codex MUST).
		String flowBindingToken = PkceCodec.generateCodeVerifier();
		String bindHash = PkceCodec.codeChallengeS256(flowBindingToken);

		String state = oauthStateStore.issue(new OAuthStateData(
				providerCd, PURPOSE_LOGIN, clientType, codeVerifier, nonce, redirectKey, bindHash));

		URI authorizeUri = provider.buildAuthorizeUri(
				new OAuthAuthorizeContext(state, nonce, codeChallenge, redirectUri, clientType));
		return new OAuthAuthorizeResult(authorizeUri, flowBindingToken);
	}

	@Override
	public OAuthCallbackOutcome handleCallback(String providerCd, String code, String state, String providerError,
			String flowBinding) {
		// state 소비 — 만료/재사용/위조/provider 불일치면 default allowlist 로 실패 리다이렉트(상세 비노출)
		OAuthStateData stateData = oauthStateStore.consume(state);
		if (stateData == null || !stateData.providerCd().equals(providerCd)) {
			return OAuthCallbackOutcome.failed(defaultRedirectUrl());
		}
		// 이 시점부터 리다이렉트 대상은 state 의 redirectKey(allowlist 해석) — 없으면 default
		String redirectUrl = resolveRedirectUrl(stateData.redirectKey());

		// 흐름 바인딩 대조 — 흐름을 시작한 브라우저(authorize 쿠키)가 아니면 거부(로그인 CSRF 차단, codex MUST)
		if (!flowBindingMatches(flowBinding, stateData.bindHash())) {
			return OAuthCallbackOutcome.failed(redirectUrl);
		}

		// provider error 또는 code 누락 — 실패 리다이렉트
		if (providerError != null || code == null || code.isBlank()) {
			return OAuthCallbackOutcome.failed(redirectUrl);
		}

		AuthorizationCodeSocialProvider provider = codeProviders.get(providerCd);
		if (provider == null) {
			return OAuthCallbackOutcome.failed(redirectUrl);
		}

		try {
			String redirectUri = oauthWebProperties.callbackUri(providerCd);
			SocialUser social = provider.exchangeCode(
					code, redirectUri, stateData.codeVerifier(), stateData.nonce(), state);

			Long userSn = accountService.findUserSnBySocial(social.providerCd(), social.providerUserId());
			OAuthResultData result;
			if (userSn == null) {
				// 신규 — 약관 동의 전 계정 생성 금지. 가입 티켓 발급 후 SIGNUP 결과 저장.
				String signupToken = signupStore.issue(social);
				result = OAuthResultData.signup(signupToken, social.email(), social.name(), stateData.bindHash());
			} else {
				result = OAuthResultData.login(userSn, stateData.clientType(), stateData.bindHash());
			}
			return OAuthCallbackOutcome.success(redirectUrl, oauthResultStore.issue(result));
		} catch (RuntimeException e) {
			// 교환/검증 실패(nonce 불일치 등) + Redis/직렬화 등 런타임 오류 — redirectKey 확보 후 넓은 예외를
			// sanitize 해 실패 리다이렉트로(내부 상세·500 JSON 노출 방지, codex SHOULD). 예외 유형만 로그.
			log.warn("[OAUTH] {} callback failed: {}", providerCd, e.getClass().getSimpleName());
			return OAuthCallbackOutcome.failed(redirectUrl);
		}
	}

	/** 흐름 바인딩 대조 — 쿠키 원문의 해시가 저장된 bindHash 와 일치해야 통과(없거나 불일치면 false). */
	private static boolean flowBindingMatches(String flowBinding, String bindHash) {
		if (flowBinding == null || bindHash == null) {
			return false;
		}
		return bindHash.equals(PkceCodec.codeChallengeS256(flowBinding));
	}

	/** redirectKey 의 allowlist URL — 없으면 default(저장 시 검증되지만 설정 변경 방어). */
	private String resolveRedirectUrl(String redirectKey) {
		String url = oauthWebProperties.redirectUrl(redirectKey);
		return url != null ? url : defaultRedirectUrl();
	}

	/** default allowlist URL — 미설정이면 IllegalState(운영 설정 필수). */
	private String defaultRedirectUrl() {
		String url = oauthWebProperties.redirectUrl("default");
		if (url == null) {
			throw new IllegalStateException("eeum.oauth.web.redirects.default 미설정");
		}
		return url;
	}

	@Override
	public OAuthFlowResult exchangeResult(String resultToken, String flowBinding) {
		OAuthResultData result = oauthResultStore.consume(resultToken);
		if (result == null) {
			throw new BusinessException(AuthErrorCode.OAUTH_RESULT_INVALID);
		}
		// 흐름 바인딩 대조 — 흐름을 시작한 브라우저가 아니면 거부(공격자 result 토큰을 피해자가 소비 못하게, codex MUST)
		if (!flowBindingMatches(flowBinding, result.bindHash())) {
			throw new BusinessException(AuthErrorCode.OAUTH_RESULT_INVALID);
		}
		if (OAuthResultData.TYPE_LOGIN.equals(result.type())) {
			AuthTokenResponse tokens = issueForActiveUser(result.userSn());
			authMapper.updateLastLgnDt(result.userSn());
			return new OAuthFlowResult(result, tokens);
		}
		// SIGNUP — 토큰 없음(가입 완료 호출로 이어짐)
		return new OAuthFlowResult(result, null);
	}

	/** userSn 의 활성 계정 + 상태 + USER 채널 자격을 검증한 뒤 우리 토큰 발급. (로그인·가입 공용) */
	private AuthTokenResponse issueForActiveUser(long userSn) {
		AuthAccountVO account = authMapper.selectActiveAccountByUserSn(userSn);
		if (account == null) {
			// 연결은 있으나 계정이 비활성/탈퇴 — 자격증명 오류와 동일(비노출)
			throw new BusinessException(AuthErrorCode.AUTH_INVALID_CREDENTIALS);
		}
		if (STTS_SUSPENDED.equals(account.getAcntStts())) {
			throw new BusinessException(AuthErrorCode.AUTH_ACCOUNT_SUSPENDED);
		}
		if (STTS_REJECTED.equals(account.getAcntStts())) {
			throw new BusinessException(AuthErrorCode.AUTH_ACCOUNT_REJECTED);
		}
		List<String> permissions = authMapper.selectAuthCdsByUserSn(userSn);
		if (!permissions.contains(CHNL_LOGIN_USER)) {
			// 소셜은 USER 채널 자격 필수
			throw new BusinessException(AuthErrorCode.AUTH_INVALID_CREDENTIALS);
		}
		// 신규 로그인/가입 — 현재 세대로 발급(이메일 로그인과 동일 정책). MUST ② 수용 경합은 이메일 로그인 주석 참고.
		long generation = authRevocationStore.currentGeneration(userSn);
		return authTokenIssuer.issue(account, permissions, CHNL_USER, generation);
	}
}
