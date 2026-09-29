package egovframework.eeum.auth.social.impl;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * 카카오 OIDC id_token 검증 provider — 공통 로직은 {@link AbstractOidcSocialProvider}.
 *
 * <p>카카오 특이: aud=REST API 키, iss=https://kauth.kakao.com, 표시 이름은 {@code nickname} 클레임.
 * 이메일은 비즈 앱 전환(사업자 심사) 후 동의항목 추가 시에만 제공 — 미제공 시 null(식별자는 sub).
 */
@Component
public class KakaoSocialProvider extends AbstractOidcSocialProvider {

	public KakaoSocialProvider(
			@Value("${eeum.oauth.kakao.client-id:}") String clientId,
			@Value("${eeum.oauth.kakao.client-secret:}") String clientSecret,
			@Value("${eeum.oauth.kakao.issuer}") String issuer,
			@Value("${eeum.oauth.kakao.jwks-uri}") String jwksUri,
			@Value("${eeum.oauth.kakao.authorize-uri}") String authorizeUri,
			@Value("${eeum.oauth.kakao.token-uri}") String tokenUri,
			@Value("${eeum.oauth.kakao.scope}") String scope) {
		super("KAKAO", clientId, clientSecret, issuer, jwksUri, authorizeUri, tokenUri, scope);
	}

	/** 카카오는 표준 {@code name} 대신 {@code nickname} 클레임을 표시 이름으로 준다. */
	@Override
	protected String extractName(Jwt jwt) {
		return jwt.getClaimAsString("nickname");
	}
}
