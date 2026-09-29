package egovframework.eeum.notification.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 미읽음 알림 수 응답 — <b>실제 수치</b>(프론트가 99 초과 시 "99+" 표시). 서버는 캡 없이 반환.
 */
@Schema(description = "미읽음 알림 수")
public record UnreadCountResponse(
		@Schema(description = "미읽음 수(실제값)", example = "7") long unreadCount) {
}
