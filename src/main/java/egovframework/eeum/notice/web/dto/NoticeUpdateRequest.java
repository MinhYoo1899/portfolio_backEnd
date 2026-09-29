package egovframework.eeum.notice.web.dto;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 공지 수정 요청 — 제목/내용/대상/발송시점/예약일시 편집. 상태(DRAFT/SCHEDULED/PUBLISHED_ONGOING/CLOSED)는 보존
 * (전이는 작성·cron·종료에서만). 내용은 게시중/예약 상태 수정 시 필수(서비스 검증).
 */
@Schema(description = "공지 수정 요청(상태 보존)")
public record NoticeUpdateRequest(

		@Schema(description = "제목") @NotBlank @Size(max = 200) String title,

		@Schema(description = "내용") @Size(max = 200000) String content,

		@Schema(description = "대상(ALL/ALL_PARTNER/MEMBER/SPECIFIC_PARTNER)") @NotBlank @Size(max = 30) String targetTypeCd,

		@Schema(description = "발송시점(IMMEDIATE/SCHEDULED)") @NotBlank @Size(max = 20) String sendTimingCd,

		@Schema(description = "예약 발송 일시(UTC ISO-8601 Z)") Instant scheduledAt) {
}
