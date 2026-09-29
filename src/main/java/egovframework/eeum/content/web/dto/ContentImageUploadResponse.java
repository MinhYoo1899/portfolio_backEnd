package egovframework.eeum.content.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 콘텐츠 이미지 업로드 결과 — 배너 등록/수정 시 imageUrl 로 사용하는 자체 인프라 경로.
 */
@Schema(description = "콘텐츠 이미지 업로드 결과")
public record ContentImageUploadResponse(
		@Schema(description = "이미지 URL(자체 파일인프라 반환 경로)", example = "/files/download?atchFileSn=12") String imageUrl) {
}
