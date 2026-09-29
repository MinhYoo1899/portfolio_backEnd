package egovframework.eeum.house.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * 고택 대표 이미지 지정 요청(판매자 본인 소유).
 */
@Schema(description = "고택 대표 이미지 지정 요청")
public record HouseImageRepresentativeRequest(

		@Schema(description = "고택일련번호")
		@NotNull(message = "고택일련번호가 필요합니다.")
		Long houseSn,

		@Schema(description = "대표로 지정할 고택이미지일련번호")
		@NotNull(message = "고택이미지일련번호가 필요합니다.")
		Long houseImgSn) {
}
