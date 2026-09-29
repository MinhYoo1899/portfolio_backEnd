package egovframework.eeum.account.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 운영자 비밀번호 설정 요청 DTO (비인증) — 설정토큰 + 새 비밀번호.
 *
 * <p>token 으로 대상 운영자를 식별(body 에 userSn 없음 — 토큰만 신뢰). 성공 시 PENDING→ACTIVE, 토큰 소비.
 * 비밀번호는 PasswordPolicy 로 검증한다(@Size 는 1차 방어, 상세 정책은 서버).
 */
@Schema(description = "운영자 비밀번호 설정 요청(비인증 — 설정토큰)")
public record OperatorSetupPasswordRequest(

		@Schema(description = "설정토큰(생성/초기화 시 1회 발급된 값)")
		@NotBlank(message = "설정 토큰이 필요합니다.")
		String token,

		@Schema(description = "새 비밀번호(8~64자, 영문·숫자·특수문자 중 2종 이상)")
		@NotBlank(message = "비밀번호를 입력해 주세요.")
		@Size(min = 8, max = 64, message = "비밀번호는 8~64자여야 합니다.")
		String newPassword) {
}
