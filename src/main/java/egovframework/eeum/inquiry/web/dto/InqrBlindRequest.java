package egovframework.eeum.inquiry.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 문의글 블라인드(부적절글 비노출) 요청 — 운영자(INQR_MODER).
 */
@Schema(description = "문의글 블라인드 요청")
public record InqrBlindRequest(

		@Schema(description = "문의일련번호")
		@NotNull(message = "inqrSn은 필수입니다.")
		Long inqrSn,

		@Schema(description = "블라인드 사유")
		@NotBlank(message = "블라인드 사유를 입력해 주세요.")
		@Size(max = 500)
		String blndRsn) {
}
