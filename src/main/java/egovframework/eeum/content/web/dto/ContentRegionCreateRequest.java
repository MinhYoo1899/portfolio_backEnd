package egovframework.eeum.content.web.dto;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;

import egovframework.eeum.content.service.banner.ContentExposeMode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 즐길거리 등록 요청. 카테고리/유형/지역은 서비스 검증. 조회수/댓글수는 읽기전용(요청에 없음, 등록 0).
 */
@Schema(description = "즐길거리 등록 요청")
public record ContentRegionCreateRequest(

		@Schema(description = "카테고리코드(REGION_CONTENT_CATEGORY)")
		@NotBlank @Size(max = 50) String categoryCd,

		@Schema(description = "유형코드(REGION_CONTENT_TYPE)")
		@NotBlank @Size(max = 50) String typeCd,

		@Schema(description = "지역(시도 코드)") @NotBlank @Size(max = 50) String sidoRegionCd,

		@Schema(description = "대표이미지 URL(자체 업로드 API 반환 경로)", example = "/files/download?atchFileSn=12")
		@NotBlank @Size(max = 500) String thumbnailUrl,

		@Schema(description = "제목") @NotBlank @Size(max = 200) String title,

		@Schema(description = "경로 요약(선택)") @Size(max = 500) String routeSummary,

		@Schema(description = "본문(리치텍스트 HTML, 선택)") @Size(max = 200000) String body,

		@Schema(description = "노출시작일(KST)") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate exposeBgnDate,

		@Schema(description = "노출종료일(KST)") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate exposeEndDate,

		@Schema(description = "앱 노출 설정(IMMEDIATE=즉시노출 / SAVE_INACTIVE=비활성저장)")
		@NotNull ContentExposeMode exposeMode) {
}
