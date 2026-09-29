package egovframework.eeum.auth.social;

/**
 * authorize URL 구성에 필요한 1회성 컨텍스트 — 서버가 발급·저장한 state/nonce/PKCE 챌린지와 고정 redirect_uri.
 *
 * <p>웹 Authorization Code + PKCE 흐름(Phase 2)에서 {@link AuthorizationCodeSocialProvider#buildAuthorizeUri}
 * 가 provider authorize 엔드포인트 쿼리를 만들 때 쓴다. codeVerifier 원문은 절대 노출하지 않고, 챌린지(S256)만 전달한다.
 *
 * @param state         CSRF 방지 1회용 state (서버 저장값과 대조)
 * @param nonce         OIDC nonce (id_token nonce 클레임과 대조 — 재생/주입 방지)
 * @param codeChallenge BASE64URL(SHA-256(codeVerifier))
 * @param redirectUri   고정 콜백 URI(config 기반 — Host 헤더 동적생성 금지)
 * @param clientType    WEB / APP (refresh 전달 방식 분기용 — result 단계에서 사용)
 */
public record OAuthAuthorizeContext(
		String state,
		String nonce,
		String codeChallenge,
		String redirectUri,
		String clientType) {
}
