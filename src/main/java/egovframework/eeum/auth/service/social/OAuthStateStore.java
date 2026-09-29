package egovframework.eeum.auth.service.social;

/**
 * OAuth authorize 시작 시 발급하는 1회용 state 저장소(웹 Authorization Code + PKCE).
 *
 * <p>키 {@code oauth:state:{state}}, 짧은 TTL, consume 은 GETDEL(원자 1회 소비 — 재사용/재생 차단).
 * state 와 함께 PKCE codeVerifier·nonce·clientType 등 콜백 검증에 필요한 컨텍스트를 보관한다
 * (codeVerifier·nonce 는 서버에만 존재 — 클라이언트로 노출 금지).
 */
public interface OAuthStateStore {

	/** authorize 컨텍스트를 보관하고 1회용 난수 state 를 발급. */
	String issue(OAuthStateData data);

	/** state 로 컨텍스트를 1회 소비(consume) — 없거나 만료/재사용/형식오류면 null. */
	OAuthStateData consume(String state);

	/**
	 * state 저장값 — 콜백에서 code 교환·로그인/가입 분기에 필요한 1회성 컨텍스트.
	 *
	 * @param providerCd   소셜제공자코드(GOOGLE/KAKAO)
	 * @param purpose      용도(현재 LOGIN 만)
	 * @param clientType   WEB/APP (refresh 전달 분기)
	 * @param codeVerifier PKCE 검증값(서버 전용)
	 * @param nonce        OIDC nonce(id_token 대조)
	 * @param redirectKey  프론트 redirect allowlist 키
	 * @param bindHash     흐름 바인딩 쿠키값의 해시 — 콜백/결과 소비가 흐름을 시작한 브라우저인지 대조(로그인 CSRF 차단)
	 */
	record OAuthStateData(
			String providerCd,
			String purpose,
			String clientType,
			String codeVerifier,
			String nonce,
			String redirectKey,
			String bindHash) {
	}
}
