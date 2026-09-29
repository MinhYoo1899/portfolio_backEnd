package egovframework.eeum.content;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.Arrays;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import egovframework.eeum.content.web.OperatorContentBannerController;
import egovframework.eeum.content.web.OperatorContentUploadController;

/**
 * 콘텐츠 API 권한 계약 검증 — 각 엔드포인트의 @PreAuthorize 권한코드가 계약(CONTENT_VIEW/CONTENT_MNG)과 일치.
 * (선언적 메서드 시큐리티 — 어노테이션 회귀를 리플렉션으로 고정)
 */
class ContentBannerSecurityVerification {

	@Test
	@DisplayName("메인배너 엔드포인트 권한 — 조회=CONTENT_VIEW, 변경=CONTENT_MNG")
	void bannerEndpoints_authorities() {
		assertAuthority(OperatorContentBannerController.class, "list", "CONTENT_VIEW");
		assertAuthority(OperatorContentBannerController.class, "detail", "CONTENT_VIEW");
		assertAuthority(OperatorContentBannerController.class, "create", "CONTENT_MNG");
		assertAuthority(OperatorContentBannerController.class, "update", "CONTENT_MNG");
		assertAuthority(OperatorContentBannerController.class, "toggle", "CONTENT_MNG");
	}

	@Test
	@DisplayName("이미지 업로드 권한 — CONTENT_MNG")
	void uploadEndpoint_authority() {
		assertAuthority(OperatorContentUploadController.class, "uploadImage", "CONTENT_MNG");
	}

	private static void assertAuthority(Class<?> controller, String methodName, String authority) {
		Method method = Arrays.stream(controller.getDeclaredMethods())
				.filter(m -> m.getName().equals(methodName))
				.findFirst()
				.orElseThrow(() -> new AssertionError(methodName + " 메서드를 찾을 수 없음: " + controller.getSimpleName()));
		PreAuthorize pre = method.getAnnotation(PreAuthorize.class);
		assertNotNull(pre, methodName + " 에 @PreAuthorize 누락");
		assertTrue(pre.value().contains(authority),
				methodName + " 권한이 " + authority + " 아님: " + pre.value());
	}
}
