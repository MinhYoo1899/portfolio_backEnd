package egovframework.eeum.notification.web.dto;

import egovframework.eeum.notification.vo.OperatorNotiSettingVO;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 운영자 알림 설정 응답 — 토글 4종(boolean). 행 부재 시 기본값({@link #defaults()}).
 */
@Schema(description = "운영자 알림 설정")
public record NotificationSettingsResponse(
		@Schema(description = "예약 알림(기본 true)", example = "true") boolean reservationAlert,
		@Schema(description = "문의 알림(기본 true)", example = "true") boolean inquiryAlert,
		@Schema(description = "정산 알림(기본 false)", example = "false") boolean settlementAlert,
		@Schema(description = "마케팅 알림(기본 false)", example = "false") boolean marketingAlert) {

	/** 설정 행이 없을 때의 기본값(예약·문의=on, 정산·마케팅=off). */
	public static NotificationSettingsResponse defaults() {
		return new NotificationSettingsResponse(true, true, false, false);
	}

	public static NotificationSettingsResponse from(OperatorNotiSettingVO vo) {
		return new NotificationSettingsResponse(
				"Y".equals(vo.getRsvtNotiYn()),
				"Y".equals(vo.getInqrNotiYn()),
				"Y".equals(vo.getStlmNotiYn()),
				"Y".equals(vo.getMktgNotiYn()));
	}
}
