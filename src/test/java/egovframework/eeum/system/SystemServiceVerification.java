package egovframework.eeum.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import egovframework.eeum.auth.vo.AuthUser;
import egovframework.eeum.base.exception.BusinessException;
import egovframework.eeum.system.exception.SystemErrorCode;
import egovframework.eeum.system.service.setting.dao.SystemMapper;
import egovframework.eeum.system.service.setting.impl.SystemServiceImpl;
import egovframework.eeum.system.vo.SystemIntegrationVO;
import egovframework.eeum.system.vo.SystemSettingVO;
import egovframework.eeum.system.web.dto.SystemSettingResponse;
import egovframework.eeum.system.web.dto.SystemSettingUpdateRequest;

/**
 * 시스템 설정 서비스 검증 — 조회/저장/초기화/연동목록 + 허용값 validation.
 */
class SystemServiceVerification {

	private final SystemMapper systemMapper = mock(SystemMapper.class);
	private final SystemServiceImpl service = new SystemServiceImpl(systemMapper);

	private static AuthUser user() {
		return new AuthUser(9L, "op@eeum.dev", "운영자", "OPERATOR", "ACTIVE", List.of("SYSTEM_MNG"));
	}

	private SystemSettingVO setting() {
		SystemSettingVO vo = new SystemSettingVO();
		vo.setSystemSettingSn(1L);
		vo.setServiceNm("이음");
		vo.setAutoLogoutMin(30);
		vo.setPswdChgCycleCd("M3");
		return vo;
	}

	private SystemSettingUpdateRequest req(int logout, String cycle) {
		return new SystemSettingUpdateRequest("이음", "1566-0123", "help@ipeum.co.kr", "123-4567", logout, cycle);
	}

	@Test
	@DisplayName("설정 조회 — 없으면 SYSTEM_SETTING_NOT_FOUND")
	void get_notFound() {
		when(systemMapper.selectSetting()).thenReturn(null);
		BusinessException e = assertThrows(() -> service.getSettings());
		assertEquals(SystemErrorCode.SYSTEM_SETTING_NOT_FOUND, e.getErrorCode());
	}

	@Test
	@DisplayName("설정 조회 성공")
	void get_success() {
		when(systemMapper.selectSetting()).thenReturn(setting());
		SystemSettingResponse res = service.getSettings();
		assertEquals("이음", res.serviceName());
		assertEquals(30, res.autoLogoutMinutes());
	}

	@Test
	@DisplayName("저장 — 자동로그아웃 미허용값(15) → SYSTEM_AUTO_LOGOUT_INVALID, 매퍼 미진입")
	void update_invalidLogout() {
		BusinessException e = assertThrows(() -> service.updateSettings(req(15, "M3"), user()));
		assertEquals(SystemErrorCode.SYSTEM_AUTO_LOGOUT_INVALID, e.getErrorCode());
		verify(systemMapper, org.mockito.Mockito.never())
				.updateSetting(any(), any(), any(), any(), anyInt(), any(), anyLong());
	}

	@Test
	@DisplayName("저장 — 비번주기 미허용값(M2) → SYSTEM_PWD_CYCLE_INVALID")
	void update_invalidCycle() {
		BusinessException e = assertThrows(() -> service.updateSettings(req(30, "M2"), user()));
		assertEquals(SystemErrorCode.SYSTEM_PWD_CYCLE_INVALID, e.getErrorCode());
	}

	@Test
	@DisplayName("저장 성공 — updateSetting 위임 + 재조회")
	void update_success() {
		when(systemMapper.updateSetting(eq("이음"), any(), any(), any(), eq(60), eq("M6"), eq(9L))).thenReturn(1);
		when(systemMapper.selectSetting()).thenReturn(setting());
		service.updateSettings(req(60, "M6"), user());
		verify(systemMapper).updateSetting(eq("이음"), any(), any(), any(), eq(60), eq("M6"), eq(9L));
	}

	@Test
	@DisplayName("초기화 — 서버 기본값(이음/30/M3)으로 저장")
	void reset_defaults() {
		when(systemMapper.updateSetting(any(), any(), any(), any(), anyInt(), any(), anyLong())).thenReturn(1);
		when(systemMapper.selectSetting()).thenReturn(setting());
		service.resetSettings(user());
		verify(systemMapper).updateSetting("이음", "1566-0123", "help@ipeum.co.kr", "123-4567", 30, "M3", 9L);
	}

	@Test
	@DisplayName("외부연동 목록 — VO→응답 매핑")
	void integrations_list() {
		SystemIntegrationVO vo = new SystemIntegrationVO();
		vo.setIntegrationCd("TOSS_PAYMENTS");
		vo.setIntegrationNm("토스페이먼츠 (PG)");
		vo.setConnSttsCd("CONNECTED");
		when(systemMapper.selectIntegrations()).thenReturn(List.of(vo));
		var list = service.listIntegrations();
		assertEquals(1, list.size());
		assertEquals("TOSS_PAYMENTS", list.get(0).integrationCd());
		assertEquals("CONNECTED", list.get(0).connectionStatus());
	}

	// ------------------------------------------------------------------

	private static BusinessException assertThrows(org.junit.jupiter.api.function.Executable exec) {
		return org.junit.jupiter.api.Assertions.assertThrows(BusinessException.class, exec);
	}
}
