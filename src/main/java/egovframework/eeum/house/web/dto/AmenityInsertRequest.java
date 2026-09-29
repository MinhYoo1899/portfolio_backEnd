package egovframework.eeum.house.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 편의시설 등록 요청(운영자급 AMENITY_MNG).
 */
@Schema(description = "편의시설 등록 요청")
public record AmenityInsertRequest(

		@Schema(description = "편의시설분류코드(공통코드 AMENITY_CTGRY)", example = "ROOM_FEATURE")
		@NotBlank(message = "분류를 선택해 주세요.")
		@Size(max = 30, message = "분류코드는 30자 이내여야 합니다.")
		String amenityCtgryCd,

		@Schema(description = "적용범위(HOUSE/ROOM/BOTH)", example = "ROOM")
		@NotBlank(message = "적용범위를 선택해 주세요.")
		@Pattern(regexp = "HOUSE|ROOM|BOTH", message = "적용범위는 HOUSE/ROOM/BOTH 중 하나여야 합니다.")
		String applyScopeCd,

		@Schema(description = "편의시설명", example = "무료 와이파이")
		@NotBlank(message = "편의시설명을 입력해 주세요.")
		@Size(max = 100, message = "편의시설명은 100자 이내여야 합니다.")
		String amenityNm,

		@Schema(description = "아이콘키(선택)", example = "wifi")
		@Size(max = 100, message = "아이콘키는 100자 이내여야 합니다.")
		String iconKey,

		@Schema(description = "정렬순서(선택, 기본 0)", example = "1")
		@Min(value = 0, message = "정렬순서는 0 이상이어야 합니다.")
		@Max(value = 100000, message = "정렬순서가 너무 큽니다.")
		Integer sortSeq) {

	public int sortSeqOrDefault() {
		return sortSeq == null ? 0 : sortSeq;
	}
}
