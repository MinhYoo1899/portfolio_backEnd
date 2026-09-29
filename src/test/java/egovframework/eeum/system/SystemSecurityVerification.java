package egovframework.eeum.system;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.Arrays;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import egovframework.eeum.system.web.OperatorSystemController;

/**
 * 시스템 설정 API 권한 계약 검증 — 조회=SYSTEM_VIEW, 저장/초기화=SYSTEM_MNG.
 */
class SystemSecurityVerification {

	@Test
	@DisplayName("시스템 엔드포인트 권한 — 조회=SYSTEM_VIEW, 저장/초기화=SYSTEM_MNG")
	void systemEndpoints_authorities() {
		assertAuthority("getSettings", "SYSTEM_VIEW");
		assertAuthority("listIntegrations", "SYSTEM_VIEW");
		assertAuthority("updateSettings", "SYSTEM_MNG");
		assertAuthority("resetSettings", "SYSTEM_MNG");
	}

	private static void assertAuthority(String methodName, String authority) {
		Method method = Arrays.stream(OperatorSystemController.class.getDeclaredMethods())
				.filter(m -> m.getName().equals(methodName))
				.findFirst()
				.orElseThrow(() -> new AssertionError(methodName + " 메서드를 찾을 수 없음"));
		PreAuthorize pre = method.getAnnotation(PreAuthorize.class);
		assertNotNull(pre, methodName + " 에 @PreAuthorize 누락");
		assertTrue(pre.value().contains(authority), methodName + " 권한이 " + authority + " 아님: " + pre.value());
	}
}
