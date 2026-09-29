package egovframework.eeum.settlement.service.settlement.dao;

import java.math.BigDecimal;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import egovframework.eeum.settlement.vo.PartnerSettlementRowVO;
import egovframework.eeum.settlement.vo.PartnerSettlementVO;
import egovframework.eeum.settlement.vo.SettlementSettingVO;

/**
 * 정산 매퍼 — 정산설정 단일행 조회/저장 + 파트너 회차 판매집계(APPROVED 결제) + 실행기록.
 */
@Mapper
public interface SettlementMapper {

	/** 정산 설정(단일행 SN=1) 조회. 없으면 null. */
	SettlementSettingVO selectSetting();

	/** 정산 설정 전체 저장(단일행 SN=1). 영향행 0 = 없음. */
	int updateSetting(@Param("platformFeeRate") BigDecimal platformFeeRate,
			@Param("cardFeeRate") BigDecimal cardFeeRate, @Param("vatRate") BigDecimal vatRate,
			@Param("settlementBasisCd") String settlementBasisCd, @Param("depositDueDays") int depositDueDays,
			@Param("settlementCycleCd") String settlementCycleCd, @Param("minSettlementAmt") int minSettlementAmt,
			@Param("autoInvoiceYn") String autoInvoiceYn, @Param("taxInvoiceYn") String taxInvoiceYn,
			@Param("mdfrSn") long mdfrSn);

	/** 회차(YYYY-MM) 파트너별 판매 집계(APPROVED 결제, 체크아웃월 기준) + 실행기록 조인. */
	List<PartnerSettlementRowVO> selectPartnerSettlementRows(@Param("roundYm") String roundYm);

	/** 특정 회차·파트너 판매 합(실행 스냅샷용, 없으면 null). */
	Long selectPartnerSalesForRound(@Param("roundYm") String roundYm, @Param("prtnrSn") long prtnrSn);

	/** 이미 실행된 회차·파트너 수(0=미실행). */
	int countExecuted(@Param("roundYm") String roundYm, @Param("prtnrSn") long prtnrSn);

	/** 파트너 정산 실행기록 삽입. REG_DT=UTC_TIMESTAMP(6). */
	int insertPartnerSettlement(PartnerSettlementVO vo);
}
