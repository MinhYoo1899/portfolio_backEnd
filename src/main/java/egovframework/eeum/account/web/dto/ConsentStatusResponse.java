package egovframework.eeum.account.web.dto;

import java.time.Instant;
import java.time.ZoneOffset;

import egovframework.eeum.account.vo.ConsentStatusVO;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 내 동의 현황 응답 — 약관 유형별 현재 동의상태(유형당 1건, 최신 행 기준).
 *
 * <p>발생시각은 Instant(UTC, ISO-8601 Z)로 노출(타임존 표준).
 */
@Schema(description = "내 동의 현황(약관 유형별 현재 상태)")
public record ConsentStatusResponse(

		@Schema(description = "약관일련번호(유형+버전)", example = "5") Long trmsSn,
		@Schema(description = "약관유형코드", example = "MARKETING") String trmsTypeCd,
		@Schema(description = "버전번호", example = "1.0") String verNo,
		@Schema(description = "약관제목", example = "마케팅 정보 수신 동의") String trmsTtl,
		@Schema(description = "필수여부(Y/N)", example = "N") String esntlYn,
		@Schema(description = "현재 동의여부(Y=동의/N=철회)", example = "Y") String agreYn,
		@Schema(description = "현재 상태 발생일시(UTC, ISO-8601 Z)") Instant agreDt) {

	public static ConsentStatusResponse from(ConsentStatusVO vo) {
		return new ConsentStatusResponse(
				vo.getTrmsSn(), vo.getTrmsTypeCd(), vo.getVerNo(), vo.getTrmsTtl(), vo.getEsntlYn(), vo.getAgreYn(),
				vo.getAgreDt() == null ? null : vo.getAgreDt().toInstant(ZoneOffset.UTC));
	}
}
