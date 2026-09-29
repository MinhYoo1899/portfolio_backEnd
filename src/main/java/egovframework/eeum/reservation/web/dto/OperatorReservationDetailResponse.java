package egovframework.eeum.reservation.web.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import egovframework.eeum.reservation.vo.OperatorReservationVO;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 운영자 예약 상세 응답. 스키마 부재 필드(결제수단/예약경로/환불/투숙상태)는 {@code unsupported} 로 명시(fake 값 금지 — 결제 도메인 성장 시 제공).
 */
@Schema(description = "운영자 예약 상세")
public record OperatorReservationDetailResponse(
		@Schema(description = "예약일련번호") Long reservationId,
		@Schema(description = "주문번호(예약번호)") String orderNo,
		@Schema(description = "예약상태코드") String statusCd,
		@Schema(description = "예약상태 라벨") String statusLabel,
		@Schema(description = "고택일련번호") Long houseSn,
		@Schema(description = "고택명") String houseName,
		@Schema(description = "객실명") String roomName,
		@Schema(description = "예약자명") String guestName,
		@Schema(description = "투숙 인원") Integer guestCount,
		@Schema(description = "체크인(KST)") LocalDate checkIn,
		@Schema(description = "체크아웃(KST)") LocalDate checkOut,
		@Schema(description = "박수") Integer nights,
		@Schema(description = "결제금액(KRW)") Integer paymentAmount,
		@Schema(description = "결제상태코드(없으면 null)") String paymentStatusCd,
		@Schema(description = "PG제공자코드(없으면 null)") String pgProvider,
		@Schema(description = "결제 승인(확정)일시(UTC, 없으면 null)") Instant confirmedAt,
		@Schema(description = "예약 등록일시(UTC)") Instant regDt,
		@Schema(description = "스키마 부재로 미제공되는 필드(true=미지원)") Unsupported unsupported) {

	/** 화면정의서 요구 필드 중 현 스키마에 없는 항목(fake 금지 — 결제/환불 도메인 성장 시 제공). */
	@Schema(description = "미지원 필드 플래그")
	public record Unsupported(
			@Schema(description = "결제수단(카드/계좌/간편)") boolean paymentMethod,
			@Schema(description = "예약경로(이음/제휴)") boolean reservationChannel,
			@Schema(description = "환불금액/취소일시") boolean refund,
			@Schema(description = "투숙상태(체크인/아웃/노쇼)") boolean stayStatus) {
	}

	public static OperatorReservationDetailResponse from(OperatorReservationVO vo) {
		return new OperatorReservationDetailResponse(
				vo.getReservationSn(),
				vo.getOrderNo(),
				vo.getReservationSttsCd(),
				OperatorReservationStatusLabel.of(vo.getReservationSttsCd()),
				vo.getHouseSn(),
				vo.getHouseNm(),
				vo.getRoomNmSnap(),
				vo.getGuestNm(),
				vo.getGuestCnt(),
				vo.getCheckInDate(),
				vo.getCheckOutDate(),
				vo.getNgtCnt(),
				vo.getTotalAmt(),
				vo.getPaymentSttsCd(),
				vo.getPgPrvdrCd(),
				vo.getAprvDt() == null ? null : vo.getAprvDt().toInstant(ZoneOffset.UTC),
				vo.getRegDt() == null ? null : vo.getRegDt().toInstant(ZoneOffset.UTC),
				new Unsupported(true, true, true, true));
	}
}
