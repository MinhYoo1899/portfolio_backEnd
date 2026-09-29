package egovframework.eeum.account.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 선택약관 동의 변경 요청 — 시행 중 선택약관(ESNTL_YN='N')의 동의/철회.
 *
 * <p>필수약관은 철회 불가(서비스가 거부). append-only — 변경은 새 동의이력 행으로 기록(기존 행 수정 안 함).
 */
@Schema(description = "선택약관 동의 변경 요청")
public record ConsentChangeRequest(

		@Schema(description = "대상 약관일련번호(시행 중 선택약관)", example = "5")
		@NotBlank(message = "대상 약관 식별자가 필요합니다.")
		@Pattern(regexp = "\\d{1,18}", message = "약관 식별자 형식이 올바르지 않습니다.")
		String trmsSn,

		@Schema(description = "동의여부(Y=동의 / N=철회)", example = "N")
		@NotBlank(message = "동의여부를 선택해 주세요.")
		@Pattern(regexp = "Y|N", message = "동의여부 값이 올바르지 않습니다.")
		String agreYn) {

	public long trmsSnAsLong() {
		return Long.parseLong(trmsSn);
	}
}
