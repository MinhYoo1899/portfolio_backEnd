package egovframework.eeum.account.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 비밀번호 변경 요청 DTO (로그인 상태).
 *
 * <p>새 비밀번호의 상세 정책(8~64자, 2종 이상, BCrypt 72바이트)은 {@code PasswordPolicy}가 검증한다.
 * DTO 의 {@code @Size(max)}는 거대 입력 방어용 1차 한도일 뿐 정책의 단일 출처는 아니다.
 */
@Schema(description = "비밀번호 변경 요청")
public record PasswordChangeRequest(

		@Schema(description = "현재 비밀번호")
		@NotBlank(message = "현재 비밀번호를 입력해 주세요.")
		@Size(max = 64, message = "비밀번호는 64자 이내여야 합니다.")
		String currentPassword,

		@Schema(description = "새 비밀번호 (8~64자, 영문·숫자·특수문자 중 2종 이상)")
		@NotBlank(message = "새 비밀번호를 입력해 주세요.")
		@Size(max = 64, message = "비밀번호는 64자 이내여야 합니다.")
		String newPassword) {
}
