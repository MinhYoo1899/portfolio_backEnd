package egovframework.eeum.content.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * 즐길거리 목록 검색(전부 선택). category/type/region/keyword 필터 + 페이지네이션(기본 8/페이지).
 */
@Schema(description = "즐길거리 목록 검색")
public record ContentRegionSearchRequest(

		@Schema(description = "카테고리 필터(null=전체)") @Size(max = 50) String category,

		@Schema(description = "유형 필터(null=전체)") @Size(max = 50) String type,

		@Schema(description = "지역(시도) 필터(null=전체)") @Size(max = 50) String region,

		@Schema(description = "제목 검색어(부분일치, null=전체)") @Size(max = 200) String keyword,

		@Schema(description = "페이지(1-base, 기본 1)") @Min(1) Integer page,

		@Schema(description = "페이지 크기(기본 8, 최대 50)") @Min(1) @Max(50) Integer size) {

	/** 페이지(기본 1). */
	public int pageOrDefault() {
		return page == null ? 1 : page;
	}

	/** 페이지 크기(기본 8). */
	public int sizeOrDefault() {
		return size == null ? 8 : size;
	}

	/** LIMIT/OFFSET 계산용 offset. */
	public int offset() {
		return (pageOrDefault() - 1) * sizeOrDefault();
	}
}
