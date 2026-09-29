package egovframework.eeum.settlement.vo;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

/**
 * 파트너 정산 실행기록 VO — PARTNER_SETTLEMENT 매핑(회차·파트너 UNIQUE, 실행 시점 스냅샷).
 */
@Getter
@Setter
public class PartnerSettlementVO {

	/** 파트너정산일련번호(PK). */
	private Long partnerSettlementSn;

	/** 정산 회차(YYYY-MM). */
	private String roundYm;

	/** 파트너일련번호. */
	private Long prtnrSn;

	/** 총 판매금(스냅샷). */
	private Integer totalSalesAmt;

	/** 카드 수수료(스냅샷). */
	private Integer cardFeeAmt;

	/** 플랫폼 수수료(스냅샷). */
	private Integer platformFeeAmt;

	/** 정산금(스냅샷). */
	private Integer settlementAmt;

	/** 정산상태(COMPLETED). */
	private String settlementSttsCd;

	/** 실행일시. */
	private LocalDateTime executedAt;

	/** 등록자(실행자). */
	private Long rgtrSn;
}
