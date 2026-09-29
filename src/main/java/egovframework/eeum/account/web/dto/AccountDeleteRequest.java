package egovframework.eeum.account.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * 회원 탈퇴 요청 DTO (대상 = 토큰의 본인. 본인확인용 비밀번호 필수).
 */
@Schema(description = "회원 탈퇴 요청")
public record AccountDeleteRequest(

		@Schema(description = "본인확인 비밀번호")
		@NotBlank(message = "비밀번호를 입력해 주세요.")
		String pswd) {
}
