package egovframework.eeum.house.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 고택 목록 조회 요청(판매자 본인 목록) — 오프셋 페이징.
 *
 * <p>정렬 최신순(HOUSE_SN DESC) 고정. 필터: status(상태)/keyword(고택명).
 */
@Schema(description = "고택 목록 조회 요청")
public record HouseSearchRequest(

		@Schema(description = "페이지(1부터)", example = "1")
		Integer page,

		@Schema(description = "페이지 크기(최대 100)", example = "20")
		Integer pageSize,

		@Schema(description = "상태 필터(DRAFT/REVIEW_REQUESTED/ACTIVE/HIDDEN/SUSPENDED)")
		@Pattern(regexp = "DRAFT|REVIEW_REQUESTED|ACTIVE|HIDDEN|SUSPENDED", message = "유효하지 않은 상태 필터입니다.")
		String status,

		@Schema(description = "고택명 검색어")
		@Size(max = 100, message = "검색어는 100자 이내여야 합니다.")
		String keyword) {

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
