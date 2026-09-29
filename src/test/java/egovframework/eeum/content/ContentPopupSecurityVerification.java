package egovframework.eeum.content;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.Arrays;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import egovframework.eeum.content.web.OperatorContentPopupController;

/**
 * 팝업 API 권한 계약 검증 — 조회=CONTENT_VIEW, 등록/수정/삭제/토글=CONTENT_MNG.
 */
class ContentPopupSecurityVerification {

	@Test
	@DisplayName("팝업 엔드포인트 권한 — 조회=CONTENT_VIEW, 변경(등록/수정/삭제/토글)=CONTENT_MNG")
	void popupEndpoints_authorities() {
		assertAuthority(OperatorContentPopupController.class, "list", "CONTENT_VIEW");
		assertAuthority(OperatorContentPopupController.class, "detail", "CONTENT_VIEW");
		assertAuthority(OperatorContentPopupController.class, "create", "CONTENT_MNG");
		assertAuthority(OperatorContentPopupController.class, "update", "CONTENT_MNG");
		assertAuthority(OperatorContentPopupController.class, "delete", "CONTENT_MNG");
		assertAuthority(OperatorContentPopupController.class, "toggle", "CONTENT_MNG");
	}

	private static void assertAuthority(Class<?> controller, String methodName, String authority) {
		Method method = Arrays.stream(controller.getDeclaredMethods())
				.filter(m -> m.getName().equals(methodName))
				.findFirst()
				.orElseThrow(() -> new AssertionError(methodName + " 메서드를 찾을 수 없음: " + controller.getSimpleName()));
		PreAuthorize pre = method.getAnnotation(PreAuthorize.class);
		assertNotNull(pre, methodName + " 에 @PreAuthorize 누락");
		assertTrue(pre.value().contains(authority), methodName + " 권한이 " + authority + " 아님: " + pre.value());
	}
}
