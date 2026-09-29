package egovframework.eeum.house.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 고택 수정 요청(판매자 본인) — 콘텐츠 전체 갱신.
 */
@Schema(description = "고택 수정 요청")
public record HouseUpdateRequest(

		@Schema(description = "고택일련번호")
		@NotNull(message = "고택일련번호가 필요합니다.")
		Long houseSn,

		@Schema(description = "고택명", example = "고즈넉 한옥")
		@NotBlank(message = "고택명을 입력해 주세요.")
		@Size(max = 200, message = "고택명은 200자 이내여야 합니다.")
		String houseNm,

		@Schema(description = "우편번호(5자리, 선택)", example = "12345")
		@Pattern(regexp = "\\d{5}", message = "우편번호는 숫자 5자리여야 합니다.")
		String zip,

		@Schema(description = "주소(선택)")
		@Size(max = 320, message = "주소는 320자 이내여야 합니다.")
		String addr,

		@Schema(description = "상세주소(선택)")
		@Size(max = 200, message = "상세주소는 200자 이내여야 합니다.")
		String daddr,

		@Schema(description = "고택설명/소개(선택)")
		@Size(max = 5000, message = "고택설명은 5000자 이내여야 합니다.")
		String houseDc,

			@Schema(description = "시도 지역코드(REGION 2자리, 선택)", example = "47")
			@Pattern(regexp = "\\d{2}", message = "시도 지역코드가 올바르지 않습니다.")
			String sidoRegionCd,

			@Schema(description = "시군구 지역코드(REGION 5자리, 선택 — 지정 시 시도 소속 검증)", example = "47170")
			@Pattern(regexp = "\\d{5}", message = "시군구 지역코드가 올바르지 않습니다.")
			String sigunguRegionCd) {
}
