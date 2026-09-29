package egovframework.eeum.dm.web.dto;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * DM 발송 요청(단일 엔드포인트). 상태는 서버 산정: saveAsDraft→DRAFT / sendTiming=SCHEDULED→SCHEDULED / NOW→SENT(스텁 발송).
 * 내용은 발송/예약 시 필수, 임시저장 시 선택. 실 전송은 벤더 미선정 → 스텁.
 */
@Schema(description = "DM 발송 요청")
public record DmCreateRequest(

		@Schema(description = "제목") @NotBlank @Size(max = 200) String title,

		@Schema(description = "내용(발송/예약 시 필수)") @Size(max = 200000) String content,

		@Schema(description = "채널(ALIMTALK/SMS)") @NotBlank @Size(max = 20) String channelCd,

		@Schema(description = "이미지 URL(선택, 자체 업로드 경로)", example = "/files/download?atchFileSn=12")
		@Size(max = 500) String imageUrl,

		@Schema(description = "발송 대상(ALL/SELECTED)") @NotBlank @Size(max = 20) String targetTypeCd,

		@Schema(description = "발송 인원(SELECTED 시 선택 인원)") @PositiveOrZero Integer targetCount,

		@Schema(description = "발송시점(NOW/SCHEDULED)") @NotBlank @Size(max = 20) String sendTimingCd,

		@Schema(description = "예약 발송 일시(UTC ISO-8601 Z, SCHEDULED 시 필수·미래)") Instant scheduledAt,

		@Schema(description = "임시저장 여부(true=DRAFT)") Boolean saveAsDraft) {
}
