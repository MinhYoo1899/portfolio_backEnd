package egovframework.eeum.content.web.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import egovframework.eeum.content.service.banner.ContentBannerStatus;
import egovframework.eeum.content.vo.ContentRegionVO;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 즐길거리 응답. {@code status}는 backend 산출값(배너와 동일). 조회수/댓글수는 읽기전용 표시값.
 */
@Schema(description = "즐길거리 응답")
public record ContentRegionResponse(
		@Schema(description = "즐길거리 식별자") Long contentId,
		@Schema(description = "카테고리코드") String categoryCd,
		@Schema(description = "유형코드") String typeCd,
		@Schema(description = "지역(시도) 코드") String sidoRegionCd,
		@Schema(description = "대표이미지 URL") String thumbnailUrl,
		@Schema(description = "제목") String title,
		@Schema(description = "경로 요약") String routeSummary,
		@Schema(description = "본문(리치텍스트 HTML)") String body,
		@Schema(description = "조회수(읽기전용)") int viewCount,
		@Schema(description = "댓글수(읽기전용)") int commentCount,
		@Schema(description = "노출시작일(KST)") LocalDate exposeBgnDate,
		@Schema(description = "노출종료일(KST)") LocalDate exposeEndDate,
		@Schema(description = "노출여부(토글값)") boolean isActive,
		@Schema(description = "산출 상태(EXPOSED/HIDDEN/ENDED)") String status,
		@Schema(description = "등록일시(UTC, ISO-8601 Z)") Instant regDt) {

	/** VO + 산출 상태 → 응답. */
	public static ContentRegionResponse from(ContentRegionVO vo, ContentBannerStatus status) {
		return new ContentRegionResponse(
				vo.getContentRegionSn(),
				vo.getCategoryCd(),
				vo.getTypeCd(),
				vo.getSidoRegionCd(),
				vo.getThumbnailUrl(),
				vo.getTitle(),
				vo.getRouteSummary(),
				vo.getBodyCn(),
				vo.getViewCnt() == null ? 0 : vo.getViewCnt(),
				vo.getCmntCnt() == null ? 0 : vo.getCmntCnt(),
				vo.getExposeBgnDate(),
				vo.getExposeEndDate(),
				"Y".equals(vo.getExposeYn()),
				status.name(),
				vo.getRegDt() == null ? null : vo.getRegDt().toInstant(ZoneOffset.UTC));
	}
}
