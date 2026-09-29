package egovframework.eeum.auth.social;

import java.net.URI;

/**
 * 서버 시작형 OIDC Authorization Code + PKCE 흐름 provider capability(Phase 2).
 *
 * <p>{@link DirectIdTokenSocialProvider}(클라가 받은 id_token 직전달)와 달리 <b>서버가 authorize 를 시작</b>한다.
 * 서버가 발급·저장한 state/nonce/PKCE 로 CSRF·재생·주입을 방지하고(전략 §5), 콜백에서 받은 code 를
 * 토큰 엔드포인트에서 교환한 뒤 신원을 추출한다.
 *
 * <p>provider 별 신원 추출 방식이 다르다: OIDC(구글·카카오)는 교환으로 받은 id_token 의 서명/nonce 까지
 * 검증하고, 레거시 OAuth2(네이버)는 access_token 으로 프로필 API 를 호출한다(id_token/nonce/PKCE 없음).
 * 공통 계약은 "code → 검증된 {@link SocialUser}" 이며, OIDC 전용 인자(codeVerifier·expectedNonce)는
 * 비OIDC 구현이 무시한다.
 *
 * <p>SSO 분리(타 프로젝트 재사용) 대비: 이 capability 와 {@link DirectIdTokenSocialProvider} 는 독립적이며,
 * 동일 provider 구현체가 둘 다 구현할 수 있다(검증 방식만 다르고 이후 처리는 공유).
 */
public interface AuthorizationCodeSocialProvider extends SocialProvider {

	/**
	 * authorize 리다이렉트 URL 구성 — {@code response_type=code} + state/nonce/PKCE(S256)/redirect_uri.
	 *
	 * @param ctx 서버가 발급·저장한 1회성 컨텍스트
	 * @return provider authorize 엔드포인트 절대 URI
	 */
	URI buildAuthorizeUri(OAuthAuthorizeContext ctx);

	/**
	 * 콜백 code 를 토큰 엔드포인트에서 교환하고 신원을 추출한다.
	 * OIDC(구글·카카오)는 id_token 검증(서명/iss/aud/exp + nonce), 레거시 OAuth2(네이버)는 access_token→프로필 API.
	 *
	 * @param code          provider 콜백 authorization code
	 * @param redirectUri   authorize 때와 동일한 고정 redirect_uri(네이버 token 요청은 미사용)
	 * @param codeVerifier  PKCE 검증값(OIDC 전용 — 비OIDC 무시)
	 * @param expectedNonce authorize 때 발급·저장한 nonce(OIDC 전용 — id_token nonce 불일치 시 거부, 비OIDC 무시)
	 * @param state         authorize 때 발급한 state(네이버 token 요청 파라미터 — OIDC 무시)
	 * @return 검증된 신원
	 * @throws egovframework.eeum.base.exception.BusinessException 교환/검증 실패 시 {@code AUTH_SOCIAL_TOKEN_INVALID}
	 */
	SocialUser exchangeCode(String code, String redirectUri, String codeVerifier, String expectedNonce, String state);
}
