package egovframework.eeum.base.audit;

/**
 * 감사 행위자 — base 가 인증 도메인 타입(AuthUser)에 의존하지 않기 위한 최소 표현 (D15).
 *
 * @param actrUserSn 행위자 사용자일련번호 (비로그인/시스템 = null)
 * @param actrType   행위자 유형 (사용자유형코드 또는 ANONYMOUS/SYSTEM)
 */
public record AuditActor(Long actrUserSn, String actrType) {

	public static AuditActor anonymous() {
		return new AuditActor(null, "ANONYMOUS");
	}
}
