package egovframework.eeum.inquiry.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 문의 답변 등록 요청(운영자).
 */
@Schema(description = "문의 답변 등록 요청")
public record InqrAnswerInsertRequest(

		@Schema(description = "문의일련번호")
		@NotNull(message = "inqrSn은 필수입니다.")
		Long inqrSn,

		@Schema(description = "답변내용")
		@NotBlank(message = "답변 내용을 입력해 주세요.")
		@Size(max = 5000)
		String ansCn) {
}
