package egovframework.eeum.reservation.web.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import egovframework.eeum.reservation.vo.ReservationVO;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 예약 응답(헤더) — 생성/상세/목록 공용. 게스트 PII 는 절대 노출하지 않는다.
 *
 * <p>발생/만료 시각은 {@code Instant}(UTC, ISO-8601 Z)로 노출(timezone-standard §2). 숙박일은 LocalDate.
 */
@Schema(description = "예약")
public record ReservationResponse(

		@Schema(description = "예약일련번호") Long reservationSn,
		@Schema(description = "주문번호") String orderNo,
		@Schema(description = "예약상태(HOLD/PAYMENT_PENDING/RESERVED/USED/CANCELLED/EXPIRED)") String reservationSttsCd,
		@Schema(description = "체크인 숙박일(KST, 포함)") LocalDate checkInDate,
		@Schema(description = "체크아웃일(KST, 미포함)") LocalDate checkOutDate,
		@Schema(description = "박수") Integer ngtCnt,
		@Schema(description = "투숙인원수") Integer guestCnt,
		@Schema(description = "총결제금액(KRW 정수)") Integer totalAmt,
		@Schema(description = "통화코드") String crncCd,
		@Schema(description = "홀드만료일시(UTC, ISO-8601 Z)") Instant holdExpiry) {

	public static ReservationResponse from(ReservationVO vo) {
		return new ReservationResponse(
				vo.getReservationSn(),
				vo.getOrderNo(),
				vo.getReservationSttsCd(),
				vo.getCheckInDate(),
				vo.getCheckOutDate(),
				vo.getNgtCnt(),
				vo.getGuestCnt(),
				vo.getTotalAmt(),
				vo.getCrncCd(),
				vo.getHoldExpryDt() == null ? null : vo.getHoldExpryDt().toInstant(ZoneOffset.UTC));
	}
}
