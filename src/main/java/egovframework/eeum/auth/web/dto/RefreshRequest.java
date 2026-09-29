package egovframework.eeum.auth.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 토큰 재발급 요청 DTO.
 *
 * <p>refresh 토큰 전달: 웹은 HttpOnly 쿠키(서버가 읽음 — body 의 {@code refreshToken} 생략),
 * 앱은 body 의 {@code refreshToken}.
 *
 * <p>{@code chnlCd}는 웹/앱 모두 필수 — 원 로그인 채널과 일치해야 하며, 계정의 채널 자격
 * (CHNL_LOGIN_*)을 재검증한다(다른 채널로의 재발급 우회 차단 — codex 교차검토 필수수정).
 * (로그아웃은 같은 DTO 를 쓰지만 검증 없이 {@code refreshToken}만 사용)
 *
 * @param refreshToken (앱 전용) refresh 토큰 — 웹은 생략(쿠키 우선)
 * @param chnlCd       로그인 채널(원 로그인과 동일): USER/PARTNER/OPERATOR
 */
@Schema(description = "토큰 재발급 요청 (refresh: 웹=쿠키·앱=body, chnlCd: 공통 필수)")
public record RefreshRequest(

		@Schema(description = "refresh 토큰(앱 전용 — 웹은 쿠키로 전달되므로 생략)")
		String refreshToken,

		// @Pattern 은 null 을 통과시키므로 @NotBlank 병행 필수 (codex 교차검토)
		@Schema(description = "로그인 채널(원 로그인과 동일해야 함): USER/PARTNER/OPERATOR", example = "USER")
		@NotBlank(message = "로그인 채널(chnlCd)을 입력해 주세요.")
		@Pattern(regexp = "USER|PARTNER|OPERATOR", message = "chnlCd는 USER/PARTNER/OPERATOR만 허용됩니다.")
		String chnlCd) {
}
