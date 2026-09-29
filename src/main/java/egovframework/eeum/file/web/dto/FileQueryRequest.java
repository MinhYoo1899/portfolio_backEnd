package egovframework.eeum.file.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * 단건 조회/다운로드 요청 — 식별자(쿼리 파라미터 바인딩). (DTO/VO 규율 — conventions.md)
 */
@Schema(description = "파일 단건 조회/다운로드 요청")
public record FileQueryRequest(
		@Schema(description = "첨부파일 식별자", example = "1")
		@NotNull(message = "atchFileSn은 필수입니다.")
		Long atchFileSn) {
}
