package egovframework.eeum.reservation.service.payment;

/**
 * 결제 게이트웨이 경계(승인) — 예약 확정(RESERVED) 시점의 결제 승인만 추상화한다.
 *
 * <p>이 슬라이스는 승인({@link #approve})만 정의한다. 취소/환불(cancel/refund)과 웹훅 멱등(PAYMENT_EVENT),
 * PAYMENT 행 영속화는 실 PG(Toss) 연동 슬라이스에서 추가한다(현재는 스텁이 즉시 승인).
 */
public interface PaymentGateway {

	/**
	 * 결제 승인 — 주문번호/금액/통화로 승인을 요청한다.
	 *
	 * @param orderNo    주문번호(RESERVATION.ORDER_NO 대응)
	 * @param amount     결제금액(KRW 정수)
	 * @param currencyCd 통화코드
	 * @return 승인 결과(승인여부/PG제공자/결제키)
	 */
	PaymentApproval approve(String orderNo, int amount, String currencyCd);
}
