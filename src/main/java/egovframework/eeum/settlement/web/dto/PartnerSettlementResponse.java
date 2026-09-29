package egovframework.eeum.settlement.web.dto;

import java.time.Instant;
import java.time.ZoneOffset;

import egovframework.eeum.settlement.vo.PartnerSettlementRowVO;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 파트너 정산 응답. 수수료/정산금은 설정값으로 산출. 상태 = 실행기록 유무(WAITING/COMPLETED).
 */
@Schema(description = "파트너 정산 응답")
public record PartnerSettlementResponse(
		@Schema(description = "정산 회차(YYYY-MM)") String roundYm,
		@Schema(description = "파트너일련번호") Long prtnrSn,
		@Schema(description = "파트너 상호") String prtnrConm,
		@Schema(description = "총 판매금(원)") long totalSalesAmt,
		@Schema(description = "카드 수수료(원)") long cardFeeAmt,
		@Schema(description = "플랫폼 수수료(원)") long platformFeeAmt,
		@Schema(description = "정산 예정금/정산금(원)") long settlementAmt,
		@Schema(description = "정산상태(WAITING/COMPLETED)") String statusCd,
		@Schema(description = "정산상태 라벨") String statusLabel,
		@Schema(description = "정산 실행일시(UTC, 완료 시)") Instant executedAt) {

	/** 집계 행 + 산출 수수료로 응답 구성. */
	public static PartnerSettlementResponse of(String roundYm, PartnerSettlementRowVO row,
			long cardFee, long platformFee, long settlement) {
		boolean done = row.getExecutedSn() != null;
		return new PartnerSettlementResponse(
				roundYm,
				row.getPrtnrSn(),
				row.getPrtnrConm(),
				row.getTotalSalesAmt() == null ? 0L : row.getTotalSalesAmt(),
				cardFee,
				platformFee,
				settlement,
				done ? "COMPLETED" : "WAITING",
				done ? "정산완료" : "정산대기",
				row.getExecutedAt() == null ? null : row.getExecutedAt().toInstant(ZoneOffset.UTC));
	}
}
