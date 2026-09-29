package egovframework.eeum.account.web.dto;

import java.time.Instant;
import java.time.ZoneOffset;

import egovframework.eeum.account.vo.PartnerVO;
import egovframework.eeum.base.crypto.PhoneMasker;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 파트너 상세 응답 DTO (운영자 승인 화면용).
 */
@Schema(description = "파트너 상세")
public record PartnerDetailResponse(
		@Schema(description = "파트너일련번호(문자열)") String prtnrSn,
		@Schema(description = "사용자일련번호(문자열)") String userSn,
		@Schema(description = "로그인 이메일") String emlAddr,
		@Schema(description = "가입자명") String userNm,
		@Schema(description = "계정상태") String acntStts,
		@Schema(description = "상호") String conm,
		@Schema(description = "사업자등록번호") String brno,
		@Schema(description = "대표자명") String rprsvNm,
		@Schema(description = "사업자등록번호검증상태") String brnoVrfcStts,
		@Schema(description = "사업장주소") String addr,
		@Schema(description = "사업장상세주소") String daddr,
		@Schema(description = "담당자명") String picNm,
		@Schema(description = "파트너 대표 이메일") String prtnrEmlAddr,
		@Schema(description = "승인상태") String aprvStts,
		@Schema(description = "승인/반려일시(UTC, ISO-8601 Z)") Instant aprvDt,
		@Schema(description = "반려사유") String rjctRsn,
		@Schema(description = "신청일시(UTC, ISO-8601 Z)") Instant regDt,
			@Schema(description = "통신판매업신고번호(선택)") String mailOrderSalesNo,
			@Schema(description = "담당자 휴대전화번호(마스킹값만 — 예: 010-****-5678)") String picMblTelno) {

	public static PartnerDetailResponse from(PartnerVO vo) {
		return new PartnerDetailResponse(
				String.valueOf(vo.getPrtnrSn()),
				String.valueOf(vo.getUserSn()),
				vo.getEmlAddr(),
				vo.getUserNm(),
				vo.getAcntStts(),
				vo.getConm(),
				vo.getBrno(),
				vo.getRprsvNm(),
				vo.getBrnoVrfcStts(),
				vo.getAddr(),
				vo.getDaddr(),
				vo.getPicNm(),
				vo.getPrtnrEmlAddr(),
				vo.getAprvStts(),
				vo.getAprvDt() == null ? null : vo.getAprvDt().toInstant(ZoneOffset.UTC),
				vo.getRjctRsn(),
				vo.getRegDt() == null ? null : vo.getRegDt().toInstant(ZoneOffset.UTC),
				vo.getMailOrderSalesNo(),
				PhoneMasker.mask(vo.getPicMblTelno()));
	}
}
