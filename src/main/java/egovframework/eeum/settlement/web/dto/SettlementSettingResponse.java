package egovframework.eeum.settlement.web.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;

import egovframework.eeum.settlement.vo.SettlementSettingVO;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 정산 설정 응답.
 */
@Schema(description = "정산 설정 응답")
public record SettlementSettingResponse(
		@Schema(description = "플랫폼 수수료율(%)") BigDecimal platformFeeRate,
		@Schema(description = "카드 수수료율(%)") BigDecimal cardFeeRate,
		@Schema(description = "부가세율(%)") BigDecimal vatRate,
		@Schema(description = "정산기준(CHECKOUT_DATE/CONFIRMED_DATE)") String settlementBasisCd,
		@Schema(description = "입금 예정일(기준일 +N일)") Integer depositDueDays,
		@Schema(description = "정산주기(MONTHLY/WEEKLY/DAILY)") String settlementCycleCd,
		@Schema(description = "최소 정산 금액(원)") Integer minSettlementAmt,
		@Schema(description = "자동 명세서 발행") boolean autoInvoice,
		@Schema(description = "세금계산서 발행") boolean taxInvoice,
		@Schema(description = "수정일시(UTC)") Instant mdfcnDt) {

	public static SettlementSettingResponse from(SettlementSettingVO vo) {
		return new SettlementSettingResponse(
				vo.getPlatformFeeRate(),
				vo.getCardFeeRate(),
				vo.getVatRate(),
				vo.getSettlementBasisCd(),
				vo.getDepositDueDays(),
				vo.getSettlementCycleCd(),
				vo.getMinSettlementAmt(),
				"Y".equals(vo.getAutoInvoiceYn()),
				"Y".equals(vo.getTaxInvoiceYn()),
				vo.getMdfcnDt() == null ? null : vo.getMdfcnDt().toInstant(ZoneOffset.UTC));
	}
}
