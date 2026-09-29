package egovframework.eeum.reservation.web.dto;

import java.time.LocalDate;

import egovframework.eeum.reservation.vo.OperatorReservationVO;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 운영자 예약 목록 항목 응답. 상태는 예약상태코드(SSOT) + 표시 라벨. 게스트 전화(PII)·결제수단·예약경로는 미포함.
 */
@Schema(description = "운영자 예약 목록 항목")
public record OperatorReservationListItemResponse(
		@Schema(description = "예약일련번호") Long reservationId,
		@Schema(description = "주문번호(예약번호)") String orderNo,
		@Schema(description = "예약상태코드") String statusCd,
		@Schema(description = "예약상태 라벨") String statusLabel,
		@Schema(description = "고택명") String houseName,
		@Schema(description = "객실명") String roomName,
		@Schema(description = "예약자명") String guestName,
		@Schema(description = "투숙 인원") Integer guestCount,
		@Schema(description = "체크인(KST)") LocalDate checkIn,
		@Schema(description = "체크아웃(KST)") LocalDate checkOut,
		@Schema(description = "박수") Integer nights,
		@Schema(description = "결제금액(KRW)") Integer paymentAmount,
		@Schema(description = "결제상태코드(없으면 null)") String paymentStatusCd) {

	public static OperatorReservationListItemResponse from(OperatorReservationVO vo) {
		return new OperatorReservationListItemResponse(
				vo.getReservationSn(),
				vo.getOrderNo(),
				vo.getReservationSttsCd(),
				OperatorReservationStatusLabel.of(vo.getReservationSttsCd()),
				vo.getHouseNm(),
				vo.getRoomNmSnap(),
				vo.getGuestNm(),
				vo.getGuestCnt(),
				vo.getCheckInDate(),
				vo.getCheckOutDate(),
				vo.getNgtCnt(),
				vo.getTotalAmt(),
				vo.getPaymentSttsCd());
	}
}
