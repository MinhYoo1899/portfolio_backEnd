package egovframework.eeum.house.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * 고택 검수 전이 요청(운영자) — review-request(HOUSE_PROXY_MNG)·review-approve(HOUSE_REVIEW) 공용.
 */
@Schema(description = "고택 검수 전이 요청(검수요청/승인)")
public record HouseReviewRequest(

		@Schema(description = "고택일련번호")
		@NotNull(message = "고택일련번호가 필요합니다.")
		Long houseSn) {
}
