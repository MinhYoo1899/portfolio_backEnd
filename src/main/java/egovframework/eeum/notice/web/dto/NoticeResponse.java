package egovframework.eeum.notice.web.dto;

import java.time.Instant;
import java.time.ZoneOffset;

import egovframework.eeum.notice.vo.NoticeVO;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 공지 응답. status(NOTICE_STTS_CD)는 저장 라이프사이클 값. 일시는 UTC Instant.
 */
@Schema(description = "공지 응답")
public record NoticeResponse(
		@Schema(description = "공지 식별자") Long noticeId,
		@Schema(description = "제목") String title,
		@Schema(description = "내용") String content,
		@Schema(description = "대상코드") String targetTypeCd,
		@Schema(description = "발송시점(IMMEDIATE/SCHEDULED)") String sendTimingCd,
		@Schema(description = "예약 발송 일시(UTC, 없으면 null)") Instant scheduledAt,
		@Schema(description = "발행 일시(UTC, 없으면 null)") Instant publishedAt,
		@Schema(description = "상태(DRAFT/SCHEDULED/PUBLISHED_ONGOING/CLOSED)") String status,
		@Schema(description = "알림 발송 여부") boolean notifySend,
		@Schema(description = "등록일시(UTC)") Instant regDt) {

	public static NoticeResponse from(NoticeVO vo) {
		return new NoticeResponse(
				vo.getNoticeSn(),
				vo.getTitle(),
				vo.getContent(),
				vo.getTargetTypeCd(),
				vo.getSendTimingCd(),
				vo.getScheduledAt() == null ? null : vo.getScheduledAt().toInstant(ZoneOffset.UTC),
				vo.getPublishedAt() == null ? null : vo.getPublishedAt().toInstant(ZoneOffset.UTC),
				vo.getNoticeSttsCd(),
				"Y".equals(vo.getNotifySendYn()),
				vo.getRegDt() == null ? null : vo.getRegDt().toInstant(ZoneOffset.UTC));
	}
}
