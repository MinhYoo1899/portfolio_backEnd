package egovframework.eeum.inquiry.web.dto;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import egovframework.eeum.inquiry.vo.InqrInfoVO;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 문의 상세 — 글 + 답변 목록. (열람 권한 통과 후 반환되므로 마스킹 없음)
 */
@Schema(description = "문의 상세")
public record InqrDetailResponse(
		String inqrSn,
		String inqrTypeCd,
		String inqrTtl,
		String inqrCn,
		@Schema(description = "작성자명") String writerNm,
		@Schema(description = "비밀글 여부") boolean secret,
		@Schema(description = "블라인드 여부") boolean blinded,
		@Schema(description = "답변상태") String ansStts,
		@Schema(description = "등록일시(UTC, ISO-8601 Z)") Instant regDt,
		@Schema(description = "답변 목록(운영자)") List<InqrAnswerResponse> answers,
		@Schema(description = "첨부 목록(공개성에 따라 다운로드 권한 상이)") List<InqrAttachmentResponse> attachments) {

	public static InqrDetailResponse from(InqrInfoVO vo, List<InqrAnswerResponse> answers) {
		return from(vo, answers, List.of());
	}

	public static InqrDetailResponse from(InqrInfoVO vo, List<InqrAnswerResponse> answers,
			List<InqrAttachmentResponse> attachments) {
		return new InqrDetailResponse(
				String.valueOf(vo.getInqrSn()),
				vo.getInqrTypeCd(),
				vo.getInqrTtl(),
				vo.getInqrCn(),
				vo.getWriterNm(),
				"Y".equals(vo.getPrvtPstYn()),
				"BLINDED".equals(vo.getPstgSttsCd()),
				vo.getAnsSttsCd(),
				vo.getRegDt() == null ? null : vo.getRegDt().toInstant(ZoneOffset.UTC),
				answers,
				attachments);
	}
}
