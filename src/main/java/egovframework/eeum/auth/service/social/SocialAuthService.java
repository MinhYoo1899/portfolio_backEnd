package egovframework.eeum.auth.service.social;

import egovframework.eeum.auth.web.dto.AuthTokenResponse;
import egovframework.eeum.auth.web.dto.SocialLoginRequest;
import egovframework.eeum.auth.web.dto.SocialLoginResponse;
import egovframework.eeum.auth.web.dto.SocialSignupRequest;

/**
 * 소셜 로그인/가입 서비스 — id_token 검증 후 우리 토큰 발급(기존 계정) 또는 가입 분기(신규).
 *
 * <p>두 진입 경로를 함께 책임진다:
 * <ul>
 *   <li>id_token 직전달({@link #login}/{@link #signup}) — 클라가 받은 id_token 검증(Phase 1).</li>
 *   <li>웹 Authorization Code + PKCE({@link #authorize}/{@link #handleCallback}/{@link #exchangeResult}) — 서버 시작(Phase 2).</li>
 * </ul>
 */
public interface SocialAuthService {

	/** 소셜 로그인 — 검증→연결계정 조회→토큰 발급(LOGIN) 또는 가입 필요(SIGNUP_REQUIRED). */
	SocialLoginResponse login(SocialLoginRequest request, String cntnIp, String userAgnt);

	/** 소셜 가입 완료 — 가입 티켓 소비→계정 생성(약관 동의)→토큰 발급. */
	AuthTokenResponse signup(SocialSignupRequest request, String cntnIp, String userAgnt);

	/**
	 * authorize 시작 — state/nonce/PKCE 생성·저장 후 provider authorize URI 구성(웹 Auth Code).
	 *
	 * @param providerCd  GOOGLE/KAKAO
	 * @param purpose     용도(현재 LOGIN)
	 * @param clientType  WEB/APP
	 * @param redirectKey 프론트 redirect allowlist 키
	 * @return provider authorize 리다이렉트 URI + 흐름 바인딩 쿠키 토큰
	 */
	OAuthAuthorizeResult authorize(String providerCd, String purpose, String clientType, String redirectKey);

	/**
	 * 콜백 처리 — state 소비→흐름 바인딩 대조→code 교환→로그인/가입 분기→result 토큰 발급(토큰 자체는 result 단계에서).
	 *
	 * <p>리다이렉트 대상(프론트 allowlist URL) 해석까지 서비스가 책임진다(allowlist 단일 출처) — 컨트롤러는 302 만.
	 * provider error·state/code 무효·흐름 바인딩 불일치 시 {@link OAuthCallbackOutcome#failed}(default allowlist + 오류표시).
	 *
	 * @param providerError provider 가 콜백에 실은 error 파라미터(없으면 null)
	 * @param flowBinding   흐름 바인딩 쿠키값(없으면 null) — state 의 bindHash 와 대조(로그인 CSRF 차단)
	 * @return 프론트 리다이렉트 대상 + 성공 시 result 토큰
	 */
	OAuthCallbackOutcome handleCallback(String providerCd, String code, String state, String providerError,
			String flowBinding);

	/**
	 * result 토큰 교환 — LOGIN 이면 토큰 발급, SIGNUP 이면 가입 티켓 반환.
	 *
	 * @param flowBinding 흐름 바인딩 쿠키값(없으면 null) — result 의 bindHash 와 대조(로그인 CSRF 차단)
	 */
	OAuthFlowResult exchangeResult(String resultToken, String flowBinding);
}
