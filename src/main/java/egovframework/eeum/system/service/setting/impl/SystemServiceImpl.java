package egovframework.eeum.system.service.setting.impl;

import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import egovframework.eeum.auth.vo.AuthUser;
import egovframework.eeum.base.audit.Audit;
import egovframework.eeum.base.exception.BusinessException;
import egovframework.eeum.system.exception.SystemErrorCode;
import egovframework.eeum.system.service.setting.SystemService;
import egovframework.eeum.system.service.setting.dao.SystemMapper;
import egovframework.eeum.system.vo.SystemSettingVO;
import egovframework.eeum.system.web.dto.SystemIntegrationResponse;
import egovframework.eeum.system.web.dto.SystemSettingResponse;
import egovframework.eeum.system.web.dto.SystemSettingUpdateRequest;
import lombok.RequiredArgsConstructor;

/**
 * 시스템 설정 서비스 구현. 단일행(SN=1) 설정을 조회/전체저장/기본값복원, 외부연동 목록 제공.
 * ★보안값 실적용(세션 타임아웃·비번 만료 로직 연결)은 별도 범위 — 본 서비스는 저장/조회만.
 */
@Service
@RequiredArgsConstructor
public class SystemServiceImpl implements SystemService {

	private static final Set<Integer> ALLOWED_LOGOUT = Set.of(10, 30, 60, 120);
	private static final Set<String> ALLOWED_PWD_CYCLE = Set.of("NONE", "M1", "M3", "M6");

	private final SystemMapper systemMapper;

	@Override
	@Transactional(readOnly = true)
	public SystemSettingResponse getSettings() {
		return SystemSettingResponse.from(requireSetting());
	}

	@Override
	@Transactional
	@Audit(actn = "SYSTEM_SETTING_UPDATE", trgtType = "SYSTEM_SETTING", trgtId = "1")
	public SystemSettingResponse updateSettings(SystemSettingUpdateRequest request, AuthUser user) {
		validateLogout(request.autoLogoutMinutes());
		validatePwdCycle(request.passwordChangeCycleCd());
		if (systemMapper.updateSetting(request.serviceName(), blankToNull(request.csCenterPhone()),
				blankToNull(request.operationEmail()), blankToNull(request.faxNumber()),
				request.autoLogoutMinutes(), request.passwordChangeCycleCd(), user.userSn()) == 0) {
			throw new BusinessException(SystemErrorCode.SYSTEM_SETTING_NOT_FOUND);
		}
		return getSettings();
	}

	@Override
	@Transactional
	@Audit(actn = "SYSTEM_SETTING_RESET", trgtType = "SYSTEM_SETTING", trgtId = "1")
	public SystemSettingResponse resetSettings(AuthUser user) {
		if (systemMapper.updateSetting("이음", "1566-0123", "help@ipeum.co.kr", "123-4567",
				30, "M3", user.userSn()) == 0) {
			throw new BusinessException(SystemErrorCode.SYSTEM_SETTING_NOT_FOUND);
		}
		return getSettings();
	}

	@Override
	@Transactional(readOnly = true)
	public List<SystemIntegrationResponse> listIntegrations() {
		return systemMapper.selectIntegrations().stream().map(SystemIntegrationResponse::from).toList();
	}

	// ------------------------------------------------------------------

	private SystemSettingVO requireSetting() {
		SystemSettingVO vo = systemMapper.selectSetting();
		if (vo == null) {
			throw new BusinessException(SystemErrorCode.SYSTEM_SETTING_NOT_FOUND);
		}
		return vo;
	}

	private void validateLogout(Integer minutes) {
		if (minutes == null || !ALLOWED_LOGOUT.contains(minutes)) {
			throw new BusinessException(SystemErrorCode.SYSTEM_AUTO_LOGOUT_INVALID);
		}
	}

	private void validatePwdCycle(String cycle) {
		if (cycle == null || !ALLOWED_PWD_CYCLE.contains(cycle)) {
			throw new BusinessException(SystemErrorCode.SYSTEM_PWD_CYCLE_INVALID);
		}
	}

	private static String blankToNull(String s) {
		return (s == null || s.isBlank()) ? null : s;
	}
}
