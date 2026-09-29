package egovframework.eeum.house.web.dto;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * 고택 편의시설 설정 요청(판매자 본인) — 선택 집합 전체 교체(replace).
 *
 * <p>{@code amenitySns} 에 담긴 편의시설만 활성으로 남기고 나머지는 해제. 빈 배열이면 전부 해제.
 */
@Schema(description = "고택 편의시설 설정 요청(전체 교체)")
public record HouseAmenitySetRequest(

		@Schema(description = "고택일련번호")
		@NotNull(message = "고택일련번호가 필요합니다.")
		Long houseSn,

		@Schema(description = "선택한 편의시설 일련번호 목록(빈 배열=전부 해제)")
		@NotNull(message = "편의시설 목록이 필요합니다.")
		@Size(max = 200, message = "편의시설은 최대 200개까지 선택할 수 있습니다.")
		List<@NotNull(message = "편의시설 일련번호가 올바르지 않습니다.")
				@Positive(message = "편의시설 일련번호가 올바르지 않습니다.") Long> amenitySns) {

	public HouseAmenitySetRequest {
		// amenitySns 누락(null)은 @NotNull 이 400으로 잡도록 null 유지(전체해제 오인 방지). null 원소는 List.copyOf(NPE) 대신 null 내성 복사로 @NotNull 검증에 위임
		amenitySns = amenitySns == null ? null : Collections.unmodifiableList(new ArrayList<>(amenitySns));
	}
}
