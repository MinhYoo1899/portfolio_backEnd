package egovframework.eeum.account.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * 운영자 역할 변경 요청 DTO — STAFF↔SUPERVISOR.
 *
 * <p>SUPERVISOR 부여 시 STAFF 동시 유지, STAFF 로 강등 시 STAFF 잔류(SUPERVISOR 해제).
 * 대상 식별은 서버가 OPERATOR 만 허용(비OPERATOR 는 404).
 */
@Schema(description = "운영자 역할 변경 요청")
public record OperatorRoleChangeRequest(

		@Schema(description = "대상 사용자일련번호(문자열)", example = "12")
		@NotBlank(message = "대상 식별자가 필요합니다.")
		@Pattern(regexp = "\\d{1,18}", message = "식별자 형식이 올바르지 않습니다.")
		String userSn,

		@Schema(description = "변경 역할(STAFF=강등 / SUPERVISOR=승격)", example = "SUPERVISOR")
		@NotNull(message = "역할을 선택해 주세요.")
		OperatorRole role) {

	public long userSnAsLong() {
		return Long.parseLong(userSn);
	}
}
