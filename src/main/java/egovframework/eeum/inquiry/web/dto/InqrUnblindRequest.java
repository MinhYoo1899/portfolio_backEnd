package egovframework.eeum.inquiry.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * 문의글 블라인드 해제 요청 — 운영자(INQR_MODER).
 */
@Schema(description = "문의글 블라인드 해제 요청")
public record InqrUnblindRequest(
		@Schema(description = "문의일련번호")
		@NotNull(message = "inqrSn은 필수입니다.")
		Long inqrSn) {
}
