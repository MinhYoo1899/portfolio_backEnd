package egovframework.eeum.auth.social.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import egovframework.eeum.auth.social.OAuthAuthorizeContext;
import egovframework.eeum.base.exception.BusinessException;

/**
 * AbstractOidcSocialProvider.buildAuthorizeUri 쿼리 구성 검증 — response_type/PKCE/state/nonce 포함,
 * client-id/secret 미설정 시 거부(fail-safe). JWKS 는 지연 로딩이라 디코더 미접속(외부 HTTP 없음).
 */
class AuthorizeUriBuilderVerification {

	/** 검증용 구체 provider — 실제 google/kakao 와 동일 베이스, 더미 엔드포인트. */
	private static class TestProvider extends AbstractOidcSocialProvider {
		TestProvider(String clientId, String clientSecret) {
			super("GOOGLE", clientId, clientSecret, "https://issuer.example",
					"https://issuer.example/jwks", "https://issuer.example/authorize",
					"https://issuer.example/token", "openid email profile");
		}
	}

	private static Map<String, String> queryParams(URI uri) {
		Map<String, String> params = new HashMap<>();
		String query = uri.getRawQuery();
		if (query == null) {
			return params;
		}
		Arrays.stream(query.split("&")).forEach(pair -> {
			int eq = pair.indexOf('=');
			String key = eq < 0 ? pair : pair.substring(0, eq);
			String val = eq < 0 ? "" : URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
			params.put(key, val);
		});
		return params;
	}

	@Test
	@DisplayName("buildAuthorizeUri — response_type=code + PKCE(S256) + state/nonce/redirect_uri 포함")
	void buildsAuthorizeQuery() {
		TestProvider provider = new TestProvider("client-123", "secret-abc");
		OAuthAuthorizeContext ctx = new OAuthAuthorizeContext(
				"state-xyz", "nonce-xyz", "challenge-xyz", "https://api.example/auth/oauth/google/callback", "WEB");

		URI uri = provider.buildAuthorizeUri(ctx);
		assertTrue(uri.toString().startsWith("https://issuer.example/authorize?"), "authorize 엔드포인트");

		Map<String, String> q = queryParams(uri);
		assertEquals("code", q.get("response_type"));
		assertEquals("client-123", q.get("client_id"));
		assertEquals("https://api.example/auth/oauth/google/callback", q.get("redirect_uri"));
		assertEquals("openid email profile", q.get("scope"));
		assertEquals("state-xyz", q.get("state"));
		assertEquals("nonce-xyz", q.get("nonce"));
		assertEquals("challenge-xyz", q.get("code_challenge"));
		assertEquals("S256", q.get("code_challenge_method"));
	}

	@Test
	@DisplayName("buildAuthorizeUri — client-id/secret 미설정이면 거부(fail-safe)")
	void rejectsWhenNotConfigured() {
		OAuthAuthorizeContext ctx = new OAuthAuthorizeContext("s", "n", "c", "https://cb", "WEB");
		assertThrows(BusinessException.class, () -> new TestProvider("", "secret").buildAuthorizeUri(ctx));
		assertThrows(BusinessException.class, () -> new TestProvider("client", "").buildAuthorizeUri(ctx));
	}

	@Test
	@DisplayName("exchangeCode — verifier/nonce/code 누락이면 거부(외부 HTTP 전 단계)")
	void exchangeRejectsMissingArgs() {
		TestProvider provider = new TestProvider("client", "secret");
		assertThrows(BusinessException.class,
				() -> provider.exchangeCode("", "https://cb", "verifier", "nonce", "state")); // code 빈값
		assertThrows(BusinessException.class,
				() -> provider.exchangeCode("code", "https://cb", "", "nonce", "state")); // verifier 빈값
		assertThrows(BusinessException.class,
				() -> provider.exchangeCode("code", "https://cb", "verifier", "", "state")); // nonce 빈값
	}
}
