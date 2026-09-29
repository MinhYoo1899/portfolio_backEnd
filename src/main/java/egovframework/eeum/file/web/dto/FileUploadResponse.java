package egovframework.eeum.file.web.dto;

import egovframework.eeum.file.vo.AtchFileVO;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 업로드 결과 — 식별자 + 다운로드 URL + 메타.
 */
@Schema(description = "파일 업로드 결과")
public record FileUploadResponse(
		@Schema(description = "첨부파일 식별자") Long atchFileSn,
		@Schema(description = "원본 파일명") String orgnlFileNm,
		@Schema(description = "다운로드 URL(백엔드 경유)") String downloadUrl,
		@Schema(description = "MIME 타입") String mimeTypeNm,
		@Schema(description = "확장자") String extnNm,
		@Schema(description = "파일 크기(byte)") Long fileSz) {

	public static FileUploadResponse from(AtchFileVO vo, String downloadUrl) {
		return new FileUploadResponse(vo.getAtchFileSn(), vo.getOrgnlFileNm(), downloadUrl,
				vo.getMimeTypeNm(), vo.getExtnNm(), vo.getFileSz());
	}
}
