package egovframework.eeum.account.web.dto;

import java.time.Instant;
import java.time.ZoneOffset;

import egovframework.eeum.account.vo.AccountVO;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 일반회원 상세 응답 — 개인정보 전체 노출(이메일·휴대폰). 조회는 감사기록 대상(PII 접근).
 */
@Schema(description = "일반회원 상세")
public record MemberDetailResponse(
		@Schema(description = "사용자일련번호(문자열)") String userSn,
		@Schema(description = "이메일") String emlAddr,
		@Schema(description = "사용자명") String userNm,
		@Schema(description = "닉네임") String ncnm,
		@Schema(description = "사용자유형코드") String userTypeCd,
		@Schema(description = "계정상태") String acntStts,
		@Schema(description = "로그인유형(EMAIL/SOCIAL/MIXED)") String lgnType,
		@Schema(description = "휴대폰번호(전체)") String mblTelno,
		@Schema(description = "휴대폰 인증여부") boolean mblTelnoVrfc,
		@Schema(description = "가입일시(UTC, ISO-8601 Z)") Instant regDt) {

	public static MemberDetailResponse from(AccountVO vo) {
		return new MemberDetailResponse(
				String.valueOf(vo.getUserSn()),
				vo.getEmlAddr(),
				vo.getUserNm(),
				vo.getNcnm(),
				vo.getUserTypeCd(),
				vo.getAcntStts(),
				vo.getLgnType(),
				vo.getMblTelno(),
				"Y".equals(vo.getMblTelnoVrfcYn()),
				vo.getRegDt() == null ? null : vo.getRegDt().toInstant(ZoneOffset.UTC));
	}
}
