package egovframework.eeum.base.audit;

/**
 * 현재 요청의 감사 행위자 식별 — 구현은 인증 도메인이 제공(빈 주입).
 * base→도메인 의존 금지(D15)를 지키기 위한 경계 인터페이스 (codex 권고).
 */
public interface ActorResolver {

	/** 현재 인증 컨텍스트의 행위자. 비로그인 시 {@link AuditActor#anonymous()}. */
	AuditActor resolveActor();
}
