package egovframework.eeum.settlement.web.dto;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 정산 설정 저장 요청(전체 저장). 정산기준/주기 허용값은 서비스 검증.
 */
@Schema(description = "정산 설정 저장 요청")
public record SettlementSettingUpdateRequest(

		@Schema(description = "플랫폼 수수료율(%)") @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal platformFeeRate,

		@Schema(description = "카드 수수료율(%)") @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal cardFeeRate,

		@Schema(description = "부가세율(%)") @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal vatRate,

		@Schema(description = "정산기준(CHECKOUT_DATE/CONFIRMED_DATE)") @NotBlank @Size(max = 20) String settlementBasisCd,

		@Schema(description = "입금 예정일(기준일 +N일)") @NotNull @Min(0) Integer depositDueDays,

		@Schema(description = "정산주기(MONTHLY/WEEKLY/DAILY)") @NotBlank @Size(max = 20) String settlementCycleCd,

		@Schema(description = "최소 정산 금액(원)") @NotNull @Min(0) Integer minSettlementAmt,

		@Schema(description = "자동 명세서 발행") @NotNull Boolean autoInvoice,

		@Schema(description = "세금계산서 발행") @NotNull Boolean taxInvoice) {
}
