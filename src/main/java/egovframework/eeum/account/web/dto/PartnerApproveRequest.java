package egovframework.eeum.account.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 파트너 승인 요청 DTO (운영자 전용).
 */
@Schema(description = "파트너 승인 요청")
public record PartnerApproveRequest(

		@Schema(description = "파트너일련번호", example = "1")
		@NotBlank(message = "파트너일련번호는 필수입니다.")
		@Pattern(regexp = "\\d{1,18}", message = "파트너일련번호 형식이 올바르지 않습니다.")
		String prtnrSn) {

	public long prtnrSnAsLong() {
		return Long.parseLong(prtnrSn);
	}
}
