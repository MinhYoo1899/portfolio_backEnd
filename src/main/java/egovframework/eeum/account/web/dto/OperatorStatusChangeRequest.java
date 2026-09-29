package egovframework.eeum.account.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 운영자 상태 변경 요청 DTO — 활성(ACTIVE)/비활성·잠금(SUSPENDED).
 *
 * <p>비활성화는 역할 삭제가 아니라 ACNT_STTS 로 로그인 차단. 자기 자신·SUPER 보호·마지막 SUPERVISOR 불변식 적용.
 */
@Schema(description = "운영자 상태 변경 요청")
public record OperatorStatusChangeRequest(

		@Schema(description = "대상 사용자일련번호(문자열)", example = "12")
		@NotBlank(message = "대상 식별자가 필요합니다.")
		@Pattern(regexp = "\\d{1,18}", message = "식별자 형식이 올바르지 않습니다.")
		String userSn,

		@Schema(description = "변경 상태(ACTIVE=활성 / SUSPENDED=비활성·잠금)", example = "SUSPENDED")
		@NotBlank(message = "상태를 선택해 주세요.")
		@Pattern(regexp = "ACTIVE|SUSPENDED", message = "상태 값이 올바르지 않습니다.")
		String acntStts) {

	public long userSnAsLong() {
		return Long.parseLong(userSn);
	}
}
