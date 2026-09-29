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
 * 소셜 웹 Authorization Code 흐름 바인딩 쿠키 정책 단일 출처 — HttpOnly, path=/auth/oauth.
 *
 * <p>{@code /authorize} 에서 발급한 1회성 토큰을 이 쿠키로 내려, {@code /callback}·{@code /result} 에서 같은
 * 브라우저인지 대조한다(로그인 CSRF 차단 — 공격자가 발급한 result 토큰을 피해자 브라우저가 소비하지 못하게).
 * 서버 state/result 에는 이 값의 해시만 저장하므로 쿠키 원문은 브라우저에만 존재한다.
 *
 * <p>SameSite/Secure 는 설정값(dev=Lax/false, prod=None/true — cross-site 프론트는 HTTPS+None 필요)으로 둔다.
 * path=/auth/oauth 로 좁혀 다른 엔드포인트로 전송되지 않게 한다.
 */
@Component
public class OAuthFlowCookieSupport {

	/** 흐름 바인딩 쿠키명. */
	public static final String OAUTH_FLOW_COOKIE = "OAUTH_FLOW";
	private static final String COOKIE_PATH = "/auth/oauth";
	private static final Duration TTL = Duration.ofSeconds(300);

	@Value("${eeum.oauth.cookie.secure:false}")
	private boolean cookieSecure;

	@Value("${eeum.oauth.cookie.same-site:Lax}")
	private String sameSite;

	/** 흐름 바인딩 토큰을 HttpOnly 쿠키로 발급(authorize 시작 시). */
	public void setFlowCookie(HttpServletResponse response, String flowBindingToken) {
		response.addHeader(HttpHeaders.SET_COOKIE, build(flowBindingToken, TTL));
	}

	/** 흐름 바인딩 쿠키 만료(result 교환 직후 정리 — 1회성 보장). */
	public void expireFlowCookie(HttpServletResponse response) {
		response.addHeader(HttpHeaders.SET_COOKIE, build("", Duration.ZERO));
	}

	/** 요청의 흐름 바인딩 쿠키 값 — 없으면 null. */
	public String readFlowCookie(HttpServletRequest request) {
		if (request.getCookies() == null) {
			return null;
		}
		for (Cookie cookie : request.getCookies()) {
			if (OAUTH_FLOW_COOKIE.equals(cookie.getName())) {
				return cookie.getValue();
			}
		}
		return null;
	}

	private String build(String value, Duration maxAge) {
		return ResponseCookie.from(OAUTH_FLOW_COOKIE, value)
				.httpOnly(true)
				.secure(cookieSecure)
				.path(COOKIE_PATH)
				.maxAge(maxAge)
				.sameSite(sameSite)
				.build()
				.toString();
	}
}
