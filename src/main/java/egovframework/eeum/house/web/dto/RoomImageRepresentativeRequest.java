package egovframework.eeum.house.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * 객실 대표 이미지 지정 요청(판매자 본인 소유).
 */
@Schema(description = "객실 대표 이미지 지정 요청")
public record RoomImageRepresentativeRequest(

		@Schema(description = "객실일련번호")
		@NotNull(message = "객실일련번호가 필요합니다.")
		Long roomSn,

		@Schema(description = "대표로 지정할 객실이미지일련번호")
		@NotNull(message = "객실이미지일련번호가 필요합니다.")
		Long roomImgSn) {
}
