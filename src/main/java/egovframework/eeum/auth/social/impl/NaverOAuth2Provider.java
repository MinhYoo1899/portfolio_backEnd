package egovframework.eeum.auth.social.impl;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import egovframework.eeum.auth.exception.AuthErrorCode;
import egovframework.eeum.auth.social.AuthorizationCodeSocialProvider;
import egovframework.eeum.auth.social.OAuthAuthorizeContext;
import egovframework.eeum.auth.social.SocialUser;
import egovframework.eeum.base.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;

/**
 * 네이버 로그인 provider — 레거시 OAuth2.0(access_token → {@code /v1/nid/me} 프로필 조회) 경로(codex 교차검토 확정).
 *
 * <p>OIDC(구글·카카오)와 달리 <b>id_token/JWKS/nonce/PKCE 가 없다</b>. CSRF/재생은 서버의 state(Redis GETDEL)
 * + 흐름 바인딩 쿠키 + 서버측 토큰 교환 + 고정 redirect_uri 로 막는다. authorize 에는 PKCE code_challenge 를
 * 붙이지 않는다(레거시 미지원).
 *
 * <ul>
 *   <li>식별자 = {@code response.id}(앱별 고유) — 없으면 실패. 이메일 자동 병합 금지.</li>
 *   <li>email/name/nickname 은 선택값(미동의 시 누락) — 방어적 처리. name 없으면 nickname 으로 대체.</li>
 *   <li>네이버는 OIDC email_verified 를 주지 않으므로 {@code emailVerified=false}(자동 true 금지).</li>
 *   <li>access_token/refresh_token 은 저장·전달·로그 금지 — nid/me 호출 직후 폐기.</li>
 * </ul>
 */
@Slf4j
@Component
public class NaverOAuth2Provider implements AuthorizationCodeSocialProvider {

	private static final String PROVIDER_CD = "NAVER";
	private static final String NID_RESULT_OK = "00";

	private final String clientId;
	private final String clientSecret;
	private final String authorizeEndpoint;
	private final String tokenEndpoint;
	private final String userInfoEndpoint;
	/** 패키지 가시성 — 테스트에서 MockRestServiceServer 바인딩용(외부 HTTP 교환 검증). */
	final RestTemplate http;
	private final ObjectMapper objectMapper = new ObjectMapper();
	/** client-id/secret 미설정 경고를 요청마다 찍지 않도록 1회만(로그 스팸 방지). */
	private final AtomicBoolean notConfiguredLogged = new AtomicBoolean(false);

	public NaverOAuth2Provider(
			@Value("${eeum.oauth.naver.client-id:}") String clientId,
			@Value("${eeum.oauth.naver.client-secret:}") String clientSecret,
			@Value("${eeum.oauth.naver.authorize-uri}") String authorizeEndpoint,
			@Value("${eeum.oauth.naver.token-uri}") String tokenEndpoint,
			@Value("${eeum.oauth.naver.userinfo-uri}") String userInfoEndpoint) {
		this.clientId = clientId;
		this.clientSecret = clientSecret;
		this.authorizeEndpoint = authorizeEndpoint;
		this.tokenEndpoint = tokenEndpoint;
		this.userInfoEndpoint = userInfoEndpoint;
		SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(Duration.ofSeconds(3));
		factory.setReadTimeout(Duration.ofSeconds(3));
		this.http = new RestTemplate(factory);
	}

	@Override
	public String providerCd() {
		return PROVIDER_CD;
	}

	@Override
	public URI buildAuthorizeUri(OAuthAuthorizeContext ctx) {
		requireConfigured();
		// 레거시 OAuth2.0 — PKCE(code_challenge)·nonce 없음. response_type=code + client_id + redirect_uri + state.
		return UriComponentsBuilder.fromUriString(authorizeEndpoint)
				.queryParam("response_type", "code")
				.queryParam("client_id", clientId)
				.queryParam("redirect_uri", ctx.redirectUri())
				.queryParam("state", ctx.state())
				.encode(StandardCharsets.UTF_8)
				.build()
				.toUri();
	}

	@Override
	public SocialUser exchangeCode(String code, String redirectUri, String codeVerifier, String expectedNonce,
			String state) {
		// codeVerifier·expectedNonce·redirectUri 는 레거시 OAuth2 교환에서 미사용(OIDC 전용 인자).
		requireConfigured();
		if (!StringUtils.hasText(code) || !StringUtils.hasText(state)) {
			throw new BusinessException(AuthErrorCode.AUTH_SOCIAL_TOKEN_INVALID);
		}
		String accessToken = requestAccessToken(code, state, redirectUri);
		return fetchProfile(accessToken);
	}

	/** code → 토큰 엔드포인트 교환(application/x-www-form-urlencoded)으로 access_token 획득. */
	private String requestAccessToken(String code, String state, String redirectUri) {
		MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
		form.add("grant_type", "authorization_code");
		form.add("client_id", clientId);
		form.add("client_secret", clientSecret);
		form.add("code", code);
		form.add("state", state);
		// authorize 때와 동일한 redirect_uri 를 token 요청에도 바인딩(RFC 6749 §4.1.3 + 네이버 명세).
		if (StringUtils.hasText(redirectUri)) {
			form.add("redirect_uri", redirectUri);
		}

		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
		headers.setAccept(List.of(MediaType.APPLICATION_JSON));

		String body;
		try {
			body = http.postForObject(tokenEndpoint, new HttpEntity<>(form, headers), String.class);
		} catch (RestClientException e) {
			// 응답 본문(토큰·오류상세 포함 가능) 로깅 금지 — 예외 유형만.
			log.warn("[OAUTH] NAVER token exchange failed: {}", e.getClass().getSimpleName());
			throw new BusinessException(AuthErrorCode.AUTH_SOCIAL_TOKEN_INVALID);
		}
		String token = readTextField(parse(body), "access_token");
		if (!StringUtils.hasText(token)) {
			log.warn("[OAUTH] NAVER token response missing access_token");
			throw new BusinessException(AuthErrorCode.AUTH_SOCIAL_TOKEN_INVALID);
		}
		return token;
	}

	/** access_token 으로 {@code /v1/nid/me} 프로필 조회 → SocialUser. 호출 직후 토큰은 폐기(저장/전달/로그 금지). */
	private SocialUser fetchProfile(String accessToken) {
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(accessToken);
		headers.setAccept(List.of(MediaType.APPLICATION_JSON));

		String body;
		try {
			body = http.exchange(userInfoEndpoint, HttpMethod.GET, new HttpEntity<>(headers), String.class).getBody();
		} catch (RestClientException e) {
			log.warn("[OAUTH] NAVER profile fetch failed: {}", e.getClass().getSimpleName());
			throw new BusinessException(AuthErrorCode.AUTH_SOCIAL_TOKEN_INVALID);
		}

		JsonNode root = parse(body);
		if (root == null || !NID_RESULT_OK.equals(readTextField(root, "resultcode"))) {
			log.warn("[OAUTH] NAVER profile resultcode not OK");
			throw new BusinessException(AuthErrorCode.AUTH_SOCIAL_TOKEN_INVALID);
		}
		JsonNode response = root.get("response");
		String id = response == null ? null : readTextField(response, "id");
		if (!StringUtils.hasText(id)) {
			// response.id 는 계정 매칭 키 — 없으면 연동 불가.
			log.warn("[OAUTH] NAVER profile missing response.id");
			throw new BusinessException(AuthErrorCode.AUTH_SOCIAL_TOKEN_INVALID);
		}
		String email = readTextField(response, "email");
		String name = readTextField(response, "name");
		if (!StringUtils.hasText(name)) {
			name = readTextField(response, "nickname");
		}
		// 네이버는 OIDC email_verified 클레임이 없으므로 검증여부는 false(자동 true 금지 — codex).
		return new SocialUser(PROVIDER_CD, id, email, false, name);
	}

	private JsonNode parse(String json) {
		if (!StringUtils.hasText(json)) {
			return null;
		}
		try {
			return objectMapper.readTree(json);
		} catch (JsonProcessingException e) {
			log.warn("[OAUTH] NAVER response parse failed");
			return null;
		}
	}

	private static String readTextField(JsonNode node, String field) {
		if (node == null) {
			return null;
		}
		JsonNode value = node.get(field);
		return value != null && value.isTextual() ? value.asText() : null;
	}

	/** client-id/secret 미설정(빈값)이면 거부(fail-safe — env 미주입 시 네이버 로그인 비활성). */
	private void requireConfigured() {
		if (!StringUtils.hasText(clientId) || !StringUtils.hasText(clientSecret)) {
			if (notConfiguredLogged.compareAndSet(false, true)) {
				log.error("[OAUTH] NAVER client-id/secret 미설정 — 네이버 로그인 비활성(env 주입 필요)");
			}
			throw new BusinessException(AuthErrorCode.AUTH_SOCIAL_TOKEN_INVALID);
		}
	}
}
