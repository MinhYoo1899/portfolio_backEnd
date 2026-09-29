package egovframework.eeum.settlement.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * 파트너 정산 실행 요청("정산하기") — 회차·파트너. 수동 확정(계좌이체 오프라인, 시스템 상태만 COMPLETED 기록).
 */
@Schema(description = "파트너 정산 실행 요청")
public record PartnerSettlementExecuteRequest(

		@Schema(description = "정산 회차(YYYY-MM)") @NotBlank @Pattern(regexp = "^\\d{4}-\\d{2}$") String roundYm,

		@Schema(description = "파트너일련번호") @NotNull Long prtnrSn) {
}
