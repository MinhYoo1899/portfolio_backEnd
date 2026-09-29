package egovframework.eeum.house.web.dto;

import java.time.LocalDate;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 객실 캘린더 가격/수량 일괄 설정 요청 — <b>마감여부(CLOSED_YN)는 건드리지 않는다</b>(기존 행 보존, 신규 행은 N).
 *
 * <p>요금/재고 변경이 기존 마감일을 재오픈하지 않도록 {@code /calendar/set}(가격+수량+마감 일괄)에서 분리한 전용 요청.
 * 마감 토글은 {@code /calendar/close} 가 담당(책임 분리). SOLD_QTY 는 보존(결제 최종확정 전용).
 */
@Schema(description = "객실 캘린더 가격/수량 일괄 설정 요청(마감여부 보존)")
public record RoomCalendarPriceStockSetRequest(

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
		Integer priceAmt) {
}
