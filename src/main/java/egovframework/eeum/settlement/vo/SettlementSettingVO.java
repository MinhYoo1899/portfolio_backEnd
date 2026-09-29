package egovframework.eeum.settlement.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

/**
 * 정산 설정 VO — SETTLEMENT_SETTING 매핑(단일행, SN=1). 수수료율은 % 단위 BigDecimal.
 */
@Getter
@Setter
public class SettlementSettingVO {

	/** 설정일련번호(단일행=1). */
	private Long settlementSettingSn;

	/** 플랫폼 수수료율(%). */
	private BigDecimal platformFeeRate;

	/** 카드 수수료율(%). */
	private BigDecimal cardFeeRate;

	/** 부가세율(%). */
	private BigDecimal vatRate;

	/** 정산기준(CHECKOUT_DATE/CONFIRMED_DATE). */
	private String settlementBasisCd;

	/** 입금 예정일(기준일 +N일). */
	private Integer depositDueDays;

	/** 정산주기(MONTHLY/WEEKLY/DAILY). */
	private String settlementCycleCd;

	/** 최소 정산 금액(원). */
	private Integer minSettlementAmt;

	/** 자동 명세서 발행(Y/N). */
	private String autoInvoiceYn;

	/** 세금계산서 발행(Y/N). */
	private String taxInvoiceYn;

	/** 등록일시. */
	private LocalDateTime regDt;

	/** 등록자. */
	private Long rgtrSn;

	/** 수정일시. */
	private LocalDateTime mdfcnDt;

	/** 수정자. */
	private Long mdfrSn;
}
