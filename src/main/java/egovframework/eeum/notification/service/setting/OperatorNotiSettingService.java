package egovframework.eeum.notification.service.setting;

import egovframework.eeum.notification.web.dto.NotificationSettingsResponse;
import egovframework.eeum.notification.web.dto.NotificationSettingsUpdateRequest;

/**
 * 운영자 본인 알림 설정 — 조회(행 부재 시 기본값)/전체교체 저장(upsert).
 */
public interface OperatorNotiSettingService {

	/** 본인 설정 조회 — 없으면 기본값(예약·문의=on, 정산·마케팅=off). */
	NotificationSettingsResponse getSettings(long userSn);

	/** 본인 설정 전체 교체(upsert) — 저장 후 확정값 반환. */
	NotificationSettingsResponse updateSettings(long userSn, NotificationSettingsUpdateRequest request);
}
