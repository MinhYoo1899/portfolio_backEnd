package egovframework.eeum.reservation;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.Arrays;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import egovframework.eeum.reservation.web.OperatorReservationController;

/**
 * 운영자 예약 콘솔 권한 계약 검증 — 목록/상세 = RESERVATION_VIEW.
 */
class OperatorReservationSecurityVerification {

	@Test
	@DisplayName("예약 콘솔 엔드포인트 권한 — 목록/상세 = RESERVATION_VIEW")
	void reservationEndpoints_authorities() {
		assertAuthority("list", "RESERVATION_VIEW");
		assertAuthority("detail", "RESERVATION_VIEW");
	}

	private static void assertAuthority(String methodName, String authority) {
		Method method = Arrays.stream(OperatorReservationController.class.getDeclaredMethods())
				.filter(m -> m.getName().equals(methodName))
				.findFirst()
				.orElseThrow(() -> new AssertionError(methodName + " 메서드를 찾을 수 없음"));
		PreAuthorize pre = method.getAnnotation(PreAuthorize.class);
		assertNotNull(pre, methodName + " 에 @PreAuthorize 누락");
		assertTrue(pre.value().contains(authority), methodName + " 권한이 " + authority + " 아님: " + pre.value());
	}
}
