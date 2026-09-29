package egovframework.eeum.reservation.service.payment;

/**
 * 결제 승인 결과 — {@code approved}(승인여부), {@code pgProviderCd}(PG제공자코드, 예: STUB/TOSS),
 * {@code paymentKey}(PG 결제키, 스텁은 임의값). PAYMENT 행 영속화는 실 PG 연동 슬라이스에서 이 값으로 채운다.
 */
public record PaymentApproval(boolean approved, String pgProviderCd, String paymentKey) {
}
