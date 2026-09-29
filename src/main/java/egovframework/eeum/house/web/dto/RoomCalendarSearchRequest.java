package egovframework.eeum.house.web.dto;

import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * 객실 캘린더 조회 요청(판매자) — 객실 + 기간.
 */
@Schema(description = "객실 캘린더 조회 요청")
public record RoomCalendarSearchRequest(

		@Schema(description = "객실일련번호")
		@NotNull(message = "객실일련번호가 필요합니다.")
		Long roomSn,

		@Schema(description = "시작 숙박일(KST, 포함)", example = "2026-07-01")
		@NotNull(message = "시작일이 필요합니다.")
		LocalDate startDate,

		@Schema(description = "종료 숙박일(KST, 포함)", example = "2026-07-31")
		@NotNull(message = "종료일이 필요합니다.")
		LocalDate endDate) {
}
