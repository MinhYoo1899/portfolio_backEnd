package egovframework.eeum.content.web.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import egovframework.eeum.content.service.banner.ContentBannerStatus;
import egovframework.eeum.content.vo.ContentMainBannerVO;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 메인배너 응답. {@code status}(EXPOSED/HIDDEN/ENDED)는 backend 산출값(프론트 재계산 금지).
 */
@Schema(description = "메인배너 응답")
public record ContentBannerResponse(
		@Schema(description = "배너 식별자") Long bannerId,
		@Schema(description = "표시순서") int sortOrder,
		@Schema(description = "배경이미지 URL") String imageUrl,
		@Schema(description = "메인카피") String mainCopy,
		@Schema(description = "서브카피") String subCopy,
		@Schema(description = "연결대상코드") String linkTargetCd,
		@Schema(description = "연결대상값") String linkTargetVal,
		@Schema(description = "노출시작일(KST)") LocalDate exposeBgnDate,
		@Schema(description = "노출종료일(KST)") LocalDate exposeEndDate,
		@Schema(description = "노출여부(토글값)") boolean isActive,
		@Schema(description = "산출 상태(EXPOSED/HIDDEN/ENDED)") String status,
		@Schema(description = "등록일시(UTC, ISO-8601 Z)") Instant regDt) {

	/** VO + 산출 상태 → 응답. */
	public static ContentBannerResponse from(ContentMainBannerVO vo, ContentBannerStatus status) {
		return new ContentBannerResponse(
				vo.getContentMainBannerSn(),
				vo.getSortOrder(),
				vo.getImgUrl(),
				vo.getMainCopy(),
				vo.getSubCopy(),
				vo.getLinkTargetCd(),
				vo.getLinkTargetVal(),
				vo.getExposeBgnDate(),
				vo.getExposeEndDate(),
				"Y".equals(vo.getExposeYn()),
				status.name(),
				vo.getRegDt() == null ? null : vo.getRegDt().toInstant(ZoneOffset.UTC));
	}
}
