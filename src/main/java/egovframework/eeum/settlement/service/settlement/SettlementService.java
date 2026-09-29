package egovframework.eeum.settlement.service.settlement;

import java.util.List;

import egovframework.eeum.auth.vo.AuthUser;
import egovframework.eeum.settlement.web.dto.PartnerSettlementExecuteRequest;
import egovframework.eeum.settlement.web.dto.PartnerSettlementResponse;
import egovframework.eeum.settlement.web.dto.SettlementSettingResponse;
import egovframework.eeum.settlement.web.dto.SettlementSettingUpdateRequest;

/**
 * 정산 서비스 — 정산 설정(조회/저장/초기화) + 파트너 정산(회차 집계 목록 + 실행).
 */
public interface SettlementService {

	/** 정산 설정 조회. */
	SettlementSettingResponse getSettings();

	/** 정산 설정 전체 저장(SETTLEMENT_MNG). 수수료율 0~100·허용 코드 검증. */
	SettlementSettingResponse updateSettings(SettlementSettingUpdateRequest request, AuthUser user);

	/** 정산 설정 초기화(SETTLEMENT_MNG) — 서버 기본값 복원. */
	SettlementSettingResponse resetSettings(AuthUser user);

	/** 회차(YYYY-MM) 파트너별 정산 목록(판매 집계 + 산출 수수료 + 실행상태). */
	List<PartnerSettlementResponse> listPartnerSettlements(String roundYm);

	/** 파트너 정산 실행(SETTLEMENT_MNG) — 회차·파트너 스냅샷 기록. 중복 실행 차단. */
	PartnerSettlementResponse executePartnerSettlement(PartnerSettlementExecuteRequest request, AuthUser user);
}
