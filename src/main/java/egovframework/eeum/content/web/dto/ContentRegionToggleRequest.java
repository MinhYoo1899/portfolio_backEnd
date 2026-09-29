package egovframework.eeum.content.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * 즐길거리 활성 토글 요청.
 */
@Schema(description = "즐길거리 활성 토글 요청")
public record ContentRegionToggleRequest(

		@Schema(description = "노출여부(true=활성/false=비활성)") @NotNull Boolean isActive) {
}
