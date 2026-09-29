package egovframework.eeum.notification.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * 운영자 알림 설정 변경 요청 — 토글 4종 <b>전체 교체(full replace)</b>. 네 값 모두 필수(부분 PATCH 아님).
 */
@Schema(description = "운영자 알림 설정 변경 요청(4종 전체 교체)")
public record NotificationSettingsUpdateRequest(
		@Schema(description = "예약 알림", example = "true")
		@NotNull(message = "예약 알림 설정값이 필요합니다.") Boolean reservationAlert,

		@Schema(description = "문의 알림", example = "true")
		@NotNull(message = "문의 알림 설정값이 필요합니다.") Boolean inquiryAlert,

		@Schema(description = "정산 알림", example = "false")
		@NotNull(message = "정산 알림 설정값이 필요합니다.") Boolean settlementAlert,

		@Schema(description = "마케팅 알림", example = "false")
		@NotNull(message = "마케팅 알림 설정값이 필요합니다.") Boolean marketingAlert) {
}
