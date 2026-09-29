package egovframework.eeum.base.security;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * refresh 토큰 쿠키 정책 단일 출처 (웹 SPA 전용 — HttpOnly, path=/auth).
 *
 * <p>발급(AuthController)과 만료(로그아웃·비밀번호 변경 등)가 동일한 이름/path/SameSite/Secure 를 쓰도록
 * 한 곳으로 모은다 — 정책이 갈라져 "어떤 응답에선 쿠키가 안 지워지는" 결함을 방지(codex SHOULD ⑦).
 */
@Component
public class RefreshCookieSupport {

	/** refresh 쿠키명 — 웹 SPA 전용. */
	public static final String REFRESH_TOKEN_COOKIE = "REFRESH_TOKEN";
	private static final String REFRESH_COOKIE_PATH = "/auth";
	private static final String SAME_SITE = "Lax";

	// 운영 true(HTTPS) / dev(HTTP) false — properties 주입.
	@Value("${Globals.jwt.cookieSecure:true}")
	private boolean cookieSecure;

	@Value("${Globals.jwt.refreshValiditySeconds:1209600}")
	private long refreshValiditySeconds;

	/** refresh 토큰을 HttpOnly 쿠키로 발급. */
	public void setRefreshCookie(HttpServletResponse response, String refreshToken) {
		response.addHeader(HttpHeaders.SET_COOKIE, build(refreshToken, Duration.ofSeconds(refreshValiditySeconds)));
	}

	/** refresh 쿠키 만료(로그아웃·비밀번호 변경 후 정리). */
	public void expireRefreshCookie(HttpServletResponse response) {
		response.addHeader(HttpHeaders.SET_COOKIE, build("", Duration.ZERO));
	}

	/** 요청의 refresh 쿠키 값 — 없으면 null. */
	public String readRefreshCookie(HttpServletRequest request) {
		if (request.getCookies() == null) {
			return null;
		}
		for (Cookie cookie : request.getCookies()) {
			if (REFRESH_TOKEN_COOKIE.equals(cookie.getName())) {
				return cookie.getValue();
			}
		}
		return null;
	}

	private String build(String value, Duration maxAge) {
		return ResponseCookie.from(REFRESH_TOKEN_COOKIE, value)
				.httpOnly(true)
				.secure(cookieSecure)
				.path(REFRESH_COOKIE_PATH)
				.maxAge(maxAge)
				.sameSite(SAME_SITE)
				.build()
				.toString();
	}
}
