package egovframework.eeum.house.web.dto;

import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 가용성 조회 요청(손님) — 고택 + 체크인/아웃 + 인원. 예약가능 객실과 가격합계를 돌려준다.
 *
 * <p>숙박일은 {@code [checkInDate, checkOutDate)} 반개구간(체크아웃일은 숙박하지 않음). 최소 1박.
 */
@Schema(description = "가용성 조회 요청")
public record AvailabilitySearchRequest(

		@Schema(description = "고택일련번호")
		@NotNull(message = "고택일련번호가 필요합니다.")
		Long houseSn,

		@Schema(description = "체크인일(KST, 포함)", example = "2026-07-04")
		@NotNull(message = "체크인일이 필요합니다.")
		LocalDate checkInDate,

		@Schema(description = "체크아웃일(KST, 미포함)", example = "2026-07-06")
		@NotNull(message = "체크아웃일이 필요합니다.")
		LocalDate checkOutDate,

		@Schema(description = "투숙 인원", example = "2")
		@NotNull(message = "인원이 필요합니다.")
		@Min(value = 1, message = "인원은 1명 이상이어야 합니다.")
		Integer guestCount) {
}
