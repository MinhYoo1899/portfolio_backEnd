package egovframework.eeum.house.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 고택 검수 반려 요청(운영자 HOUSE_REVIEW) — REVIEW_REQUESTED → DRAFT. 반려사유 필수(감사/조회 저장).
 */
@Schema(description = "고택 검수 반려 요청")
public record HouseReviewRejectRequest(

		@Schema(description = "고택일련번호")
		@NotNull(message = "고택일련번호가 필요합니다.")
		Long houseSn,

		@Schema(description = "반려사유(필수, 1~500자)")
		@NotBlank(message = "반려사유가 필요합니다.")
		@Size(max = 500, message = "반려사유는 500자 이내여야 합니다.")
		String rejectReason) {
}
