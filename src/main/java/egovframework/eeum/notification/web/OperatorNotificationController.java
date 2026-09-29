package egovframework.eeum.notification.web;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import egovframework.eeum.auth.vo.AuthUser;
import egovframework.eeum.base.exception.BusinessException;
import egovframework.eeum.base.response.ApiResponse;
import egovframework.eeum.base.response.PageResponse;
import egovframework.eeum.notification.service.notification.OperatorNotificationService;
import egovframework.eeum.notification.web.dto.OperatorNotificationListItemResponse;
import egovframework.eeum.notification.web.dto.UnreadCountResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * 운영자 <b>본인</b> 알림함 — {@code /operator/notifications}. 대상은 항상 토큰 본인(운영자 DB 검증). 최근 90일.
 *
 * <p>인증만 필요(특정 권한코드 불요). 목록/미읽음수/개별읽음/전체읽음.
 */
@Tag(name = "operator-notifications", description = "운영자 본인 알림함(조회/읽음)")
@RestController
@RequestMapping("/operator/notifications")
@RequiredArgsConstructor
public class OperatorNotificationController {

	private final OperatorNotificationService notificationService;

	@Operation(summary = "알림 목록", description = "본인 알림 최근 90일(최신순, 서버페이징).")
	@GetMapping
	public ApiResponse<PageResponse<OperatorNotificationListItemResponse>> list(
			@AuthenticationPrincipal AuthUser user,
			@RequestParam(name = "page", defaultValue = "1") int page,
			@RequestParam(name = "size", defaultValue = "20") int size) {
		return ApiResponse.ok(notificationService.list(requireUser(user).userSn(), page, size));
	}

	@Operation(summary = "미읽음 수", description = "본인 미읽음 실제 수치(표시 캡 99+는 프론트).")
	@GetMapping("/unread-count")
	public ApiResponse<UnreadCountResponse> unreadCount(@AuthenticationPrincipal AuthUser user) {
		return ApiResponse.ok(new UnreadCountResponse(notificationService.unreadCount(requireUser(user).userSn())));
	}

	@Operation(summary = "개별 읽음", description = "본인 알림 1건 읽음 처리(본인 것만, 멱등).")
	@PostMapping("/{notiSn}/read")
	public ApiResponse<Void> read(@AuthenticationPrincipal AuthUser user, @PathVariable("notiSn") long notiSn) {
		notificationService.markRead(requireUser(user).userSn(), notiSn);
		return ApiResponse.ok();
	}

	@Operation(summary = "전체 읽음", description = "본인 미읽음 전체 읽음 처리.")
	@PostMapping("/read-all")
	public ApiResponse<Void> readAll(@AuthenticationPrincipal AuthUser user) {
		notificationService.markAllRead(requireUser(user).userSn());
		return ApiResponse.ok();
	}

	private static AuthUser requireUser(AuthUser user) {
		if (user == null) {
			throw BusinessException.unauthorized();
		}
		return user;
	}
}
