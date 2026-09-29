package egovframework.eeum.reservation.service.payment.impl;

import java.util.UUID;

import org.springframework.stereotype.Component;

import egovframework.eeum.reservation.service.payment.PaymentApproval;
import egovframework.eeum.reservation.service.payment.PaymentGateway;

/**
 * 결제 게이트웨이 스텁 — 즉시 승인(approved=true, provider=STUB). 실 PG(Toss) 연동 전 임시 구현.
 *
 * <p>이 슬라이스는 승인 경계만 호출하고 PAYMENT 행은 저장하지 않는다(테이블 영속화 + 웹훅 멱등은 실 Toss 슬라이스).
 * paymentKey 는 추적용 임의값(UUID)만 반환한다.
 */
@Component
public class StubPaymentGateway implements PaymentGateway {

	/** PG제공자코드(스텁). */
	private static final String PROVIDER_STUB = "STUB";

	@Override
	public PaymentApproval approve(String orderNo, int amount, String currencyCd) {
		return new PaymentApproval(true, PROVIDER_STUB, "STUB-" + UUID.randomUUID());
	}
}
