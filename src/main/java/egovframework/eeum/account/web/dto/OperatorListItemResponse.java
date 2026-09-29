package egovframework.eeum.account.web.dto;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import egovframework.eeum.account.vo.OperatorVO;
import egovframework.eeum.base.crypto.PhoneMasker;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 운영자 목록 항목 응답 DTO. 연락처는 <b>마스킹값만</b>(대량열람 평문 노출 차단, crypto-standard.md §7).
 */
@Schema(description = "운영자 목록 항목")
public record OperatorListItemResponse(
		@Schema(description = "사용자일련번호(문자열)") String userSn,
		@Schema(description = "로그인 이메일") String emlAddr,
		@Schema(description = "운영자명") String userNm,
		@Schema(description = "계정상태") String acntStts,
		@Schema(description = "부서명") String deptNm,
		@Schema(description = "직위명") String jbpsNm,
		@Schema(description = "휴대전화번호(마스킹)", example = "010-****-5678") String mblTelnoMasked,
		@Schema(description = "보유 역할코드 목록") List<String> roleCds,
		@Schema(description = "등록일시(UTC, ISO-8601 Z)") Instant regDt) {

	public static OperatorListItemResponse from(OperatorVO vo, List<String> roleCds) {
		return new OperatorListItemResponse(
				String.valueOf(vo.getUserSn()),
				vo.getEmlAddr(),
				vo.getUserNm(),
				vo.getAcntStts(),
				vo.getDeptNm(),
				vo.getJbpsNm(),
				PhoneMasker.mask(vo.getMblTelno()),
				roleCds == null ? List.of() : List.copyOf(roleCds),
				vo.getRegDt() == null ? null : vo.getRegDt().toInstant(ZoneOffset.UTC));
	}
}
