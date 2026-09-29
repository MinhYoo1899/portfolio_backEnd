package egovframework.eeum.inquiry.web.dto;

import java.time.Instant;
import java.time.ZoneOffset;

import egovframework.eeum.inquiry.vo.InqrInfoVO;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 문의 목록 항목. 비밀글 비인가자에겐 SQL 에서 title/writerNm 이 NULL 로 내려오며 {@code masked=true}.
 */
@Schema(description = "문의 목록 항목")
public record InqrListItemResponse(
		String inqrSn,
		String inqrTypeCd,
		@Schema(description = "제목(비밀글 마스킹 시 null)") String inqrTtl,
		@Schema(description = "작성자명(비밀글 마스킹 시 null)") String writerNm,
		@Schema(description = "비밀글 여부") boolean secret,
		@Schema(description = "블라인드 여부(운영자 조회 시)") boolean blinded,
		@Schema(description = "마스킹됨(비밀글+비인가)") boolean masked,
		@Schema(description = "답변상태(PENDING/ANSWERED)") String ansStts,
		@Schema(description = "답변 수") Integer answerCnt,
		@Schema(description = "등록일시(UTC, ISO-8601 Z)") Instant regDt) {

	public static InqrListItemResponse from(InqrInfoVO vo) {
		boolean secret = "Y".equals(vo.getPrvtPstYn());
		boolean masked = secret && vo.getInqrTtl() == null; // SQL 마스킹(title null) 판정
		return new InqrListItemResponse(
				String.valueOf(vo.getInqrSn()),
				vo.getInqrTypeCd(),
				vo.getInqrTtl(),
				vo.getWriterNm(),
				secret,
				"BLINDED".equals(vo.getPstgSttsCd()),
				masked,
				vo.getAnsSttsCd(),
				vo.getAnswerCnt(),
				vo.getRegDt() == null ? null : vo.getRegDt().toInstant(ZoneOffset.UTC));
	}
}
