package egovframework.eeum.house.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * 고택 임시조치 해제 요청(운영자급 HOUSE_MODER) — SUSPENDED→HIDDEN(판매자가 재게시).
 */
@Schema(description = "고택 임시조치 해제 요청")
public record HouseReleaseRequest(

		@Schema(description = "고택일련번호")
		@NotNull(message = "고택일련번호가 필요합니다.")
		Long houseSn) {
}
