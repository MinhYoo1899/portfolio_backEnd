package egovframework.eeum.house.web.dto;

import egovframework.eeum.house.vo.RoomOptionVO;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 객실 추가옵션 응답 항목.
 */
@Schema(description = "객실 추가옵션")
public record RoomOptionResponse(

		@Schema(description = "객실추가옵션일련번호") Long roomOptionSn,
		@Schema(description = "옵션명") String optionNm,
		@Schema(description = "추가금액(원) — 과금단위=예약당 1회") Integer addAmt) {

	public static RoomOptionResponse from(RoomOptionVO vo) {
		return new RoomOptionResponse(vo.getRoomOptionSn(), vo.getOptionNm(), vo.getAddAmt());
	}
}
