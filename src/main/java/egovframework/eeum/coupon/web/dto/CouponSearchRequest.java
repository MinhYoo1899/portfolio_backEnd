package egovframework.eeum.coupon.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * 쿠폰 목록 검색(전부 선택). status(산출값 기준 in-service 필터)·discountType(SQL)·keyword(SQL) + 페이지네이션(기본 10).
 */
@Schema(description = "쿠폰 목록 검색")
public record CouponSearchRequest(

		@Schema(description = "상태 필터(UPCOMING/ACTIVE/ENDED, null=전체)") @Size(max = 20) String status,

		@Schema(description = "할인유형 필터(RATE/FLAT, null=전체)") @Size(max = 20) String discountTypeCd,

		@Schema(description = "제목 검색어(부분일치, null=전체)") @Size(max = 200) String keyword,

		@Schema(description = "페이지(1-base, 기본 1)") @Min(1) Integer page,

		@Schema(description = "페이지 크기(기본 10, 최대 50)") @Min(1) @Max(50) Integer size) {

	public int pageOrDefault() {
		return page == null ? 1 : page;
	}

	public int sizeOrDefault() {
		return size == null ? 10 : size;
	}

	public int offset() {
		return (pageOrDefault() - 1) * sizeOrDefault();
	}
}
