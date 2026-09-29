package egovframework.eeum.system.web;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import egovframework.eeum.auth.vo.AuthUser;
import egovframework.eeum.base.response.ApiResponse;
import egovframework.eeum.system.service.setting.SystemService;
import egovframework.eeum.system.web.dto.SystemIntegrationResponse;
import egovframework.eeum.system.web.dto.SystemSettingResponse;
import egovframework.eeum.system.web.dto.SystemSettingUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 운영자 시스템 설정 — 조회=SYSTEM_VIEW, 저장/초기화=SYSTEM_MNG(SUPERVISOR 전용).
 *
 * <p>서비스 기본정보 + 보안(자동로그아웃·비번주기) 저장/조회, 외부연동 목록. 보안값 실적용은 별도 범위.
 */
@Tag(name = "operator-system", description = "운영자 시스템 설정 조회/저장/초기화·외부연동")
@RestController
@RequestMapping("/operator/system")
@RequiredArgsConstructor
public class OperatorSystemController {

	private final SystemService systemService;

	@Operation(summary = "시스템 설정 조회(운영자)", description = "권한 SYSTEM_VIEW. 서비스 기본정보 + 보안 설정.")
	@PreAuthorize("hasAuthority('SYSTEM_VIEW')")
	@GetMapping("/settings")
	public ApiResponse<SystemSettingResponse> getSettings() {
		return ApiResponse.ok(systemService.getSettings());
	}

	@Operation(summary = "시스템 설정 저장(운영자)", description = "권한 SYSTEM_MNG. 전체 저장. 자동로그아웃(10/30/60/120)·비번주기(NONE/M1/M3/M6) 검증.")
	@PreAuthorize("hasAuthority('SYSTEM_MNG')")
	@PutMapping("/settings")
	public ApiResponse<SystemSettingResponse> updateSettings(
			@Valid @RequestBody SystemSettingUpdateRequest request, @AuthenticationPrincipal AuthUser user) {
		return ApiResponse.ok(systemService.updateSettings(request, user));
	}

	@Operation(summary = "시스템 설정 초기화(운영자)", description = "권한 SYSTEM_MNG. 서버 기본값 복원.")
	@PreAuthorize("hasAuthority('SYSTEM_MNG')")
	@PostMapping("/settings/reset")
	public ApiResponse<SystemSettingResponse> resetSettings(@AuthenticationPrincipal AuthUser user) {
		return ApiResponse.ok(systemService.resetSettings(user));
	}

	@Operation(summary = "외부연동 목록(운영자)", description = "권한 SYSTEM_VIEW. 연동 6종 + 연동상태(CONNECTED/DISCONNECTED/ERROR).")
	@PreAuthorize("hasAuthority('SYSTEM_VIEW')")
	@GetMapping("/integrations")
	public ApiResponse<List<SystemIntegrationResponse>> listIntegrations() {
		return ApiResponse.ok(systemService.listIntegrations());
	}
}
