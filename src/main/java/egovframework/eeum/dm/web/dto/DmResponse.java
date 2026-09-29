package egovframework.eeum.dm.web.dto;

import java.time.Instant;
import java.time.ZoneOffset;

import egovframework.eeum.dm.vo.DmMessageVO;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DM 응답. status(DM_STTS_CD)는 저장 라이프사이클 값. 일시는 UTC Instant.
 */
@Schema(description = "DM 응답")
public record DmResponse(
		@Schema(description = "DM 식별자") Long dmId,
		@Schema(description = "제목") String title,
		@Schema(description = "내용") String content,
		@Schema(description = "채널(ALIMTALK/SMS)") String channelCd,
		@Schema(description = "이미지 URL") String imageUrl,
		@Schema(description = "발송 대상(ALL/SELECTED)") String targetTypeCd,
		@Schema(description = "발송 인원") Integer targetCount,
		@Schema(description = "발송시점(NOW/SCHEDULED)") String sendTimingCd,
		@Schema(description = "예약 발송 일시(UTC, 없으면 null)") Instant scheduledAt,
		@Schema(description = "발송 완료 일시(UTC, 없으면 null)") Instant sentAt,
		@Schema(description = "상태(SENT/SCHEDULED/DRAFT/FAILED)") String status,
		@Schema(description = "실패 사유") String failReason,
		@Schema(description = "등록일시(UTC)") Instant regDt) {

	public static DmResponse from(DmMessageVO vo) {
		return new DmResponse(
				vo.getDmMessageSn(),
				vo.getTitle(),
				vo.getContent(),
				vo.getChannelCd(),
				vo.getImgUrl(),
				vo.getTargetTypeCd(),
				vo.getTargetCnt(),
				vo.getSendTimingCd(),
				vo.getScheduledAt() == null ? null : vo.getScheduledAt().toInstant(ZoneOffset.UTC),
				vo.getSentAt() == null ? null : vo.getSentAt().toInstant(ZoneOffset.UTC),
				vo.getDmSttsCd(),
				vo.getFailRsn(),
				vo.getRegDt() == null ? null : vo.getRegDt().toInstant(ZoneOffset.UTC));
	}
}
