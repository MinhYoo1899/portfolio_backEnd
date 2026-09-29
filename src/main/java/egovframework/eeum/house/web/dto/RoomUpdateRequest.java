package egovframework.eeum.house.web.dto;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 객실 수정 요청(판매자 본인 소유).
 */
@Schema(description = "객실 수정 요청")
public record RoomUpdateRequest(

		@Schema(description = "객실일련번호")
		@NotNull(message = "객실일련번호가 필요합니다.")
		Long roomSn,

		@Schema(description = "객실명(객실유형명)", example = "안채")
		@NotBlank(message = "객실명을 입력해 주세요.")
		@Size(max = 200, message = "객실명은 200자 이내여야 합니다.")
		String roomNm,

		@Schema(description = "기준인원", example = "2")
		@NotNull(message = "기준인원이 필요합니다.")
		@Min(value = 1, message = "기준인원은 1명 이상이어야 합니다.")
		@Max(value = 1000, message = "기준인원이 너무 큽니다.")
		Integer bscPrsnCnt,

		@Schema(description = "최대인원(기준인원 이상)", example = "4")
		@NotNull(message = "최대인원이 필요합니다.")
		@Min(value = 1, message = "최대인원은 1명 이상이어야 합니다.")
		@Max(value = 1000, message = "최대인원이 너무 큽니다.")
		Integer maxPrsnCnt,

		@Schema(description = "기본재고수량(같은유형 객실 수, 기본 1)", example = "1")
		@Min(value = 1, message = "재고수량은 1 이상이어야 합니다.")
		@Max(value = 10000, message = "재고수량이 너무 큽니다.")
		Integer dfltStockQty,

		@Schema(description = "최소숙박박수(기본 1)", example = "1")
		@Min(value = 1, message = "최소숙박박수는 1 이상이어야 합니다.")
		@Max(value = 365, message = "최소숙박박수가 너무 큽니다.")
		Integer minStayNgtCnt,

		@Schema(description = "객실설명(선택)")
		@Size(max = 5000, message = "객실설명은 5000자 이내여야 합니다.")
		String roomDc,

		@Schema(description = "면적(m², 선택)", example = "33.05")
		@DecimalMin(value = "0.0", inclusive = false, message = "면적은 0보다 커야 합니다.")
		@Digits(integer = 5, fraction = 2, message = "면적 형식이 올바르지 않습니다.")
		BigDecimal areaSqm,

		@Schema(description = "방 개수(선택)", example = "2")
		@Min(value = 0, message = "방 개수는 0 이상이어야 합니다.")
		@Max(value = 1000, message = "방 개수가 너무 큽니다.")
		Integer roomCnt,

		@Schema(description = "화장실 개수(선택)", example = "1")
		@Min(value = 0, message = "화장실 개수는 0 이상이어야 합니다.")
		@Max(value = 1000, message = "화장실 개수가 너무 큽니다.")
		Integer bathrmCnt) {

	public int dfltStockQtyOrDefault() {
		return dfltStockQty == null ? 1 : dfltStockQty;
	}

	public int minStayNgtCntOrDefault() {
		return minStayNgtCnt == null ? 1 : minStayNgtCnt;
	}
}
