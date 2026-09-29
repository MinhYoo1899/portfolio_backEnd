package egovframework.eeum.content.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * 팝업 노출 토글 요청.
 */
@Schema(description = "팝업 노출 토글 요청")
public record ContentPopupToggleRequest(

		@Schema(description = "노출여부(true=노출/false=미노출)") @NotNull Boolean isActive) {
}
