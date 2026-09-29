package egovframework.eeum.settlement.vo;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

/**
 * 파트너 정산 행 VO — 회차(월) 파트너별 판매 집계 + 실행기록 조인.
 *
 * <p>{@code totalSalesAmt} 는 APPROVED 결제 합(집계). 수수료/정산금은 서비스가 설정값으로 산출.
 * {@code executedSn} 이 있으면 정산완료(COMPLETED), 없으면 정산대기(WAITING).
 */
@Getter
@Setter
public class PartnerSettlementRowVO {

	/** 파트너일련번호. */
	private Long prtnrSn;

	/** 파트너 상호(PRTNR_MST.CONM). */
	private String prtnrConm;

	/** 총 판매금(APPROVED 결제 합계, 집계). */
	private Long totalSalesAmt;

	/** 실행기록 일련번호(있으면 정산완료). */
	private Long executedSn;

	/** 실행일시(실행기록 있을 때). */
	private LocalDateTime executedAt;
}
