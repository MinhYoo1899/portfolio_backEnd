package egovframework.eeum.content.web.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import egovframework.eeum.content.service.banner.ContentBannerStatus;
import egovframework.eeum.content.vo.ContentPopupVO;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 팝업 응답. {@code status}(EXPOSED/HIDDEN/ENDED)는 backend 산출값(프론트 재계산 금지 — 배너와 동일 규칙).
 */
@Schema(description = "팝업 응답")
public record ContentPopupResponse(
		@Schema(description = "팝업 식별자") Long popupId,
		@Schema(description = "팝업이미지 URL") String imageUrl,
		@Schema(description = "팝업 제목") String title,
		@Schema(description = "노출위치코드(POPUP_EXPOSE_LOCATION)") String exposeLocationCd,
		@Schema(description = "노출대상코드(POPUP_EXPOSE_TARGET)") String exposeTargetCd,
		@Schema(description = "연결대상코드") String linkTargetCd,
		@Schema(description = "연결대상값") String linkTargetVal,
		@Schema(description = "노출시작일(KST)") LocalDate exposeBgnDate,
		@Schema(description = "노출종료일(KST)") LocalDate exposeEndDate,
		@Schema(description = "노출여부(토글값)") boolean isActive,
		@Schema(description = "산출 상태(EXPOSED/HIDDEN/ENDED)") String status,
		@Schema(description = "등록일시(UTC, ISO-8601 Z)") Instant regDt) {

	/** VO + 산출 상태 → 응답. */
	public static ContentPopupResponse from(ContentPopupVO vo, ContentBannerStatus status) {
		return new ContentPopupResponse(
				vo.getContentPopupSn(),
				vo.getImgUrl(),
				vo.getTitle(),
				vo.getExposeLocationCd(),
				vo.getExposeTargetCd(),
				vo.getLinkTargetCd(),
				vo.getLinkTargetVal(),
				vo.getExposeBgnDate(),
				vo.getExposeEndDate(),
				"Y".equals(vo.getExposeYn()),
				status.name(),
				vo.getRegDt() == null ? null : vo.getRegDt().toInstant(ZoneOffset.UTC));
	}
}
