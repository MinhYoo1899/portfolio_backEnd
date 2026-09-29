package egovframework.eeum.content.web.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * 추천고택 순서 저장 요청(드래그앤드롭) — [{recId, sortOrder}] 배열 전체 반영.
 */
@Schema(description = "추천고택 순서 저장 요청")
public record ContentRecHouseOrderRequest(

		@Schema(description = "순서 항목 목록")
		@NotEmpty(message = "순서 항목이 필요합니다.")
		@Size(max = 500, message = "한 번에 저장할 수 있는 항목 수를 초과했습니다.")
		List<@Valid OrderItem> items) {

	/** 순서 항목 — 추천고택 식별자 + 표시순서. */
	@Schema(description = "추천고택 순서 항목")
	public record OrderItem(
			@Schema(description = "추천고택 식별자") @NotNull Long recId,
			@Schema(description = "표시순서(0 이상)") @NotNull @PositiveOrZero Integer sortOrder) {
	}
}
