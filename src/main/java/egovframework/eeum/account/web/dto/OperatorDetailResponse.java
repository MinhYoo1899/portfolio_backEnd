package egovframework.eeum.account.web.dto;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import egovframework.eeum.account.vo.OperatorVO;
import egovframework.eeum.base.crypto.PhoneMasker;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 운영자 상세 응답 DTO (계정관리 화면용, 권한코드 ACNT_MNG).
 *
 * <p>연락처는 <b>마스킹값만</b> 노출({@code 010-****-5678}) — 전체 노출은 본인({@code /operator/me})에 한정
 * (crypto-standard.md §7). 수정은 별도 update 요청으로(생략 시 보존).
 */
@Schema(description = "운영자 상세")
public record OperatorDetailResponse(
		@Schema(description = "사용자일련번호(문자열)") String userSn,
		@Schema(description = "로그인 이메일") String emlAddr,
		@Schema(description = "운영자명") String userNm,
		@Schema(description = "계정상태") String acntStts,
		@Schema(description = "부서명") String deptNm,
		@Schema(description = "직위명") String jbpsNm,
		@Schema(description = "휴대전화번호(마스킹)", example = "010-****-5678") String mblTelnoMasked,
		@Schema(description = "2차인증(MFA)사용여부") String mfaUseYn,
		@Schema(description = "보유 역할코드 목록") List<String> roleCds,
		@Schema(description = "등록일시(UTC, ISO-8601 Z)") Instant regDt) {

	public static OperatorDetailResponse from(OperatorVO vo, List<String> roleCds) {
		return new OperatorDetailResponse(
				String.valueOf(vo.getUserSn()),
				vo.getEmlAddr(),
				vo.getUserNm(),
				vo.getAcntStts(),
				vo.getDeptNm(),
				vo.getJbpsNm(),
				PhoneMasker.mask(vo.getMblTelno()),
				vo.getMfaUseYn(),
				roleCds == null ? List.of() : List.copyOf(roleCds),
				vo.getRegDt() == null ? null : vo.getRegDt().toInstant(ZoneOffset.UTC));
	}
}
