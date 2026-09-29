package egovframework.eeum.house.web.dto;

import egovframework.eeum.house.vo.AmenityVO;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 편의시설 응답(목록 항목 — 선택용/관리용 공용).
 *
 * <p>승인 게이트 없음(운영자 중심). {@code customYn} 으로 직접입력 여부만 구분.
 */
@Schema(description = "편의시설")
public record AmenityResponse(

		@Schema(description = "편의시설일련번호") Long amenitySn,
		@Schema(description = "편의시설분류코드(공통코드 AMENITY_CTGRY)") String amenityCtgryCd,
		@Schema(description = "적용범위(HOUSE/ROOM/BOTH)") String applyScopeCd,
		@Schema(description = "편의시설명") String amenityNm,
		@Schema(description = "안정 문자열 키(커스텀은 null)") String amenityKey,
		@Schema(description = "아이콘키") String iconKey,
		@Schema(description = "정렬순서") Integer sortSeq,
		@Schema(description = "사용여부(Y/N)") String useYn,
		@Schema(description = "커스텀 여부(Y/N)") String customYn) {

	public static AmenityResponse from(AmenityVO vo) {
		return new AmenityResponse(
				vo.getAmenitySn(),
				vo.getAmenityCtgryCd(),
				vo.getApplyScopeCd(),
				vo.getAmenityNm(),
				vo.getAmenityKey(),
				vo.getIconKey(),
				vo.getSortSeq(),
				vo.getUseYn(),
				vo.getCustomYn());
	}
}
