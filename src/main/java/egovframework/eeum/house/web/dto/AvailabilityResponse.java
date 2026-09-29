package egovframework.eeum.house.web.dto;

import java.time.LocalDate;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 가용성 조회 응답 — 요청 기간 + 객실별 가용/가격 목록.
 */
@Schema(description = "가용성 조회 응답")
public record AvailabilityResponse(

		@Schema(description = "고택일련번호") Long houseSn,
		@Schema(description = "체크인일") LocalDate checkInDate,
		@Schema(description = "체크아웃일") LocalDate checkOutDate,
		@Schema(description = "숙박 박수") Integer nights,
		@Schema(description = "객실별 가용/가격") List<RoomAvailabilityItem> rooms) {

	public static AvailabilityResponse of(long houseSn, LocalDate checkIn, LocalDate checkOut, int nights,
			List<RoomAvailabilityItem> rooms) {
		return new AvailabilityResponse(houseSn, checkIn, checkOut, nights, rooms);
	}
}
