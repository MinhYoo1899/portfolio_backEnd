package egovframework.eeum.coupon;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.Arrays;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import egovframework.eeum.coupon.web.OperatorCouponController;

/**
 * 쿠폰 API 권한 계약 검증 — 조회=COUPON_VIEW, 발행/수정/삭제=COUPON_MNG.
 */
class CouponSecurityVerification {

	@Test
	@DisplayName("쿠폰 엔드포인트 권한 — 조회=COUPON_VIEW, 변경(발행/수정/삭제)=COUPON_MNG")
	void couponEndpoints_authorities() {
		assertAuthority("list", "COUPON_VIEW");
		assertAuthority("detail", "COUPON_VIEW");
		assertAuthority("create", "COUPON_MNG");
		assertAuthority("update", "COUPON_MNG");
		assertAuthority("delete", "COUPON_MNG");
	}

	private static void assertAuthority(String methodName, String authority) {
		Method method = Arrays.stream(OperatorCouponController.class.getDeclaredMethods())
				.filter(m -> m.getName().equals(methodName))
				.findFirst()
				.orElseThrow(() -> new AssertionError(methodName + " 메서드를 찾을 수 없음"));
		PreAuthorize pre = method.getAnnotation(PreAuthorize.class);
		assertNotNull(pre, methodName + " 에 @PreAuthorize 누락");
		assertTrue(pre.value().contains(authority), methodName + " 권한이 " + authority + " 아님: " + pre.value());
	}
}
