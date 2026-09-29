package egovframework.eeum.account.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 본인 정보 수정 요청 DTO (대상 = 토큰의 본인 — 식별자 불필요).
 */
@Schema(description = "본인 정보 수정 요청")
public record AccountUpdateRequest(

		@Schema(description = "사용자명(실명)", example = "홍길동")
		@NotBlank(message = "이름을 입력해 주세요.")
		@Size(max = 50, message = "이름은 50자 이내여야 합니다.")
		String userNm,

		@Schema(description = "별명(닉네임)", example = "한옥지기")
		@Size(max = 30, message = "별명은 30자 이내여야 합니다.")
		String ncnm) {
}
