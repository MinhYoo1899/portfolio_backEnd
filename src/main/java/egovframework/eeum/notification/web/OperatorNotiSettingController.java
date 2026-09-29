package egovframework.eeum.notification.web;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import egovframework.eeum.auth.vo.AuthUser;
import egovframework.eeum.base.exception.BusinessException;
import egovframework.eeum.base.response.ApiResponse;
import egovframework.eeum.notification.service.setting.OperatorNotiSettingService;
import egovframework.eeum.notification.web.dto.NotificationSettingsResponse;
import egovframework.eeum.notification.web.dto.NotificationSettingsUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 운영자 <b>본인</b> 알림 설정 — {@code /operator/me/notification-settings}. 대상은 항상 토큰 본인.
 *
 * <p>인증만 필요(특정 권한코드 불요). 조회는 행 부재 시 기본값, 저장은 4종 전체교체(upsert).
 */
@Tag(name = "operator-me-notification-settings", description = "운영자 본인 알림 설정(조회/저장)")
@RestController
@RequestMapping("/operator/me/notification-settings")
@RequiredArgsConstructor
public class OperatorNotiSettingController {

	private final OperatorNotiSettingService notiSettingService;

	@Operation(summary = "알림 설정 조회", description = "본인 알림 토글 4종. 설정한 적 없으면 기본값(예약·문의=on, 정산·마케팅=off).")
	@GetMapping
	public ApiResponse<NotificationSettingsResponse> get(@AuthenticationPrincipal AuthUser user) {
		return ApiResponse.ok(notiSettingService.getSettings(requireUser(user).userSn()));
	}

	@Operation(summary = "알림 설정 저장", description = "토글 4종 전체 교체(upsert). 저장 즉시 반영되며 재조회 시 유지. 기존 적재된 알림은 삭제하지 않음.")
	@PutMapping
	public ApiResponse<NotificationSettingsResponse> put(@AuthenticationPrincipal AuthUser user,
			@Valid @RequestBody NotificationSettingsUpdateRequest request) {
		return ApiResponse.ok(notiSettingService.updateSettings(requireUser(user).userSn(), request));
	}

	private static AuthUser requireUser(AuthUser user) {
		if (user == null) {
			throw BusinessException.unauthorized();
		}
		return user;
	}
}
