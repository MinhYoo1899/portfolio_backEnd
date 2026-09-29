package egovframework.eeum.inquiry.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * 문의 답변 삭제 요청(운영자).
 */
@Schema(description = "문의 답변 삭제 요청")
public record InqrAnswerDeleteRequest(
		@Schema(description = "답변일련번호")
		@NotNull(message = "ansSn은 필수입니다.")
		Long ansSn) {
}
