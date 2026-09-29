package egovframework.eeum.house.web.dto;

import egovframework.eeum.house.vo.HouseImageVO;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 손님 공개 고택 이미지 항목 — 다운로드 URL(백엔드 경유)·대표여부·정렬만 노출.
 *
 * <p>내부 식별자(houseImgSn)·감사컬럼은 노출하지 않는다(공개 계약 최소화).
 */
@Schema(description = "공개 고택 이미지 항목")
public record PublicHouseImageItem(

		@Schema(description = "이미지 다운로드 URL(백엔드 경유)") String imageUrl,
		@Schema(description = "대표이미지 여부(Y/N)") String rprsYn,
		@Schema(description = "정렬순서") Integer sortSeq) {

	public static PublicHouseImageItem from(HouseImageVO vo) {
		return new PublicHouseImageItem(
				"/files/download?atchFileSn=" + vo.getAtchFileSn(),
				vo.getRprsYn(),
				vo.getSortSeq());
	}
}
