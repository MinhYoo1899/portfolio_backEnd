package egovframework.eeum.house.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 고택 임시조치(강제 게시중지) 요청(운영자급 HOUSE_MODER).
 */
@Schema(description = "고택 강제 게시중지 요청")
public record HouseSuspendRequest(

		@Schema(description = "고택일련번호")
		@NotNull(message = "고택일련번호가 필요합니다.")
		Long houseSn,

		@Schema(description = "임시조치 사유(감사로그 기록)")
		@Size(max = 500, message = "사유는 500자 이내여야 합니다.")
		String suspendRsn) {
}
