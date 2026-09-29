package egovframework.eeum.house.web.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * 객실 추가옵션 설정 요청 — 교체(replace) 방식. 기존 활성 옵션을 비우고 {@code items} 로 재구성.
 *
 * <p>옵션명은 자유텍스트(trim 후 중복 금지), 추가금액은 0 이상. 정렬순서는 목록 인덱스로 부여.
 * 소유권은 부모 객실/고택의 PRTNR_SN 으로 판정(요청에 prtnrSn 없음).
 */
@Schema(description = "객실 추가옵션 설정 요청(교체)")
public record RoomOptionSetRequest(

		@Schema(description = "객실일련번호")
		@NotNull(message = "객실일련번호가 필요합니다.")
		Long roomSn,

		@Schema(description = "옵션 목록(빈 목록이면 전부 비움)")
		@NotNull(message = "옵션 목록이 필요합니다.")
		@Size(max = 50, message = "옵션 항목이 너무 많습니다.")
		@Valid
		List<Item> items) {

	public RoomOptionSetRequest {
		items = items == null ? List.of() : List.copyOf(items);
	}

	/** 추가옵션 항목 — 옵션명 + 추가금액. */
	@Schema(description = "추가옵션 항목")
	public record Item(

			@Schema(description = "옵션명(자유텍스트)", example = "조식 추가")
			@NotBlank(message = "옵션명이 필요합니다.")
			@Size(max = 100, message = "옵션명이 너무 깁니다.")
			String optionNm,

			@Schema(description = "추가금액(원, 0 이상)", example = "15000")
			@NotNull(message = "추가금액이 필요합니다.")
			@PositiveOrZero(message = "추가금액은 0 이상이어야 합니다.")
			@Max(value = 100_000_000, message = "추가금액이 너무 큽니다.")
			Integer addAmt) {
	}
}
