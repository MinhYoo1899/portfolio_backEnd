package egovframework.eeum.house.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * 편의시설 사용여부(노출) 변경 요청(운영자급 AMENITY_MNG) — 비활성(N) 시 신규 선택 목록에서 제외.
 */
@Schema(description = "편의시설 사용여부 변경 요청")
public record AmenityActiveChangeRequest(

		@Schema(description = "편의시설일련번호")
		@NotNull(message = "편의시설일련번호가 필요합니다.")
		Long amenitySn,

		@Schema(description = "사용여부(Y=노출/N=중지)", example = "N")
		@NotNull(message = "사용여부가 필요합니다.")
		@Pattern(regexp = "Y|N", message = "사용여부는 Y 또는 N만 허용됩니다.")
		String useYn) {
}
