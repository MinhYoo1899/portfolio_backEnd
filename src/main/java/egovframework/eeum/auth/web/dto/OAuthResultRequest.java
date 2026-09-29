package egovframework.eeum.auth.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * OAuth result 교환 요청 — 콜백 리다이렉트로 프론트가 받은 1회용 result 토큰.
 *
 * @param resultToken 콜백이 프론트에 {@code ?result=} 로 전달한 1회용 토큰
 */
@Schema(description = "OAuth result 교환 요청")
public record OAuthResultRequest(

		@Schema(description = "콜백이 전달한 1회용 result 토큰")
		@NotBlank(message = "resultToken은 필수입니다.")
		String resultToken) {
}
