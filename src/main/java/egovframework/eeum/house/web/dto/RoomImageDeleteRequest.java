package egovframework.eeum.house.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * 객실 이미지 삭제 요청(판매자 본인 소유).
 */
@Schema(description = "객실 이미지 삭제 요청")
public record RoomImageDeleteRequest(

		@Schema(description = "객실일련번호")
		@NotNull(message = "객실일련번호가 필요합니다.")
		Long roomSn,

		@Schema(description = "객실이미지일련번호")
		@NotNull(message = "객실이미지일련번호가 필요합니다.")
		Long roomImgSn) {
}
