package egovframework.eeum.account.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 인증요청(가입 승인 대기) 파트너 수 응답 — 파트너 관리 헤더칩/사이드바 뱃지용.
 */
@Schema(description = "인증요청(승인대기) 파트너 수")
public record PartnerPendingCountResponse(
		@Schema(description = "가입 승인 대기(aprvStts=REQUESTED) 파트너 수") long requestedCount) {

	public static PartnerPendingCountResponse of(long requestedCount) {
		return new PartnerPendingCountResponse(requestedCount);
	}
}
