package egovframework.eeum.content;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.Arrays;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import egovframework.eeum.content.web.OperatorContentRecHouseController;

/**
 * 추천고택 API 권한 계약 검증 — 조회=CONTENT_VIEW, 등록/수정/삭제/토글/순서=CONTENT_MNG.
 */
class ContentRecHouseSecurityVerification {

	@Test
	@DisplayName("추천고택 엔드포인트 권한 — 조회=CONTENT_VIEW, 변경(등록/수정/삭제/토글/순서)=CONTENT_MNG")
	void recHouseEndpoints_authorities() {
		assertAuthority("list", "CONTENT_VIEW");
		assertAuthority("detail", "CONTENT_VIEW");
		assertAuthority("create", "CONTENT_MNG");
		assertAuthority("update", "CONTENT_MNG");
		assertAuthority("reorder", "CONTENT_MNG");
		assertAuthority("delete", "CONTENT_MNG");
		assertAuthority("toggle", "CONTENT_MNG");
	}

	private static void assertAuthority(String methodName, String authority) {
		Method method = Arrays.stream(OperatorContentRecHouseController.class.getDeclaredMethods())
				.filter(m -> m.getName().equals(methodName))
				.findFirst()
				.orElseThrow(() -> new AssertionError(methodName + " 메서드를 찾을 수 없음"));
		PreAuthorize pre = method.getAnnotation(PreAuthorize.class);
		assertNotNull(pre, methodName + " 에 @PreAuthorize 누락");
		assertTrue(pre.value().contains(authority), methodName + " 권한이 " + authority + " 아님: " + pre.value());
	}
}
