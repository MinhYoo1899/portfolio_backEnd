package egovframework.eeum.inquiry.web.dto;

import egovframework.eeum.file.vo.AtchFileVO;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 문의 상세의 첨부 항목 — 식별자/원본명/다운로드 URL/크기/공개성.
 *
 * <p>다운로드 URL 은 문의 도메인 권한판정 경로(GET /inquiries/files/download). PRIVATE 은 작성자·운영자만 받는다.
 */
@Schema(description = "문의 첨부")
public record InqrAttachmentResponse(
		@Schema(description = "첨부파일일련번호") Long atchFileSn,
		@Schema(description = "원본 파일명") String orgnlFileNm,
		@Schema(description = "다운로드 URL(권한판정 경유)") String downloadUrl,
		@Schema(description = "파일 크기(byte)") Long fileSz,
		@Schema(description = "공개성(PUBLIC/PRIVATE)") String visibility) {

	public static InqrAttachmentResponse from(AtchFileVO vo, String downloadUrl) {
		return new InqrAttachmentResponse(
				vo.getAtchFileSn(), vo.getOrgnlFileNm(), downloadUrl, vo.getFileSz(), vo.getAtchVisbltyCd());
	}
}
