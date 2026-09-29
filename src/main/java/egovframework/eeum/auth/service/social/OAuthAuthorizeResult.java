package egovframework.eeum.auth.service.social;

import java.net.URI;

/**
 * authorize 시작 결과(서비스→컨트롤러) — provider 리다이렉트 URI + 흐름 바인딩 토큰.
 *
 * <p>{@code flowBindingToken} 은 컨트롤러가 HttpOnly 쿠키로 내려, 콜백/결과 소비 단계에서 같은 브라우저인지
 * 대조하는 데 쓴다(로그인 CSRF 차단 — 공격자가 발급한 result 토큰을 피해자 브라우저가 소비하지 못하게).
 * 서버 state 에는 이 토큰의 해시만 저장하므로, 원문 토큰은 쿠키로만 존재한다.
 *
 * @param authorizeUri     provider authorize 리다이렉트 URI
 * @param flowBindingToken HttpOnly 흐름 바인딩 쿠키로 내릴 1회성 난수 토큰
 */
public record OAuthAuthorizeResult(URI authorizeUri, String flowBindingToken) {
}
