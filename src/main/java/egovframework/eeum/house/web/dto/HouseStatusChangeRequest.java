package egovframework.eeum.house.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * 고택 상태변경 요청(판매자 본인) — 게시/숨김.
 *
 * <p>판매자가 지정 가능한 상태는 DRAFT/ACTIVE/HIDDEN 뿐. SUSPENDED(운영자 임시조치)는 불가.
 */
@Schema(description = "고택 상태변경 요청")
public record HouseStatusChangeRequest(

		@Schema(description = "고택일련번호")
		@NotNull(message = "고택일련번호가 필요합니다.")
		Long houseSn,

		@Schema(description = "변경할 상태(DRAFT/ACTIVE/HIDDEN)", example = "ACTIVE")
		@NotNull(message = "변경할 상태가 필요합니다.")
		@Pattern(regexp = "DRAFT|ACTIVE|HIDDEN", message = "상태는 DRAFT/ACTIVE/HIDDEN 만 허용됩니다.")
		String houseSttsCd) {
}
