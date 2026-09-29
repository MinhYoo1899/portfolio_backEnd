package egovframework.eeum.house.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 가용성 조회 — 객실별 결과 항목.
 *
 * <p>{@code available=true}면 요청 전 기간 예약 가능하고 {@code totalPrice}가 채워진다. 불가면 false + totalPrice null.
 */
@Schema(description = "가용성 조회 객실 항목")
public record RoomAvailabilityItem(

		@Schema(description = "객실일련번호") Long roomSn,
		@Schema(description = "객실명") String roomNm,
		@Schema(description = "기준인원") Integer bscPrsnCnt,
		@Schema(description = "최대인원") Integer maxPrsnCnt,
		@Schema(description = "예약 가능 여부") boolean available,
		@Schema(description = "예약 불가 사유(SOLD_OUT/CAPACITY/MIN_STAY/null=가능)") String reason,
		@Schema(description = "전 기간 가격 합계(KRW, 가능할 때만)") Long totalPrice) {
}
