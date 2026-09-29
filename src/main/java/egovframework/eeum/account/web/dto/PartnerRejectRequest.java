package egovframework.eeum.account.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 파트너 반려 요청 DTO (운영자 전용 — 반려사유 필수, DB CHECK 와 일치).
 */
@Schema(description = "파트너 반려 요청")
public record PartnerRejectRequest(

		@Schema(description = "파트너일련번호", example = "1")
		@NotBlank(message = "파트너일련번호는 필수입니다.")
		@Pattern(regexp = "\\d{1,18}", message = "파트너일련번호 형식이 올바르지 않습니다.")
		String prtnrSn,

		@Schema(description = "반려사유")
		@NotBlank(message = "반려사유를 입력해 주세요.")
		@Size(max = 500, message = "반려사유는 500자 이내여야 합니다.")
		String rjctRsn) {

	public long prtnrSnAsLong() {
		return Long.parseLong(prtnrSn);
	}
}
