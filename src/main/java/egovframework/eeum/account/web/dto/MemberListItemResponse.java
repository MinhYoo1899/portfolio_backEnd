package egovframework.eeum.account.web.dto;

import java.time.Instant;
import java.time.ZoneOffset;

import egovframework.eeum.account.vo.AccountVO;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 일반회원 목록 항목 응답 — 개인정보(이메일·휴대폰)는 마스킹 노출(목록 최소노출 원칙).
 *
 * <p>전체값은 상세(MemberDetailResponse)에서만 노출하며 상세조회는 감사기록 대상이다.
 */
@Schema(description = "일반회원 목록 항목")
public record MemberListItemResponse(
		@Schema(description = "사용자일련번호(문자열)") String userSn,
		@Schema(description = "이메일(마스킹)", example = "h***@example.com") String emlAddr,
		@Schema(description = "사용자명") String userNm,
		@Schema(description = "닉네임") String ncnm,
		@Schema(description = "계정상태") String acntStts,
		@Schema(description = "휴대폰(마스킹)", example = "010-****-5678") String mblTelno,
		@Schema(description = "가입일시(UTC, ISO-8601 Z)") Instant regDt) {

	public static MemberListItemResponse from(AccountVO vo) {
		return new MemberListItemResponse(
				String.valueOf(vo.getUserSn()),
				maskEmail(vo.getEmlAddr()),
				vo.getUserNm(),
				vo.getNcnm(),
				vo.getAcntStts(),
				maskPhone(vo.getMblTelno()),
				vo.getRegDt() == null ? null : vo.getRegDt().toInstant(ZoneOffset.UTC));
	}

	/** 이메일 마스킹 — 로컬파트 첫 글자만 노출(예: hong@x.com → h***@x.com). */
	static String maskEmail(String email) {
		if (email == null || email.isBlank()) {
			return null;
		}
		int at = email.indexOf('@');
		if (at <= 0) {
			return "***";
		}
		return email.charAt(0) + "***" + email.substring(at);
	}

	/** 휴대폰 마스킹 — 숫자만 추출 후 앞 3 + **** + 뒤 4(예: 01012345678 → 010-****-5678). */
	static String maskPhone(String phone) {
		if (phone == null || phone.isBlank()) {
			return null;
		}
		String digits = phone.replaceAll("\\D", "");
		if (digits.length() < 7) {
			return "***";
		}
		return digits.substring(0, 3) + "-****-" + digits.substring(digits.length() - 4);
	}
}
