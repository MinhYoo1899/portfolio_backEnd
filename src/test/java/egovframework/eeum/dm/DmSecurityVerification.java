package egovframework.eeum.dm;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.Arrays;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import egovframework.eeum.dm.web.OperatorDmController;

/**
 * DM API 권한 계약 검증 — 조회=DM_VIEW, 발송/삭제=DM_MNG.
 */
class DmSecurityVerification {

	@Test
	@DisplayName("DM 엔드포인트 권한 — 조회=DM_VIEW, 변경(발송/삭제)=DM_MNG")
	void dmEndpoints_authorities() {
		assertAuthority("list", "DM_VIEW");
		assertAuthority("detail", "DM_VIEW");
		assertAuthority("create", "DM_MNG");
		assertAuthority("delete", "DM_MNG");
	}

	private static void assertAuthority(String methodName, String authority) {
		Method method = Arrays.stream(OperatorDmController.class.getDeclaredMethods())
				.filter(m -> m.getName().equals(methodName))
				.findFirst()
				.orElseThrow(() -> new AssertionError(methodName + " 메서드를 찾을 수 없음"));
		PreAuthorize pre = method.getAnnotation(PreAuthorize.class);
		assertNotNull(pre, methodName + " 에 @PreAuthorize 누락");
		assertTrue(pre.value().contains(authority), methodName + " 권한이 " + authority + " 아님: " + pre.value());
	}
}
