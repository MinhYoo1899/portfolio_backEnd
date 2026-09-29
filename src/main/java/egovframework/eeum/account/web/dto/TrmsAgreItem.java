package egovframework.eeum.account.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 약관 동의 항목 (가입 요청에 포함).
 *
 * @param trmsSn 약관일련번호 (ID 문자열 규약)
 * @param agreYn 동의여부 (Y/N — 선택약관은 N 가능, 필수약관은 Y 필수)
 */
@Schema(description = "약관 동의 항목")
public record TrmsAgreItem(

		@Schema(description = "약관일련번호", example = "1")
		@NotBlank(message = "약관일련번호는 필수입니다.")
		@Pattern(regexp = "\\d+", message = "약관일련번호 형식이 올바르지 않습니다.")
		String trmsSn,

		@Schema(description = "동의여부(Y/N)", example = "Y")
		@NotBlank(message = "동의여부는 필수입니다.")
		@Pattern(regexp = "Y|N", message = "동의여부는 Y 또는 N만 허용됩니다.")
		String agreYn) {

	public long trmsSnAsLong() {
		return Long.parseLong(trmsSn);
	}

	public boolean isAgreed() {
		return "Y".equals(agreYn);
	}
}
