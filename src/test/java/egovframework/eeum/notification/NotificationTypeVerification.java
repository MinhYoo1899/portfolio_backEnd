package egovframework.eeum.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import egovframework.eeum.notification.service.notification.NotificationType;

/**
 * 알림 유형이 소유하는 계약 검증 — OPS 딥링크 8종, 수신 권한 매핑 8종, 숫자ID만/‘/operator’ 경로 없음.
 */
class NotificationTypeVerification {

	@Test
	@DisplayName("OPS 딥링크 8종 — 유형이 소유(서버 allowlist)")
	void deeplinks() {
		assertEquals("/partner/12", NotificationType.PARTNER_AUTH.link("12"));
		assertEquals("/product?houseSn=45", NotificationType.PRODUCT_REVIEW.link("45"));
		assertEquals("/reservation?reservationId=7", NotificationType.RESERVATION.link("7"));
		assertEquals("/inquiry?inquiryId=9", NotificationType.INQUIRY.link("9"));
		assertEquals("/settlement?settlementId=3", NotificationType.SETTLEMENT.link("3"));
		assertEquals("/coupon?couponId=8", NotificationType.COUPON.link("8"));
		assertEquals("/notice?noticeId=2", NotificationType.NOTICE.link("2"));
		assertEquals("/dm?dmId=5", NotificationType.DM.link("5"));
	}

	@Test
	@DisplayName("수신 권한(requiredAuthority) 8종 매핑")
	void requiredAuthorities() {
		assertEquals("PRTNR_APRV", NotificationType.PARTNER_AUTH.requiredAuthority());
		assertEquals("HOUSE_REVIEW", NotificationType.PRODUCT_REVIEW.requiredAuthority());
		assertEquals("RESERVATION_VIEW", NotificationType.RESERVATION.requiredAuthority());
		assertEquals("INQR_ANS", NotificationType.INQUIRY.requiredAuthority());
		assertEquals("SETTLEMENT_VIEW", NotificationType.SETTLEMENT.requiredAuthority());
		assertEquals("COUPON_VIEW", NotificationType.COUPON.requiredAuthority());
		assertEquals("NOTICE_VIEW", NotificationType.NOTICE.requiredAuthority());
		assertEquals("DM_VIEW", NotificationType.DM.requiredAuthority());
	}

	@Test
	@DisplayName("숫자ID 아님/누락 — 링크 null(경로/쿼리 주입 차단), 옛 '/operator/…' 경로 잔재 없음")
	void linkGuards() {
		assertNull(NotificationType.PARTNER_AUTH.link(null));
		assertNull(NotificationType.PARTNER_AUTH.link("abc"));
		assertNull(NotificationType.PARTNER_AUTH.link("1/../2"));
		for (NotificationType t : NotificationType.values()) {
			assertFalse(t.link("1").startsWith("/operator/"), t + " 은 /operator 경로가 아니어야 함");
		}
	}
}
