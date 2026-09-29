package egovframework.eeum.house.web.dto;

import java.time.LocalDate;

import egovframework.eeum.house.vo.RoomCalendarVO;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 객실 캘린더 일자 응답(판매자 관리용).
 *
 * <p>{@code availableQty}는 DB 기준(TOTAL−SOLD). Redis 임시점유(HOLD)는 손님 가용성 조회에서 별도 반영(후속 슬라이스).
 */
@Schema(description = "객실 캘린더 일자")
public record RoomCalendarResponse(

		@Schema(description = "숙박일(KST)") LocalDate stayDate,
		@Schema(description = "판매가능 총수량") Integer totalQty,
		@Schema(description = "확정 판매수량") Integer soldQty,
		@Schema(description = "남은수량(DB 기준 = 총수량−판매수량)") Integer availableQty,
		@Schema(description = "1박 판매가(KRW)") Integer priceAmt,
		@Schema(description = "마감여부(Y/N)") String closedYn,
			@Schema(description = "휴무/마감 사유(마감 시, 없으면 null)") String closeRsn) {

	public static RoomCalendarResponse from(RoomCalendarVO vo) {
		int total = vo.getTotalQty() == null ? 0 : vo.getTotalQty();
		int sold = vo.getSoldQty() == null ? 0 : vo.getSoldQty();
		return new RoomCalendarResponse(
				vo.getStayDate(),
				total,
				sold,
				Math.max(total - sold, 0),
				vo.getPriceAmt(),
				vo.getClosedYn(),
				vo.getCloseRsn());
	}
}
