package egovframework.eeum.auth.social.impl;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.databind.JsonNode;

import egovframework.eeum.auth.exception.AuthErrorCode;
import egovframework.eeum.auth.social.AuthorizationCodeSocialProvider;
import egovframework.eeum.auth.social.DirectIdTokenSocialProvider;
import egovframework.eeum.auth.social.OAuthAuthorizeContext;
import egovframework.eeum.auth.social.SocialUser;
import egovframework.eeum.base.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;

/**
 * OIDC id_token 검증 공통 베이스 — JWKS(RS256) 서명 + iss + aud(우리 client-id) + azp + exp 검증 후 신원 추출.
 *
 * <p>두 capability 를 모두 구현한다(codex 교차검토):
 * <ul>
 *   <li>{@link DirectIdTokenSocialProvider} — 클라가 받은 id_token 직전달 검증(서버 nonce 없음, {@code /auth/social-login}).</li>
 *   <li>{@link AuthorizationCodeSocialProvider} — 서버 시작 Authorization Code + PKCE(Phase 2): authorize URL 구성 +
 *       code→token 교환 + id_token 검증 시 <b>nonce</b> 추가 대조.</li>
 * </ul>
 *
 * <p>구글·카카오 등 OIDC provider 구현체는 (providerCd, clientId, clientSecret, issuer, jwksUri, authorizeEndpoint,
 * tokenEndpoint, scope)만 주입하고, 이름 클레임이 다르면 {@link #extractName}만 재정의한다. JWKS 는 최초 검증 시점
 * 지연 로딩(부팅 안전), connect/read 타임아웃 3초. client-id/secret 미설정(빈값)이면 거부(fail-safe).
 * 클라이언트 값 불신, 토큰 클레임만 사용. 토큰/code/secret/state 원문은 로그 금지.
 */
@Slf4j
public abstract class AbstractOidcSocialProvider
		implements DirectIdTokenSocialProvider, AuthorizationCodeSocialProvider {

	private final String providerCd;
	private final String clientId;
	private final String clientSecret;
	private final String authorizeEndpoint;
	private final String tokenEndpoint;
	private final String scope;
	private final NimbusJwtDecoder decoder;
	private final RestTemplate tokenHttp;
	private final com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
	/** client-id 미설정 경고를 요청마다 찍지 않도록 1회만(로그 스팸 방지). */
	private final AtomicBoolean notConfiguredLogged = new AtomicBoolean(false);

	protected AbstractOidcSocialProvider(String providerCd, String clientId, String clientSecret, String issuer,
			String jwksUri, String authorizeEndpoint, String tokenEndpoint, String scope) {
		this.providerCd = providerCd;
		this.clientId = clientId;
		this.clientSecret = clientSecret;
		this.authorizeEndpoint = authorizeEndpoint;
		this.tokenEndpoint = tokenEndpoint;
		this.scope = scope;
		SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(Duration.ofSeconds(3));
		factory.setReadTimeout(Duration.ofSeconds(3));
		this.tokenHttp = new RestTemplate(factory);
		NimbusJwtDecoder nimbus = NimbusJwtDecoder.withJwkSetUri(jwksUri)
				.restOperations(new RestTemplate(factory))
				.build();
		nimbus.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
				new JwtTimestampValidator(),
				new JwtIssuerValidator(issuer),
				audienceValidator()));
		this.decoder = nimbus;
	}

	@Override
	public String providerCd() {
		return providerCd;
	}

	@Override
	public SocialUser verify(String idToken) {
		// id_token 직전달 경로 — 서버 nonce 없음(기존 동작 유지). client-id 만 필요(secret 불요 — 회귀 0).
		if (!StringUtils.hasText(idToken)) {
			throw new BusinessException(AuthErrorCode.AUTH_SOCIAL_TOKEN_INVALID);
		}
		requireClientId();
		return verifyIdToken(idToken, null);
	}

	@Override
	public URI buildAuthorizeUri(OAuthAuthorizeContext ctx) {
		requireConfigured();
		return UriComponentsBuilder.fromUriString(authorizeEndpoint)
				.queryParam("response_type", "code")
				.queryParam("client_id", clientId)
				.queryParam("redirect_uri", ctx.redirectUri())
				.queryParam("scope", scope)
				.queryParam("state", ctx.state())
				.queryParam("nonce", ctx.nonce())
				.queryParam("code_challenge", ctx.codeChallenge())
				.queryParam("code_challenge_method", "S256")
				.encode(StandardCharsets.UTF_8)
				.build()
				.toUri();
	}

	@Override
	public SocialUser exchangeCode(String code, String redirectUri, String codeVerifier, String expectedNonce,
			String state) {
		// state 는 OIDC 교환에선 불필요(미사용) — 레거시 OAuth2(네이버) 호환을 위한 공통 계약 인자.
		requireConfigured();
		if (!StringUtils.hasText(code) || !StringUtils.hasText(codeVerifier) || !StringUtils.hasText(expectedNonce)) {
			throw new BusinessException(AuthErrorCode.AUTH_SOCIAL_TOKEN_INVALID);
		}
		String idToken = requestIdToken(code, redirectUri, codeVerifier);
		return verifyIdToken(idToken, expectedNonce);
	}

	/** 토큰 엔드포인트에 code 교환 요청(application/x-www-form-urlencoded) 후 응답 JSON 에서 id_token 추출. */
	private String requestIdToken(String code, String redirectUri, String codeVerifier) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
		headers.setAccept(List.of(MediaType.APPLICATION_JSON));

		MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
		form.add("grant_type", "authorization_code");
		form.add("code", code);
		form.add("redirect_uri", redirectUri);
		form.add("client_id", clientId);
		form.add("client_secret", clientSecret);
		form.add("code_verifier", codeVerifier);

		String body;
		try {
			body = tokenHttp.postForObject(tokenEndpoint,
					new org.springframework.http.HttpEntity<>(form, headers), String.class);
		} catch (RestClientException e) {
			// 네트워크/HTTP 오류 — 응답 본문(토큰·오류상세 포함 가능) 로깅 금지, 예외 유형만.
			log.warn("[OAUTH] {} token exchange failed: {}", providerCd, e.getClass().getSimpleName());
			throw new BusinessException(AuthErrorCode.AUTH_SOCIAL_TOKEN_INVALID);
		}
		if (!StringUtils.hasText(body)) {
			throw new BusinessException(AuthErrorCode.AUTH_SOCIAL_TOKEN_INVALID);
		}
		try {
			JsonNode root = objectMapper.readTree(body);
			JsonNode idTokenNode = root.get("id_token");
			if (idTokenNode == null || !idTokenNode.isTextual()) {
				log.warn("[OAUTH] {} token response missing id_token", providerCd);
				throw new BusinessException(AuthErrorCode.AUTH_SOCIAL_TOKEN_INVALID);
			}
			return idTokenNode.asText();
		} catch (com.fasterxml.jackson.core.JsonProcessingException e) {
			log.warn("[OAUTH] {} token response parse failed", providerCd);
			throw new BusinessException(AuthErrorCode.AUTH_SOCIAL_TOKEN_INVALID);
		}
	}

	/**
	 * id_token 서명/iss/aud/exp 검증(공통) + {@code expectedNonce} 가 있으면 nonce 클레임 대조.
	 *
	 * @param expectedNonce null 이면 nonce 검증 생략(직전달 경로), 값이 있으면 불일치 시 거부(Auth Code 경로)
	 */
	private SocialUser verifyIdToken(String idToken, String expectedNonce) {
		Jwt jwt;
		try {
			jwt = decoder.decode(idToken);
		} catch (JwtException e) {
			// 위조/만료/issuer·audience 불일치 — 예외 유형만 로그(공격자 문자열 혼입·열거 방지)
			log.warn("[SOCIAL] {} id_token invalid: {}", providerCd, e.getClass().getSimpleName());
			throw new BusinessException(AuthErrorCode.AUTH_SOCIAL_TOKEN_INVALID);
		}
		if (expectedNonce != null) {
			String nonce = jwt.getClaimAsString("nonce");
			if (!expectedNonce.equals(nonce)) {
				// nonce 불일치 = 재생/주입 — 원문 노출 금지
				log.warn("[OAUTH] {} id_token nonce mismatch", providerCd);
				throw new BusinessException(AuthErrorCode.AUTH_SOCIAL_TOKEN_INVALID);
			}
		}
		String sub = jwt.getSubject();
		if (!StringUtils.hasText(sub)) {
			log.warn("[SOCIAL] {} id_token missing sub", providerCd);
			throw new BusinessException(AuthErrorCode.AUTH_SOCIAL_TOKEN_INVALID);
		}
		return new SocialUser(
				providerCd,
				sub,
				jwt.getClaimAsString("email"),
				Boolean.TRUE.equals(jwt.getClaimAsBoolean("email_verified")),
				extractName(jwt));
	}

	/** client-id 미설정(빈값)이면 소셜 거부(fail-safe) — id_token 직전달 경로(secret 불요). */
	private void requireClientId() {
		if (!StringUtils.hasText(clientId)) {
			logNotConfigured();
			throw new BusinessException(AuthErrorCode.AUTH_SOCIAL_TOKEN_INVALID);
		}
	}

	/** client-id/secret 미설정(빈값)이면 소셜 거부(fail-safe) — Authorization Code 경로(secret 필요). */
	private void requireConfigured() {
		if (!StringUtils.hasText(clientId) || !StringUtils.hasText(clientSecret)) {
			logNotConfigured();
			throw new BusinessException(AuthErrorCode.AUTH_SOCIAL_TOKEN_INVALID);
		}
	}

	private void logNotConfigured() {
		if (notConfiguredLogged.compareAndSet(false, true)) {
			log.error("[SOCIAL] {} client-id/secret 미설정 — 소셜 로그인 비활성(env 주입 필요)", providerCd);
		}
	}

	/** 표시 이름 추출 — 기본 OIDC {@code name}. provider 별 다르면 재정의(예: 카카오 {@code nickname}). */
	protected String extractName(Jwt jwt) {
		return jwt.getClaimAsString("name");
	}

	/** aud 에 우리 client-id 포함 필수 + aud 복수면 azp 가 우리 client-id 여야 함(다른 앱용 토큰 도용 차단). */
	private OAuth2TokenValidator<Jwt> audienceValidator() {
		return jwt -> {
			List<String> aud = jwt.getAudience();
			if (aud == null || !aud.contains(clientId)) {
				return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_audience", "audience 불일치", null));
			}
			if (aud.size() > 1 && !clientId.equals(jwt.getClaimAsString("azp"))) {
				return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_azp", "azp 불일치", null));
			}
			return OAuth2TokenValidatorResult.success();
		};
	}
}
