package egovframework.eeum.notice;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.Arrays;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import egovframework.eeum.notice.web.OperatorNoticeController;

/**
 * 공지 API 권한 계약 검증 — 조회=NOTICE_VIEW, 작성/수정/삭제/종료=NOTICE_MNG.
 */
class NoticeSecurityVerification {

	@Test
	@DisplayName("공지 엔드포인트 권한 — 조회=NOTICE_VIEW, 변경(작성/수정/삭제/종료)=NOTICE_MNG")
	void noticeEndpoints_authorities() {
		assertAuthority("list", "NOTICE_VIEW");
		assertAuthority("detail", "NOTICE_VIEW");
		assertAuthority("create", "NOTICE_MNG");
		assertAuthority("update", "NOTICE_MNG");
		assertAuthority("close", "NOTICE_MNG");
		assertAuthority("delete", "NOTICE_MNG");
	}

	private static void assertAuthority(String methodName, String authority) {
		Method method = Arrays.stream(OperatorNoticeController.class.getDeclaredMethods())
				.filter(m -> m.getName().equals(methodName))
				.findFirst()
				.orElseThrow(() -> new AssertionError(methodName + " 메서드를 찾을 수 없음"));
		PreAuthorize pre = method.getAnnotation(PreAuthorize.class);
		assertNotNull(pre, methodName + " 에 @PreAuthorize 누락");
		assertTrue(pre.value().contains(authority), methodName + " 권한이 " + authority + " 아님: " + pre.value());
	}
}
