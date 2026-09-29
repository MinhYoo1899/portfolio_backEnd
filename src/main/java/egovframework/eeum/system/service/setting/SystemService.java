package egovframework.eeum.system.service.setting;

import java.util.List;

import egovframework.eeum.auth.vo.AuthUser;
import egovframework.eeum.system.web.dto.SystemIntegrationResponse;
import egovframework.eeum.system.web.dto.SystemSettingResponse;
import egovframework.eeum.system.web.dto.SystemSettingUpdateRequest;

/**
 * 시스템 설정 서비스 — 서비스 기본정보 + 보안 설정 조회/저장/초기화, 외부연동 목록.
 */
public interface SystemService {

	/** 시스템 설정 조회. */
	SystemSettingResponse getSettings();

	/** 시스템 설정 전체 저장(SYSTEM_MNG). 허용값 검증. */
	SystemSettingResponse updateSettings(SystemSettingUpdateRequest request, AuthUser user);

	/** 시스템 설정 초기화(SYSTEM_MNG) — 서버 기본값 복원. */
	SystemSettingResponse resetSettings(AuthUser user);

	/** 외부연동 목록(연동상태 포함). */
	List<SystemIntegrationResponse> listIntegrations();
}
