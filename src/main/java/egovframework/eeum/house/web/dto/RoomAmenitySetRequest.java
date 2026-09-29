package egovframework.eeum.house.web.dto;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * 객실 편의시설 설정 요청 — 선택 집합 전체 교체(replace).
 *
 * <p>{@code amenitySns} 에 담긴 편의시설만 활성으로 남기고 나머지는 해제. 빈 배열이면 전부 해제.
 * 소유권은 부모 객실/고택의 PRTNR_SN 으로 판정(요청에 prtnrSn 없음).
 */
@Schema(description = "객실 편의시설 설정 요청(전체 교체)")
public record RoomAmenitySetRequest(

		@Schema(description = "객실일련번호")
		@NotNull(message = "객실일련번호가 필요합니다.")
		Long roomSn,

		@Schema(description = "선택한 편의시설 일련번호 목록(빈 배열=전부 해제)")
		@NotNull(message = "편의시설 목록이 필요합니다.")
		@Size(max = 200, message = "편의시설은 최대 200개까지 선택할 수 있습니다.")
		List<@NotNull(message = "편의시설 일련번호가 올바르지 않습니다.")
				@Positive(message = "편의시설 일련번호가 올바르지 않습니다.") Long> amenitySns,

		@Schema(description = "직접입력 커스텀 편의시설명 목록(즉시 활성 등록 후 매핑). 빈 배열 허용, 각 100자 이내")
		@Size(max = 30, message = "직접입력 편의시설은 최대 30개까지 가능합니다.")
		List<@Size(max = 100, message = "직접입력 편의시설명은 100자 이내여야 합니다.") String> customAmenityNms) {

	public RoomAmenitySetRequest {
		// amenitySns 누락(null)은 @NotNull 이 400으로 잡도록 null 유지(전체해제 오인 방지). null 원소도 List.copyOf(NPE) 대신 null 내성 복사로 @NotNull 검증에 위임
		amenitySns = amenitySns == null ? null : Collections.unmodifiableList(new ArrayList<>(amenitySns));
		customAmenityNms = normalizeNames(customAmenityNms);
	}

	/** 공백 trim → 빈값 제거 → 대소문자 무시 중복 제거(입력순 유지). 길이 초과는 절단하지 않고 @Size 검증으로 거부. */
	private static List<String> normalizeNames(List<String> names) {
		if (names == null || names.isEmpty()) {
			return List.of();
		}
		LinkedHashMap<String, String> uniq = new LinkedHashMap<>();
		for (String raw : names) {
			if (raw == null) {
				continue;
			}
			String nm = raw.strip();
			if (!nm.isEmpty()) {
				uniq.putIfAbsent(nm.toLowerCase(Locale.ROOT), nm);
			}
		}
		return List.copyOf(uniq.values());
	}
}
