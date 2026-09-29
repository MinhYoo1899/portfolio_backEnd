package egovframework.eeum.account.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 이메일 인증코드 검증 요청 DTO — 6자리 숫자 코드.
 */
@Schema(description = "이메일 인증코드 검증 요청")
public record EmailVerifyRequest(

		@Schema(description = "6자리 숫자 인증코드", example = "012345")
		@NotBlank(message = "인증 코드를 입력해 주세요.")
		@Pattern(regexp = "\\d{6}", message = "인증 코드는 6자리 숫자입니다.")
		String code) {
}
