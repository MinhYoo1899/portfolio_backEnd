package egovframework.eeum.settlement;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.Arrays;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import egovframework.eeum.settlement.web.OperatorSettlementController;

/**
 * 정산 API 권한 계약 검증 — 조회/목록=SETTLEMENT_VIEW, 저장/초기화/실행=SETTLEMENT_MNG(SUPERVISOR).
 */
class SettlementSecurityVerification {

	@Test
	@DisplayName("정산 엔드포인트 권한 — 조회=SETTLEMENT_VIEW, 저장/초기화/실행=SETTLEMENT_MNG")
	void settlementEndpoints_authorities() {
		assertAuthority("getSettings", "SETTLEMENT_VIEW");
		assertAuthority("listPartnerSettlements", "SETTLEMENT_VIEW");
		assertAuthority("updateSettings", "SETTLEMENT_MNG");
		assertAuthority("resetSettings", "SETTLEMENT_MNG");
		assertAuthority("executePartnerSettlement", "SETTLEMENT_MNG");
	}

	private static void assertAuthority(String methodName, String authority) {
		Method method = Arrays.stream(OperatorSettlementController.class.getDeclaredMethods())
				.filter(m -> m.getName().equals(methodName))
				.findFirst()
				.orElseThrow(() -> new AssertionError(methodName + " 메서드를 찾을 수 없음"));
		PreAuthorize pre = method.getAnnotation(PreAuthorize.class);
		assertNotNull(pre, methodName + " 에 @PreAuthorize 누락");
		assertTrue(pre.value().contains(authority), methodName + " 권한이 " + authority + " 아님: " + pre.value());
	}
}
