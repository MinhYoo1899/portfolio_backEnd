package egovframework.eeum.house.web.dto;

import egovframework.eeum.house.vo.HouseImageVO;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 고택 이미지 응답.
 */
@Schema(description = "고택 이미지")
public record HouseImageResponse(

		@Schema(description = "고택이미지일련번호") Long houseImgSn,
		@Schema(description = "첨부파일일련번호") Long atchFileSn,
		@Schema(description = "공개 이미지 URL") String urlAddr,
		@Schema(description = "원본파일명") String orgnlFileNm,
		@Schema(description = "정렬순서") Integer sortSeq,
		@Schema(description = "대표여부(Y/N)") String rprsYn) {

	public static HouseImageResponse from(HouseImageVO vo) {
		return new HouseImageResponse(
				vo.getHouseImgSn(),
				vo.getAtchFileSn(),
				vo.getUrlAddr(),
				vo.getOrgnlFileNm(),
				vo.getSortSeq(),
				vo.getRprsYn());
	}
}
