package egovframework.eeum.house.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * 고택 삭제 요청(판매자 본인, soft delete).
 */
@Schema(description = "고택 삭제 요청")
public record HouseDeleteRequest(

		@Schema(description = "고택일련번호")
		@NotNull(message = "고택일련번호가 필요합니다.")
		Long houseSn) {
}
