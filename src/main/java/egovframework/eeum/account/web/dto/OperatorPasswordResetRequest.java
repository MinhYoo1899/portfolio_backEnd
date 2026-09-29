package egovframework.eeum.account.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 운영자 비밀번호 초기화 요청 DTO — 대상 운영자에 설정토큰 재발급(1회 노출).
 *
 * <p>관리자가 직접 비밀번호를 지정하지 않는다 — 토큰을 통해 대상 본인이 setup-password 로 새 비밀번호를 설정한다.
 */
@Schema(description = "운영자 비밀번호 초기화(설정토큰 재발급) 요청")
public record OperatorPasswordResetRequest(

		@Schema(description = "대상 사용자일련번호(문자열)", example = "12")
		@NotBlank(message = "대상 식별자가 필요합니다.")
		@Pattern(regexp = "\\d{1,18}", message = "식별자 형식이 올바르지 않습니다.")
		String userSn) {

	public long userSnAsLong() {
		return Long.parseLong(userSn);
	}
}
