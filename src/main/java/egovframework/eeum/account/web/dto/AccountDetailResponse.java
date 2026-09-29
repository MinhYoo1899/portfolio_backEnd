package egovframework.eeum.account.web.dto;

import java.time.Instant;
import java.time.ZoneOffset;

import egovframework.eeum.account.vo.AccountVO;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 계정 상세 응답 DTO.
 */
@Schema(description = "계정 상세")
public record AccountDetailResponse(
		@Schema(description = "사용자일련번호(문자열)", example = "1024") String userSn,
		@Schema(description = "이메일주소") String emlAddr,
		@Schema(description = "사용자유형코드", example = "MEMBER") String userTypeCd,
		@Schema(description = "계정상태", example = "ACTIVE") String acntStts,
		@Schema(description = "사용자명") String userNm,
		@Schema(description = "별명") String ncnm,
		@Schema(description = "휴대전화번호 인증여부") boolean mblTelnoVrfc,
		@Schema(description = "이메일 인증여부") boolean emailVerified,
		@Schema(description = "가입일시(UTC, ISO-8601 Z)") Instant regDt) {

	public static AccountDetailResponse from(AccountVO vo) {
		return new AccountDetailResponse(
				String.valueOf(vo.getUserSn()),
				vo.getEmlAddr(),
				vo.getUserTypeCd(),
				vo.getAcntStts(),
				vo.getUserNm(),
				vo.getNcnm(),
				"Y".equals(vo.getMblTelnoVrfcYn()),
				"Y".equals(vo.getEmlVrfdYn()),
				vo.getRegDt() == null ? null : vo.getRegDt().toInstant(ZoneOffset.UTC));
	}
}
