package egovframework.eeum.notice.web.dto;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 공지 작성 요청. 상태는 서버 산정: saveAsDraft→DRAFT / sendTiming=SCHEDULED→SCHEDULED / IMMEDIATE→PUBLISHED_ONGOING(발행+알림).
 * 내용은 임시저장 시 선택, 발행/예약 시 필수(서비스 검증).
 */
@Schema(description = "공지 작성 요청")
public record NoticeCreateRequest(

		@Schema(description = "제목") @NotBlank @Size(max = 200) String title,

		@Schema(description = "내용(발행/예약 시 필수, 임시저장 시 선택)") @Size(max = 200000) String content,

		@Schema(description = "대상(ALL/ALL_PARTNER/MEMBER/SPECIFIC_PARTNER)") @NotBlank @Size(max = 30) String targetTypeCd,

		@Schema(description = "발송시점(IMMEDIATE/SCHEDULED)") @NotBlank @Size(max = 20) String sendTimingCd,

		@Schema(description = "예약 발송 일시(UTC ISO-8601 Z, SCHEDULED 시 필수·미래)") Instant scheduledAt,

		@Schema(description = "임시저장 여부(true=DRAFT)") Boolean saveAsDraft) {
}
