package egovframework.eeum.account.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 일반회원 상태변경 요청 — 정지/해제/탈퇴처리. 사유 필수(감사 추적성).
 *
 * <p>허용 전이는 서비스 상태머신이 강제(ACTIVE↔SUSPENDED, ACTIVE/SUSPENDED→WITHDRAWN). 대상은 MEMBER 고정.
 */
@Schema(description = "일반회원 상태변경 요청")
public record MemberStatusChangeRequest(

		@Schema(description = "대상 사용자일련번호(문자열)", example = "42")
		@NotBlank(message = "대상 식별자가 필요합니다.")
		@Pattern(regexp = "\\d{1,18}", message = "식별자 형식이 올바르지 않습니다.")
		String userSn,

		@Schema(description = "변경 상태(ACTIVE=해제 / SUSPENDED=정지 / WITHDRAWN=탈퇴처리)", example = "SUSPENDED")
		@NotBlank(message = "상태를 선택해 주세요.")
		@Pattern(regexp = "ACTIVE|SUSPENDED|WITHDRAWN", message = "상태 값이 올바르지 않습니다.")
		String acntStts,

		@Schema(description = "조치 사유(감사 기록)", example = "약관 위반 신고 누적")
		@NotBlank(message = "조치 사유를 입력해 주세요.")
		@Size(max = 500, message = "사유는 500자 이내여야 합니다.")
		String rsn) {

	public long userSnAsLong() {
		return Long.parseLong(userSn);
	}
}
