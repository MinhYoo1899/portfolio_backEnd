package egovframework.eeum.account.web.dto;

import egovframework.eeum.file.web.dto.FileUploadResponse;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 프로필 이미지 교체 결과 — 새 프로필 이미지의 식별자/다운로드 URL/메타.
 */
@Schema(description = "프로필 이미지 교체 결과")
public record ProfileImageResponse(
		@Schema(description = "첨부파일 식별자") Long atchFileSn,
		@Schema(description = "다운로드 URL(백엔드 경유)") String downloadUrl,
		@Schema(description = "MIME 타입") String mimeTypeNm,
		@Schema(description = "확장자") String extnNm,
		@Schema(description = "파일 크기(byte)") Long fileSz) {

	public static ProfileImageResponse from(FileUploadResponse stored) {
		return new ProfileImageResponse(stored.atchFileSn(), stored.downloadUrl(),
				stored.mimeTypeNm(), stored.extnNm(), stored.fileSz());
	}
}
