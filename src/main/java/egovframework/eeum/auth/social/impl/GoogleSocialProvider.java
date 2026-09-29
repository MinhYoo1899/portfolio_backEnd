package egovframework.eeum.auth.social.impl;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 구글 OIDC id_token 검증 provider — 공통 로직은 {@link AbstractOidcSocialProvider}.
 * (이름은 표준 {@code name} 클레임 — 기본 동작 사용)
 */
@Component
public class GoogleSocialProvider extends AbstractOidcSocialProvider {

	public GoogleSocialProvider(
			@Value("${eeum.oauth.google.client-id:}") String clientId,
			@Value("${eeum.oauth.google.client-secret:}") String clientSecret,
			@Value("${eeum.oauth.google.issuer}") String issuer,
			@Value("${eeum.oauth.google.jwks-uri}") String jwksUri,
			@Value("${eeum.oauth.google.authorize-uri}") String authorizeUri,
			@Value("${eeum.oauth.google.token-uri}") String tokenUri,
			@Value("${eeum.oauth.google.scope}") String scope) {
		super("GOOGLE", clientId, clientSecret, issuer, jwksUri, authorizeUri, tokenUri, scope);
	}
}
