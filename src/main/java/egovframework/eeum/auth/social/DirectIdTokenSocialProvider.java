package egovframework.eeum.auth.social;

/**
 * 클라이언트(앱/웹 SDK)가 받은 OIDC {@code id_token} 을 서버로 직접 전달받아 검증하는 provider capability.
 *
 * <p>현재 경로: {@code POST /auth/social-login}. 서버가 authorize 를 시작하지 않으므로 서버 발급 nonce 가 없다
 * (서명/JWKS·iss·aud·exp 로 검증). 서버 시작형 Authorization Code 흐름은 별도 capability 로 분리한다(후속).
 */
public interface DirectIdTokenSocialProvider extends SocialProvider {

	/**
	 * provider 발급 id_token 을 검증(서명/JWKS·iss·aud·exp)하고 신원을 추출한다.
	 *
	 * @param idToken provider 가 발급한 OIDC id_token
	 * @return 검증된 신원
	 * @throws egovframework.eeum.base.exception.BusinessException 검증 실패 시 {@code AUTH_SOCIAL_TOKEN_INVALID}
	 */
	SocialUser verify(String idToken);
}
