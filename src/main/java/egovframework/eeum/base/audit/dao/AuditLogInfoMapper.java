package egovframework.eeum.base.audit.dao;

import org.apache.ibatis.annotations.Mapper;

import egovframework.eeum.base.audit.AuditLogInfoVO;

/**
 * 감사로그 매퍼 — XML: {@code egovframework/mapper/eeum/base/AuditLogInfoMapper_mysql.xml}
 */
@Mapper
public interface AuditLogInfoMapper {

	/** 감사로그 기록 (append-only). */
	int insertAuditLogInfo(AuditLogInfoVO auditLog);
}
