package egovframework.eeum.house.web.dto;

import egovframework.eeum.house.vo.RoomBeddingVO;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 객실 침구 응답 항목.
 */
@Schema(description = "객실 침구")
public record RoomBeddingResponse(

		@Schema(description = "침구타입코드(COMM_CD BEDDING_TYPE)") String beddingTypeCd,
		@Schema(description = "개수") Integer bedCnt) {

	public static RoomBeddingResponse from(RoomBeddingVO vo) {
		return new RoomBeddingResponse(vo.getBeddingTypeCd(), vo.getBedCnt());
	}
}
