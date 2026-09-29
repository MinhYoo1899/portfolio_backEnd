package egovframework.eeum.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import egovframework.eeum.base.exception.BusinessException;
import egovframework.eeum.notification.service.setting.dao.OperatorNotiSettingMapper;
import egovframework.eeum.notification.service.setting.impl.OperatorNotiSettingServiceImpl;
import egovframework.eeum.notification.vo.OperatorNotiSettingVO;
import egovframework.eeum.notification.web.dto.NotificationSettingsResponse;
import egovframework.eeum.notification.web.dto.NotificationSettingsUpdateRequest;

/**
 * 운영자 알림 설정 서비스 검증 — 운영자 경계(DB 검증), 행 부재 시 기본값, VO 매핑, 전체교체 upsert, 비운영자 차단.
 */
class OperatorNotiSettingServiceVerification {

	private static final long OPERATOR = 77L;
	private static final long NON_OPERATOR = 88L; // MEMBER/PARTNER/미존재

	private final OperatorNotiSettingMapper mapper = mock(OperatorNotiSettingMapper.class);
	private final OperatorNotiSettingServiceImpl service = new OperatorNotiSettingServiceImpl(mapper);

	@Test
	@DisplayName("조회 — 운영자 + 행 부재 시 기본값(예약·문의 on, 정산·마케팅 off)")
	void getReturnsDefaultsWhenAbsent() {
		when(mapper.existsOperator(OPERATOR)).thenReturn(true);
		when(mapper.selectByUserSn(OPERATOR)).thenReturn(null);

		NotificationSettingsResponse res = service.getSettings(OPERATOR);
		assertTrue(res.reservationAlert());
		assertTrue(res.inquiryAlert());
		assertFalse(res.settlementAlert());
		assertFalse(res.marketingAlert());
	}

	@Test
	@DisplayName("조회 — 운영자 + 저장행 있으면 그 값으로 매핑(Y/N→boolean)")
	void getMapsStoredRow() {
		when(mapper.existsOperator(OPERATOR)).thenReturn(true);
		OperatorNotiSettingVO vo = new OperatorNotiSettingVO();
		vo.setRsvtNotiYn("N");
		vo.setInqrNotiYn("Y");
		vo.setStlmNotiYn("Y");
		vo.setMktgNotiYn("N");
		when(mapper.selectByUserSn(OPERATOR)).thenReturn(vo);

		NotificationSettingsResponse res = service.getSettings(OPERATOR);
		assertFalse(res.reservationAlert());
		assertTrue(res.inquiryAlert());
		assertTrue(res.settlementAlert());
		assertFalse(res.marketingAlert());
	}

	@Test
	@DisplayName("저장 — 운영자 + 전체교체 upsert(Y/N 변환) + 저장값 그대로 반환")
	void updateUpsertsAndEchoes() {
		when(mapper.existsOperator(OPERATOR)).thenReturn(true);
		var req = new NotificationSettingsUpdateRequest(true, false, true, false);

		NotificationSettingsResponse res = service.updateSettings(OPERATOR, req);

		verify(mapper).upsert(OPERATOR, "Y", "N", "Y", "N");
		assertTrue(res.reservationAlert());
		assertFalse(res.inquiryAlert());
		assertTrue(res.settlementAlert());
		assertFalse(res.marketingAlert());
	}

	@Test
	@DisplayName("차단 — 비운영자(MEMBER/PARTNER/미존재) GET 은 403, 설정 조회 안 함")
	void getBlockedForNonOperator() {
		when(mapper.existsOperator(NON_OPERATOR)).thenReturn(false);

		assertThrows(BusinessException.class, () -> service.getSettings(NON_OPERATOR));
		verify(mapper, never()).selectByUserSn(anyLong());
	}

	@Test
	@DisplayName("차단 — 비운영자 PUT 은 403, upsert 미호출")
	void putBlockedForNonOperatorNoUpsert() {
		when(mapper.existsOperator(NON_OPERATOR)).thenReturn(false);
		var req = new NotificationSettingsUpdateRequest(true, true, true, true);

		assertThrows(BusinessException.class, () -> service.updateSettings(NON_OPERATOR, req));
		verify(mapper, never()).upsert(anyLong(), anyString(), anyString(), anyString(), anyString());
	}
}
