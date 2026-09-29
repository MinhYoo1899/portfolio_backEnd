package egovframework.eeum.content.web.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import egovframework.eeum.content.service.banner.ContentBannerStatus;
import egovframework.eeum.content.vo.ContentRecHouseVO;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 추천고택 응답. {@code status}는 backend 산출값(배너와 동일 규칙). {@code houseName}/{@code houseAddr}는 HOUSE 조인 표시값.
 */
@Schema(description = "추천고택 응답")
public record ContentRecHouseResponse(
		@Schema(description = "추천고택 식별자") Long recId,
		@Schema(description = "추천분류코드(REC_HOUSE_CATEGORY)") String recCategoryCd,
		@Schema(description = "대상 고택일련번호") Long houseSn,
		@Schema(description = "고택명(표시)") String houseName,
		@Schema(description = "고택 주소(지역 표시)") String houseAddr,
		@Schema(description = "분류 내 표시순서") int sortOrder,
		@Schema(description = "노출시작일(KST)") LocalDate exposeBgnDate,
		@Schema(description = "노출종료일(KST)") LocalDate exposeEndDate,
		@Schema(description = "노출여부(토글값)") boolean isActive,
		@Schema(description = "산출 상태(EXPOSED/HIDDEN/ENDED)") String status,
		@Schema(description = "등록일시(UTC, ISO-8601 Z)") Instant regDt) {

	/** VO + 산출 상태 → 응답. */
	public static ContentRecHouseResponse from(ContentRecHouseVO vo, ContentBannerStatus status) {
		return new ContentRecHouseResponse(
				vo.getContentRecHouseSn(),
				vo.getRecCategoryCd(),
				vo.getHouseSn(),
				vo.getHouseNm(),
				vo.getHouseAddr(),
				vo.getSortOrder(),
				vo.getExposeBgnDate(),
				vo.getExposeEndDate(),
				"Y".equals(vo.getExposeYn()),
				status.name(),
				vo.getRegDt() == null ? null : vo.getRegDt().toInstant(ZoneOffset.UTC));
	}
}
