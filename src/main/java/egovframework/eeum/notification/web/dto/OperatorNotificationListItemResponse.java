package egovframework.eeum.notification.web.dto;

import java.time.Instant;
import java.time.ZoneOffset;

import egovframework.eeum.notification.vo.OperatorNotificationVO;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 운영자 알림 목록 항목 응답.
 */
@Schema(description = "운영자 알림 목록 항목")
public record OperatorNotificationListItemResponse(
		@Schema(description = "알림일련번호(문자열)") String notiId,
		@Schema(description = "유형") String type,
		@Schema(description = "제목") String title,
		@Schema(description = "내용") String body,
		@Schema(description = "대상 엔티티 유형") String targetType,
		@Schema(description = "대상 식별자") String targetId,
		@Schema(description = "이동 링크(서버 allowlist 산출)") String linkUrl,
		@Schema(description = "읽음여부") boolean isRead,
		@Schema(description = "발생일시(UTC, ISO-8601 Z)") Instant createdAt) {

	public static OperatorNotificationListItemResponse from(OperatorNotificationVO vo) {
		return new OperatorNotificationListItemResponse(
				String.valueOf(vo.getOperatorNotiSn()),
				vo.getNotiTypeCd(),
				vo.getNotiTtl(),
				vo.getNotiBody(),
				vo.getTrgtTypeCd(),
				vo.getTrgtId(),
				vo.getLinkUrl(),
				"Y".equals(vo.getReadYn()),
				vo.getRegDt() == null ? null : vo.getRegDt().toInstant(ZoneOffset.UTC));
	}
}
