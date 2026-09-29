package egovframework.eeum.file.web.dto;

import java.time.Instant;
import java.time.ZoneOffset;

import egovframework.eeum.file.vo.AtchFileVO;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 파일 메타 상세(공개 이미지).
 */
@Schema(description = "파일 메타 상세")
public record FileDetailResponse(
		@Schema(description = "첨부파일 식별자") Long atchFileSn,
		@Schema(description = "원본 파일명") String orgnlFileNm,
		@Schema(description = "다운로드 URL") String downloadUrl,
		@Schema(description = "MIME 타입") String mimeTypeNm,
		@Schema(description = "확장자") String extnNm,
		@Schema(description = "파일 크기(byte)") Long fileSz,
		@Schema(description = "공개범위코드") String fileScopeCd,
		@Schema(description = "등록일시(UTC, ISO-8601 Z)") Instant regDt) {

	public static FileDetailResponse from(AtchFileVO vo, String downloadUrl) {
		return new FileDetailResponse(vo.getAtchFileSn(), vo.getOrgnlFileNm(), downloadUrl,
				vo.getMimeTypeNm(), vo.getExtnNm(), vo.getFileSz(), vo.getFileScopeCd(),
				vo.getRegDt() == null ? null : vo.getRegDt().toInstant(ZoneOffset.UTC));
	}
}
