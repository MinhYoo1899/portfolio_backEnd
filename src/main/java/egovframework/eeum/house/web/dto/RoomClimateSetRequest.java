package egovframework.eeum.house.web.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 객실 냉난방시설 설정 요청 — 교체(replace) 방식. 기존 활성 냉난방시설을 비우고 {@code coolingCds}/{@code heatingCds} 로 재구성.
 *
 * <p>냉방·난방 각각 다중선택. {@code coolingCds} 는 COMM_CD COOLING_TYPE, {@code heatingCds} 는 COMM_CD HEATING_TYPE 값만 허용.
 * 둘 다 빈 배열이면 전부 해제. 소유권은 부모 객실/고택의 PRTNR_SN 으로 판정(요청에 prtnrSn 없음).
 */
@Schema(description = "객실 냉난방시설 설정 요청(교체)")
public record RoomClimateSetRequest(

		@Schema(description = "객실일련번호")
		@NotNull(message = "객실일련번호가 필요합니다.")
		Long roomSn,

		@Schema(description = "냉방 코드 목록(COMM_CD COOLING_TYPE, 빈 목록이면 냉방 전부 비움)", example = "[\"AIRCON\",\"FAN\"]")
		@NotNull(message = "냉방 코드 목록이 필요합니다.")
		@Size(max = 50, message = "냉방 항목이 너무 많습니다.")
		List<String> coolingCds,

		@Schema(description = "난방 코드 목록(COMM_CD HEATING_TYPE, 빈 목록이면 난방 전부 비움)", example = "[\"ONDOL\",\"BOILER\"]")
		@NotNull(message = "난방 코드 목록이 필요합니다.")
		@Size(max = 50, message = "난방 항목이 너무 많습니다.")
		List<String> heatingCds) {

	public RoomClimateSetRequest {
		coolingCds = coolingCds == null ? List.of() : List.copyOf(coolingCds);
		heatingCds = heatingCds == null ? List.of() : List.copyOf(heatingCds);
	}
}
