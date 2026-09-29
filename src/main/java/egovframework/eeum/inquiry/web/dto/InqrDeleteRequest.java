package egovframework.eeum.inquiry.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * 문의글 삭제 요청(작성자 본인).
 */
@Schema(description = "문의글 삭제 요청")
public record InqrDeleteRequest(
		@Schema(description = "문의일련번호")
		@NotNull(message = "inqrSn은 필수입니다.")
		Long inqrSn) {
}
