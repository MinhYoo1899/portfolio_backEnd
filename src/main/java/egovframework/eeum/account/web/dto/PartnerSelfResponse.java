package egovframework.eeum.account.web.dto;

import java.time.Instant;
import java.time.ZoneOffset;

import egovframework.eeum.account.vo.PartnerVO;
import egovframework.eeum.base.crypto.PhoneMasker;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 파트너 본인(PTNS 콘솔) 사업자정보 응답 — 운영자용 {@link PartnerDetailResponse} 와 분리한 별도 DTO.
 *
 * <p>운영 내부필드(승인자 aprvUserSn·승인일시 aprvDt 등)는 노출하지 않는다.
 * 담당자 연락처(picMblTelno)는 <b>마스킹값만</b> 담는다(전체값 비노출).
 */
@Schema(description = "파트너 본인 사업자 정보(PTNS)")
public record PartnerSelfResponse(
		String prtnrSn,
		String userSn,
		String loginEmail,
		String userNm,
		String acntStts,
		String conm,
		String brno,
		String rprsvNm,
		String brnoVrfcStts,
		String addr,
		String daddr,
		String mailOrderSalesNo,
		String picNm,
		@Schema(description = "담당자 휴대전화번호(마스킹값만 — 예: 010-****-5678)") String picMblTelno,
		String prtnrEmlAddr,
		String aprvStts,
		String rjctRsn,
		@Schema(description = "신청일시(UTC, ISO-8601 Z)") Instant regDt) {

	public static PartnerSelfResponse from(PartnerVO vo) {
		return new PartnerSelfResponse(
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
				vo.getMailOrderSalesNo(),
				vo.getPicNm(),
				PhoneMasker.mask(vo.getPicMblTelno()),
				vo.getPrtnrEmlAddr(),
				vo.getAprvStts(),
				vo.getRjctRsn(),
				vo.getRegDt() == null ? null : vo.getRegDt().toInstant(ZoneOffset.UTC));
	}
}
