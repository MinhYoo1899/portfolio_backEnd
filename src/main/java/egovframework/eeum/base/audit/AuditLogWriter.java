package egovframework.eeum.base.audit;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import egovframework.eeum.base.audit.dao.AuditLogInfoMapper;
import lombok.RequiredArgsConstructor;

/**
 * 감사로그 기록기 — 항상 별도 트랜잭션(REQUIRES_NEW)으로 기록 (codex 권고).
 * 본 업무 트랜잭션이 롤백돼도 FAIL 기록은 남고, 기록 실패가 본 업무를 막지 않도록 호출측(AuditRecorder)이 예외를 삼킨다.
 */
@Component
@RequiredArgsConstructor
public class AuditLogWriter {

	private final AuditLogInfoMapper auditLogMapper;

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void write(AuditLogInfoVO auditLog) {
		auditLogMapper.insertAuditLogInfo(auditLog);
	}
}
