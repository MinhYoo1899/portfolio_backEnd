package egovframework.eeum.house.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 운영자 고택 목록 조회 요청 — 전 판매자 대상(오프셋 페이징). 필터: status/keyword/prtnrSn/sidoRegionCd/sigunguRegionCd.
 * keyword 는 고택명 + 파트너 상호(둘 중 하나라도 매칭). 지역 필터는 H 컬럼(SIDO/SIGUNGU_REGION_CD) 기준.
 */
@Schema(description = "운영자 고택 목록 조회 요청")
public record OperatorHouseSearchRequest(

		@Schema(description = "페이지(1부터)", example = "1")
		Integer page,

		@Schema(description = "페이지 크기(최대 100)", example = "20")
		Integer pageSize,

		@Schema(description = "상태 필터(DRAFT/REVIEW_REQUESTED/ACTIVE/HIDDEN/SUSPENDED — filter-options SSOT와 동일)")
		@Pattern(regexp = "DRAFT|REVIEW_REQUESTED|ACTIVE|HIDDEN|SUSPENDED", message = "유효하지 않은 상태 필터입니다.")
		String status,

		@Schema(description = "검색어(고택명 또는 파트너 상호)")
		@Size(max = 100, message = "검색어는 100자 이내여야 합니다.")
		String keyword,

		@Schema(description = "특정 파트너로 필터(선택)")
		Long prtnrSn,

			@Schema(description = "시도 지역코드 필터(REGION 2자리, 선택)", example = "47")
			@Pattern(regexp = "\\d{2}", message = "시도 지역코드가 올바르지 않습니다.")
			String sidoRegionCd,

			@Schema(description = "시군구 지역코드 필터(REGION 5자리, 선택)", example = "47170")
			@Pattern(regexp = "\\d{5}", message = "시군구 지역코드가 올바르지 않습니다.")
			String sigunguRegionCd) {

	private static final int DEFAULT_PAGE_SIZE = 20;
	private static final int MAX_PAGE_SIZE = 100;

	@Schema(hidden = true)
	public int pageOrDefault() {
		return (page == null || page < 1) ? 1 : page;
	}

	@Schema(hidden = true)
	public int pageSizeOrDefault() {
		if (pageSize == null || pageSize < 1) {
			return DEFAULT_PAGE_SIZE;
		}
		return Math.min(pageSize, MAX_PAGE_SIZE);
	}

	@Schema(hidden = true)
	public int offset() {
		return (pageOrDefault() - 1) * pageSizeOrDefault();
	}
}
