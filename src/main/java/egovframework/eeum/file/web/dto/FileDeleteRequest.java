package egovframework.eeum.file.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * 파일 삭제 요청.
 */
@Schema(description = "파일 삭제 요청")
public record FileDeleteRequest(
		@Schema(description = "첨부파일 식별자", example = "1")
		@NotNull(message = "atchFileSn은 필수입니다.")
		Long atchFileSn) {
}
