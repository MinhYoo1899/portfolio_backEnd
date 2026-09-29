package egovframework.eeum.auth.social;

/**
 * 검증된 소셜 인증 결과 — provider 발급 id_token 의 서명/클레임을 검증한 뒤 추출한 신원.
 *
 * <p>보안 원칙(sns-login-strategy §5): 클라이언트가 보낸 email/name/providerUserId 는 신뢰하지 않고,
 * 서명 검증을 통과한 토큰 클레임에서만 추출한다. 계정 키 = {@code providerUserId}(이메일은 식별자 아님).
 *
 * @param providerCd      소셜제공자코드(공통코드 SOCIAL_PRVDR — GOOGLE/KAKAO/NAVER/APPLE)
 * @param providerUserId  제공자측 사용자 식별자(OIDC {@code sub}) — 계정 연결 키
 * @param email           이메일(참고용 — 식별자/자동연결 금지)
 * @param emailVerified   provider 의 이메일 검증여부
 * @param name            표시 이름(없을 수 있음)
 */
public record SocialUser(
		String providerCd,
		String providerUserId,
		String email,
		boolean emailVerified,
		String name) {
}
