package egovframework.eeum.base.audit;

import lombok.Builder;
import lombok.Getter;

/**
 * 감사로그(AUDIT_LOG_INFO) 기록 VO — append-only.
 */
@Getter
@Builder
public class AuditLogInfoVO {

	/** 행위자(사용자일련번호) — 비로그인/시스템 NULL */
	private final Long actrUserSn;

	/** 행위자유형 */
	private final String actrType;

	/** 행위내용 (행위코드 + 부가 JSON — 예: PARTNER_LIST_VIEW {"page":1,...}) */
	private final String actnCn;

	/** 대상유형 */
	private final String trgtType;

	/** 대상식별자 */
	private final String trgtId;

	/** 처리결과 (SUCCESS/FAIL) */
	private final String prcsRslt;

	/** 접속IP */
	private final String cntnIp;

	/** 사용자에이전트 */
	private final String userAgnt;

	/** 상관관계식별자 */
	private final String corrId;
}
