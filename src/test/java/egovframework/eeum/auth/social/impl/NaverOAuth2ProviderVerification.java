package egovframework.eeum.auth.social.impl;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;

import egovframework.eeum.auth.social.OAuthAuthorizeContext;
import egovframework.eeum.auth.social.SocialUser;
import egovframework.eeum.base.exception.BusinessException;

/**
 * 네이버 provider 검증 — authorize URL 구성(PKCE/nonce 미포함) + client 미설정 fail-safe. (HTTP 교환은 통합 계층.)
 */
class NaverOAuth2ProviderVerification {

	private static final String AUTHORIZE = "https://nid.naver.com/oauth2.0/authorize";
	private static final String TOKEN = "https://nid.naver.com/oauth2.0/token";
	private static final String USERINFO = "https://openapi.naver.com/v1/nid/me";

	private NaverOAuth2Provider provider(String clientId, String clientSecret) {
		return new NaverOAuth2Provider(clientId, clientSecret, AUTHORIZE, TOKEN, USERINFO);
	}

	@Test
	@DisplayName("providerCd = NAVER")
	void providerCd() {
		assertEquals("NAVER", provider("id", "secret").providerCd());
	}

	@Test
	@DisplayName("authorize URL — response_type=code·client_id·redirect_uri·state 포함, PKCE/nonce 미포함")
	void buildAuthorizeUriHasNoPkceNorNonce() {
		String uri = provider("naver-id", "naver-secret")
				.buildAuthorizeUri(new OAuthAuthorizeContext("state-x", "nonce-x", "challenge-x",
						"http://localhost:8082/auth/oauth/naver/callback", "WEB"))
				.toString();

		assertTrue(uri.startsWith(AUTHORIZE), uri);
		assertTrue(uri.contains("response_type=code"), uri);
		assertTrue(uri.contains("client_id=naver-id"), uri);
		assertTrue(uri.contains("state=state-x"), uri);
		assertTrue(uri.contains("redirect_uri="), uri);
		// 레거시 OAuth2 — PKCE/nonce 파라미터를 붙이지 않는다(codex).
		assertFalse(uri.contains("code_challenge"), uri);
		assertFalse(uri.contains("nonce"), uri);
	}

	@Test
	@DisplayName("client-id/secret 미설정이면 authorize/exchange 모두 거부(fail-safe)")
	void rejectsWhenNotConfigured() {
		NaverOAuth2Provider notConfigured = provider("", "");
		OAuthAuthorizeContext ctx = new OAuthAuthorizeContext("s", "n", "c", "http://localhost/cb", "WEB");

		assertThrows(BusinessException.class, () -> notConfigured.buildAuthorizeUri(ctx));
		assertThrows(BusinessException.class,
				() -> notConfigured.exchangeCode("code", "http://localhost/cb", null, null, "s"));
	}

	private static final String CB = "http://localhost:8082/auth/oauth/naver/callback";

	@Test
	@DisplayName("exchangeCode 성공 — token 폼에 state·redirect_uri 포함·PKCE/nonce 미포함, nid/me → SocialUser")
	void exchangeSuccessMapsProfile() {
		NaverOAuth2Provider provider = provider("naver-id", "naver-secret");
		MockRestServiceServer server = MockRestServiceServer.createServer(provider.http);
		server.expect(requestTo(TOKEN))
				.andExpect(method(HttpMethod.POST))
				.andExpect(content().string(containsString("grant_type=authorization_code")))
				.andExpect(content().string(containsString("code=code-1")))
				.andExpect(content().string(containsString("state=state-1")))
				.andExpect(content().string(containsString("redirect_uri=")))
				.andExpect(content().string(not(containsString("code_challenge"))))
				.andExpect(content().string(not(containsString("nonce"))))
				.andRespond(withSuccess("{\"access_token\":\"at-1\",\"token_type\":\"bearer\"}",
						MediaType.APPLICATION_JSON));
		server.expect(requestTo(USERINFO))
				.andExpect(method(HttpMethod.GET))
				.andExpect(header("Authorization", "Bearer at-1"))
				.andRespond(withSuccess(
						"{\"resultcode\":\"00\",\"message\":\"success\","
								+ "\"response\":{\"id\":\"nid-1\",\"email\":\"a@b.com\",\"name\":\"홍길동\"}}",
						MediaType.APPLICATION_JSON));

		SocialUser user = provider.exchangeCode("code-1", CB, null, null, "state-1");

		assertEquals("NAVER", user.providerCd());
		assertEquals("nid-1", user.providerUserId());
		assertEquals("a@b.com", user.email());
		assertEquals("홍길동", user.name());
		assertFalse(user.emailVerified(), "네이버는 email_verified 없음 → false");
		server.verify();
	}

	@Test
	@DisplayName("exchangeCode — name 없으면 nickname 으로 대체, email 누락은 null 허용")
	void exchangeFallsBackToNickname() {
		NaverOAuth2Provider provider = provider("naver-id", "naver-secret");
		MockRestServiceServer server = MockRestServiceServer.createServer(provider.http);
		server.expect(requestTo(TOKEN))
				.andRespond(withSuccess("{\"access_token\":\"at-2\"}", MediaType.APPLICATION_JSON));
		server.expect(requestTo(USERINFO))
				.andRespond(withSuccess(
						"{\"resultcode\":\"00\",\"response\":{\"id\":\"nid-2\",\"nickname\":\"미노\"}}",
						MediaType.APPLICATION_JSON));

		SocialUser user = provider.exchangeCode("code-2", CB, null, null, "state-2");
		assertEquals("nid-2", user.providerUserId());
		assertEquals("미노", user.name());
		assertNull(user.email());
	}

	@Test
	@DisplayName("exchangeCode — nid/me resultcode!=00 이면 거부")
	void exchangeRejectsBadResultCode() {
		NaverOAuth2Provider provider = provider("naver-id", "naver-secret");
		MockRestServiceServer server = MockRestServiceServer.createServer(provider.http);
		server.expect(requestTo(TOKEN))
				.andRespond(withSuccess("{\"access_token\":\"at-3\"}", MediaType.APPLICATION_JSON));
		server.expect(requestTo(USERINFO))
				.andRespond(withSuccess("{\"resultcode\":\"024\",\"message\":\"Authentication failed\"}",
						MediaType.APPLICATION_JSON));

		assertThrows(BusinessException.class,
				() -> provider.exchangeCode("code-3", CB, null, null, "state-3"));
	}

	@Test
	@DisplayName("exchangeCode — response.id 누락이면 거부(계정 매칭 키 없음)")
	void exchangeRejectsMissingId() {
		NaverOAuth2Provider provider = provider("naver-id", "naver-secret");
		MockRestServiceServer server = MockRestServiceServer.createServer(provider.http);
		server.expect(requestTo(TOKEN))
				.andRespond(withSuccess("{\"access_token\":\"at-4\"}", MediaType.APPLICATION_JSON));
		server.expect(requestTo(USERINFO))
				.andRespond(withSuccess("{\"resultcode\":\"00\",\"response\":{\"email\":\"a@b.com\"}}",
						MediaType.APPLICATION_JSON));

		assertThrows(BusinessException.class,
				() -> provider.exchangeCode("code-4", CB, null, null, "state-4"));
	}
}
