package egovframework.eeum.house.web.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 손님 공개 가용검색 응답 — 기존 가용성 결과 + 최저가(lowestTotalPrice).
 *
 * <p>불가 객실도 사유(reason)와 함께 포함한다(프론트가 매진/인원초과/최소숙박을 구분 표시).
 * 밤별 단가 배열은 제공하지 않는다 — 총액/박수/최저가만(사용자 결정, 밤별 스냅샷은 예약 생성 시 서버가 별도 확정).
 */
@Schema(description = "공개 가용검색 응답")
public record PublicAvailabilityResponse(

		@Schema(description = "고택일련번호") Long houseSn,
		@Schema(description = "체크인일(포함)") LocalDate checkInDate,
		@Schema(description = "체크아웃일(미포함)") LocalDate checkOutDate,
		@Schema(description = "숙박 박수") Integer nights,
		@Schema(description = "예약 가능 객실 중 최저 총액(KRW, 가능 객실 없으면 null)") Long lowestTotalPrice,
		@Schema(description = "객실별 가용/가격(불가 객실은 reason 포함)") List<RoomAvailabilityItem> rooms) {

	public static PublicAvailabilityResponse from(AvailabilityResponse res) {
		Long lowest = res.rooms() == null ? null
				: res.rooms().stream()
						.filter(RoomAvailabilityItem::available)
						.map(RoomAvailabilityItem::totalPrice)
						.filter(Objects::nonNull)
						.min(Long::compareTo)
						.orElse(null);
		return new PublicAvailabilityResponse(res.houseSn(), res.checkInDate(), res.checkOutDate(),
				res.nights(), lowest, res.rooms() == null ? List.of() : res.rooms());
	}
}
