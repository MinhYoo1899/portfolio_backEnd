package egovframework.eeum.reservation.web.dto;

/**
 * 예약상태코드 → 화면 표시 라벨 매핑(운영자 콘솔 표시용). value=실제 코드(분기 기준), 라벨=표시용.
 */
final class OperatorReservationStatusLabel {

	private OperatorReservationStatusLabel() {
	}

	/** 예약상태코드 → 표시 라벨. 미정의 코드는 코드 그대로. */
	static String of(String sttsCd) {
		if (sttsCd == null) {
			return null;
		}
		return switch (sttsCd) {
			case "RESERVED" -> "예약확정";
			case "USED" -> "이용완료";
			case "CANCELLED" -> "취소";
			case "HOLD", "PAYMENT_PENDING" -> "결제대기";
			case "EXPIRED" -> "만료";
			default -> sttsCd;
		};
	}
}
