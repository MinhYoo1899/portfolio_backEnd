package egovframework.eeum.auth.service.social;

/**
 * 콜백 처리 결과(서비스→컨트롤러) — 프론트 리다이렉트 대상 절대 URL + 성공 시 result 토큰.
 *
 * <p>오픈리다이렉트 차단: {@code redirectUrl} 은 항상 allowlist 에서만 해석된 값이다.
 * 실패 시 {@code resultToken=null}, {@code failed=true}(컨트롤러가 {@code ?error=oauth_failed} 부착) — 내부 상세 비노출.
 *
 * @param redirectUrl 프론트 절대 URL(allowlist 해석값)
 * @param resultToken 성공 시 1회용 result 토큰(실패면 null)
 * @param failed      실패 여부(true 면 컨트롤러가 error 파라미터 부착)
 */
public record OAuthCallbackOutcome(
		String redirectUrl,
		String resultToken,
		boolean failed) {

	public static OAuthCallbackOutcome success(String redirectUrl, String resultToken) {
		return new OAuthCallbackOutcome(redirectUrl, resultToken, false);
	}

	public static OAuthCallbackOutcome failed(String redirectUrl) {
		return new OAuthCallbackOutcome(redirectUrl, null, true);
	}
}
