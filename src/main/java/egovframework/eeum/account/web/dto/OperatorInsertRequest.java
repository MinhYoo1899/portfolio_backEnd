package egovframework.eeum.account.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 운영자(직원) 생성 요청 DTO — 계정(PENDING, 비번 미설정) + 운영자 상세 + 역할 + 설정토큰 발급.
 *
 * <p>역할은 {@link OperatorRole}(STAFF/SUPERVISOR) enum 으로만 — raw ROLE_CD/AUTH_CD 주입 차단.
 */
@Schema(description = "운영자 생성 요청")
public record OperatorInsertRequest(

		@Schema(description = "이메일주소(로그인 ID)", example = "staff@eeum.kr")
		@NotBlank(message = "이메일을 입력해 주세요.")
		@Email(message = "이메일 형식이 아닙니다.")
		@Size(max = 150, message = "이메일은 150자 이내여야 합니다.")
		String emlAddr,

		@Schema(description = "운영자명(실명)", example = "김운영")
		@NotBlank(message = "이름을 입력해 주세요.")
		@Size(max = 50, message = "이름은 50자 이내여야 합니다.")
		String userNm,

		@Schema(description = "부여 역할(STAFF/SUPERVISOR). SUPERVISOR 면 STAFF 동시 부여.", example = "STAFF")
		@NotNull(message = "역할을 선택해 주세요.")
		OperatorRole role,

		@Schema(description = "부서명", example = "운영팀")
		@Size(max = 100, message = "부서명은 100자 이내여야 합니다.")
		String deptNm,

		@Schema(description = "직위명", example = "사원")
		@Size(max = 100, message = "직위명은 100자 이내여야 합니다.")
		String jbpsNm,

			@Schema(description = "휴대전화번호(필수 — 화면정의서 common/permission). 하이픈 허용, 서버가 정규화·암호화·해시.",
					example = "010-1234-5678")
			@NotBlank(message = "휴대전화번호를 입력해 주세요.")
			@Size(max = 20, message = "휴대전화번호가 너무 깁니다.")
			String phone) {
}
