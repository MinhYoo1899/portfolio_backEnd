package egovframework.eeum.account.web.dto;

import java.time.Instant;
import java.time.ZoneOffset;

import egovframework.eeum.account.vo.PartnerVO;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 파트너 목록 항목 응답 DTO.
 */
@Schema(description = "파트너 목록 항목")
public record PartnerListItemResponse(
		@Schema(description = "파트너일련번호(문자열)") String prtnrSn,
		@Schema(description = "상호") String conm,
		@Schema(description = "사업자등록번호") String brno,
		@Schema(description = "대표자명") String rprsvNm,
		@Schema(description = "가입자명") String userNm,
		@Schema(description = "로그인 이메일") String emlAddr,
		@Schema(description = "담당자명") String picNm,
		@Schema(description = "파트너 대표 이메일(회사)") String prtnrEmlAddr,
		@Schema(description = "등록 고택 수(미삭제 기준)") Integer houseCnt,
		@Schema(description = "승인상태") String aprvStts,
		@Schema(description = "신청일시(UTC, ISO-8601 Z)") Instant regDt) {
	// ⚠ 담당자 연락처(picMblTelno)는 목록에 포함하지 않는다 — 목록 대량열람 시 평문연락처 복호화/노출 차단(codex MUST).
	//    마스킹값이라도 복호화가 선행되므로 상세(detail) 응답에서만 제공한다.

	public static PartnerListItemResponse from(PartnerVO vo) {
		return new PartnerListItemResponse(
				String.valueOf(vo.getPrtnrSn()),
				vo.getConm(),
				vo.getBrno(),
				vo.getRprsvNm(),
				vo.getUserNm(),
				vo.getEmlAddr(),
				vo.getPicNm(),
				vo.getPrtnrEmlAddr(),
				vo.getHouseCnt(),
				vo.getAprvStts(),
				vo.getRegDt() == null ? null : vo.getRegDt().toInstant(ZoneOffset.UTC));
	}
}
