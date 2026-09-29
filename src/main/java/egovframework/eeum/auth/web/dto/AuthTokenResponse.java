package egovframework.eeum.auth.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 로그인/재발급 응답 DTO.
 *
 * <p>refresh 전달 방식(확정): 웹={@code refreshToken=null}(HttpOnly 쿠키로 전달), 앱=body 포함.
 *
 * @param accessToken          access 토큰(Bearer)
 * @param accessTokenExpiresIn access 만료(초)
 * @param refreshToken         refresh 토큰 — 앱 전용(웹은 null, 쿠키로 전달)
 * @param user                 사용자 정보 + 권한 목록
 */
@Schema(description = "로그인/재발급 응답")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuthTokenResponse(
		@Schema(description = "access 토큰(Bearer)") String accessToken,
		@Schema(description = "access 만료(초)", example = "1800") long accessTokenExpiresIn,
		@Schema(description = "refresh 토큰(앱 전용 — 웹은 HttpOnly 쿠키)") String refreshToken,
		@Schema(description = "사용자 정보") AuthUserInfo user) {

	/** 웹 응답 — refresh 는 쿠키로 나가므로 body 에서 제거. */
	public AuthTokenResponse withoutRefreshToken() {
		return new AuthTokenResponse(accessToken, accessTokenExpiresIn, null, user);
	}
}
