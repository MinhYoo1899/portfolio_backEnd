package egovframework.eeum.house.web.dto;

import egovframework.eeum.house.vo.RoomImageVO;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 객실 이미지 응답.
 */
@Schema(description = "객실 이미지")
public record RoomImageResponse(

		@Schema(description = "객실이미지일련번호") Long roomImgSn,
		@Schema(description = "첨부파일일련번호") Long atchFileSn,
		@Schema(description = "공개 이미지 URL") String urlAddr,
		@Schema(description = "원본파일명") String orgnlFileNm,
		@Schema(description = "정렬순서") Integer sortSeq,
		@Schema(description = "대표여부(Y/N)") String rprsYn) {

	public static RoomImageResponse from(RoomImageVO vo) {
		return new RoomImageResponse(
				vo.getRoomImgSn(),
				vo.getAtchFileSn(),
				vo.getUrlAddr(),
				vo.getOrgnlFileNm(),
				vo.getSortSeq(),
				vo.getRprsYn());
	}
}
