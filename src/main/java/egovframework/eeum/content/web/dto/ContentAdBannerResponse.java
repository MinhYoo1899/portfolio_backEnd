package egovframework.eeum.content.web.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import egovframework.eeum.content.service.banner.ContentBannerStatus;
import egovframework.eeum.content.vo.ContentAdBannerVO;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 광고배너 응답. {@code status}(EXPOSED/HIDDEN/ENDED)는 backend 산출값(프론트 재계산 금지 — 메인배너와 동일 규칙).
 */
@Schema(description = "광고배너 응답")
public record ContentAdBannerResponse(
		@Schema(description = "광고배너 식별자") Long bannerId,
		@Schema(description = "제목") String title,
		@Schema(description = "부제") String subCopy,
		@Schema(description = "광고이미지 URL") String imageUrl,
		@Schema(description = "노출위치코드(AD_BANNER_POSITION)") String positionCd,
		@Schema(description = "연결대상코드") String linkTargetCd,
		@Schema(description = "연결대상값") String linkTargetVal,
		@Schema(description = "노출시작일(KST)") LocalDate exposeBgnDate,
		@Schema(description = "노출종료일(KST)") LocalDate exposeEndDate,
		@Schema(description = "노출여부(토글값)") boolean isActive,
		@Schema(description = "산출 상태(EXPOSED/HIDDEN/ENDED)") String status,
		@Schema(description = "등록일시(UTC, ISO-8601 Z)") Instant regDt) {

	/** VO + 산출 상태 → 응답. */
	public static ContentAdBannerResponse from(ContentAdBannerVO vo, ContentBannerStatus status) {
		return new ContentAdBannerResponse(
				vo.getContentAdBannerSn(),
				vo.getTitle(),
				vo.getSubCopy(),
				vo.getImgUrl(),
				vo.getPositionCd(),
				vo.getLinkTargetCd(),
				vo.getLinkTargetVal(),
				vo.getExposeBgnDate(),
				vo.getExposeEndDate(),
				"Y".equals(vo.getExposeYn()),
				status.name(),
				vo.getRegDt() == null ? null : vo.getRegDt().toInstant(ZoneOffset.UTC));
	}
}
