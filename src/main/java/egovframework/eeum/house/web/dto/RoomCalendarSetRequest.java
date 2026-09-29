package egovframework.eeum.house.web.dto;

import java.time.LocalDate;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * 객실 캘린더 기간 일괄 설정 요청(판매자) — 기간 + (선택)요일 필터로 재고·가격·마감 일괄 upsert.
 *
 * <p>예: 평일 10만원/주말 15만원을 두 번 호출(요일 필터)로 설정. SOLD_QTY는 건드리지 않는다(결제 최종확정 전용).
 */
@Schema(description = "객실 캘린더 기간 일괄 설정 요청")
public record RoomCalendarSetRequest(

		@Schema(description = "객실일련번호")
		@NotNull(message = "객실일련번호가 필요합니다.")
		Long roomSn,

		@Schema(description = "시작 숙박일(KST, 포함)", example = "2026-07-01")
		@NotNull(message = "시작일이 필요합니다.")
		LocalDate startDate,

		@Schema(description = "종료 숙박일(KST, 포함)", example = "2026-07-31")
		@NotNull(message = "종료일이 필요합니다.")
		LocalDate endDate,

		@Schema(description = "적용 요일(ISO: 1=월 ~ 7=일, 선택). 비우면 전체 요일", example = "[6, 7]")
		List<@Min(value = 1, message = "요일은 1~7") @Max(value = 7, message = "요일은 1~7") Integer> daysOfWeek,

		@Schema(description = "판매가능 총수량", example = "1")
		@NotNull(message = "총수량이 필요합니다.")
		@Min(value = 0, message = "총수량은 0 이상이어야 합니다.")
		@Max(value = 100000, message = "총수량이 너무 큽니다.")
		Integer totalQty,

		@Schema(description = "1박 판매가(KRW 정수)", example = "150000")
		@NotNull(message = "판매가가 필요합니다.")
		@Min(value = 0, message = "판매가는 0 이상이어야 합니다.")
		@Max(value = 100000000, message = "판매가가 너무 큽니다.")
		Integer priceAmt,

		@Schema(description = "마감여부(Y=판매중지, 기본 N)", example = "N")
		@Pattern(regexp = "Y|N", message = "마감여부는 Y 또는 N만 허용됩니다.")
		String closedYn) {

	public String closedYnOrDefault() {
		return closedYn == null || closedYn.isBlank() ? "N" : closedYn;
	}
}
