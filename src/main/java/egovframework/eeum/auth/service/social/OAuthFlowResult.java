package egovframework.eeum.auth.service.social;

import egovframework.eeum.auth.service.social.OAuthResultStore.OAuthResultData;

/**
 * result 토큰 교환 결과(서비스→컨트롤러 내부 전달용) — LOGIN 이면 토큰 발급 완료, SIGNUP 이면 가입 티켓.
 *
 * <p>이 타입은 web 응답 DTO 가 아니라 서비스 계층 내부 전달용이다(컨트롤러가 clientType 으로 refresh 전달 방식·응답 형태를 구성).
 *
 * @param result LOGIN/SIGNUP 종류·식별 정보(토큰 미포함)
 * @param tokens result=LOGIN 일 때 발급된 토큰(refresh 포함 — 컨트롤러가 WEB/APP 분기 전달), SIGNUP 이면 null
 */
public record OAuthFlowResult(
		OAuthResultData result,
		egovframework.eeum.auth.web.dto.AuthTokenResponse tokens) {
}
