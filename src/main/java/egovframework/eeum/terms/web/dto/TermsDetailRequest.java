package egovframework.eeum.terms.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * 약관 상세 조회 요청 — 식별자(쿼리 파라미터 바인딩). (DTO/VO 규율 — conventions.md)
 */
@Schema(description = "약관 상세 조회 요청")
public record TermsDetailRequest(
		@Schema(description = "약관일련번호", example = "1")
		@NotNull(message = "trmsSn은 필수입니다.")
		@Positive(message = "trmsSn은 양수여야 합니다.")
		Long trmsSn) {
}
