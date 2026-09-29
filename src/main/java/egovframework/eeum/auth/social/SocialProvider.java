package egovframework.eeum.auth.social;

/**
 * 소셜 로그인 provider 추상화의 공통 루트 — provider 식별만 책임진다.
 *
 * <p>실제 신원 검증 방식은 capability 인터페이스로 분리한다(전략: codex 교차검토):
 * <ul>
 *   <li>{@link DirectIdTokenSocialProvider} — 클라이언트가 받은 id_token 직전달 검증(현재 {@code /auth/social-login}).</li>
 *   <li>(후속) {@code AuthorizationCodeSocialProvider} — 서버 시작 Authorization Code + PKCE 흐름.</li>
 * </ul>
 * SSO 분리(타 프로젝트 재사용) 대비: provider 추가는 capability 구현체 추가로만.
 */
public interface SocialProvider {

	/** 이 provider 의 코드(공통코드 SOCIAL_PRVDR — 예: GOOGLE). */
	String providerCd();
}
