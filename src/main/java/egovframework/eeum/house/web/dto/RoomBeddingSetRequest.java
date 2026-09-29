package egovframework.eeum.house.web.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 객실 침구 설정 요청 — 교체(replace) 방식. 기존 활성 침구를 비우고 {@code items} 로 재구성.
 *
 * <p>소유권은 부모 객실/고택의 PRTNR_SN 으로 판정(요청에 prtnrSn 없음). {@code beddingTypeCd} 는 COMM_CD BEDDING_TYPE 값만 허용.
 */
@Schema(description = "객실 침구 설정 요청(교체)")
public record RoomBeddingSetRequest(

		@Schema(description = "객실일련번호")
		@NotNull(message = "객실일련번호가 필요합니다.")
		Long roomSn,

		@Schema(description = "침구 목록(빈 목록이면 전부 비움)")
		@NotNull(message = "침구 목록이 필요합니다.")
		@Size(max = 50, message = "침구 항목이 너무 많습니다.")
		@Valid
		List<Item> items) {

	/** 침구 항목 — 타입코드 + 개수. */
	@Schema(description = "침구 항목")
	public record Item(

			@Schema(description = "침구타입코드(COMM_CD BEDDING_TYPE)", example = "QUEEN_BED")
			@NotBlank(message = "침구타입코드가 필요합니다.")
			@Size(max = 20, message = "침구타입코드 형식이 올바르지 않습니다.")
			String beddingTypeCd,

			@Schema(description = "개수(1 이상)", example = "1")
			@NotNull(message = "개수가 필요합니다.")
			@Min(value = 1, message = "개수는 1 이상이어야 합니다.")
			@Max(value = 1000, message = "개수가 너무 큽니다.")
			Integer bedCnt) {
	}
}
