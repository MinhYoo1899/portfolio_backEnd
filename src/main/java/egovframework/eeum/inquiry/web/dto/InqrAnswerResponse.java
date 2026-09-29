package egovframework.eeum.inquiry.web.dto;

import java.time.Instant;
import java.time.ZoneOffset;

import egovframework.eeum.inquiry.vo.InqrAnsInfoVO;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 문의 답변 항목(운영자 작성).
 */
@Schema(description = "문의 답변")
public record InqrAnswerResponse(
		String ansSn,
		@Schema(description = "답변내용") String ansCn,
		@Schema(description = "답변자명") String ansrNm,
		@Schema(description = "등록일시(UTC, ISO-8601 Z)") Instant regDt) {

	public static InqrAnswerResponse from(InqrAnsInfoVO vo) {
		return new InqrAnswerResponse(
				String.valueOf(vo.getAnsSn()),
				vo.getAnsCn(),
				vo.getAnsrNm(),
				vo.getRegDt() == null ? null : vo.getRegDt().toInstant(ZoneOffset.UTC));
	}
}
