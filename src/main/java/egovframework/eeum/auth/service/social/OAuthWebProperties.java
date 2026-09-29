package egovframework.eeum.auth.service.social;

import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

/**
 * 웹 Authorization Code 흐름 설정 — {@code eeum.oauth.*}(callback-base-url + web.redirects allowlist).
 *
 * <p>callback redirect_uri 는 config 고정({@code callbackBaseUrl}/auth/oauth/{provider}/callback) — Host 헤더 동적생성 금지.
 * 프론트 redirect 는 {@code redirects}(redirectKey→절대 URL) allowlist 에 있는 키만 허용(오픈리다이렉트 차단).
 */
@Component
@ConfigurationProperties(prefix = "eeum.oauth")
@Getter
@Setter
public class OAuthWebProperties {

	/** 콜백 redirect_uri 베이스(고정). */
	private String callbackBaseUrl;

	/** 웹 흐름 설정. */
	private Web web = new Web();

	@Getter
	@Setter
	public static class Web {
		/** 프론트 redirect allowlist — redirectKey → 절대 URL. 알려진 키 없으면 거부. */
		private Map<String, String> redirects = new HashMap<>();
	}

	/** 고정 콜백 redirect_uri 구성 — {@code {callbackBaseUrl}/auth/oauth/{providerCd}/callback}(소문자 provider). */
	public String callbackUri(String providerCd) {
		return callbackBaseUrl + "/auth/oauth/" + providerCd.toLowerCase() + "/callback";
	}

	/** redirectKey 의 프론트 절대 URL — allowlist 에 없으면 null(거부). */
	public String redirectUrl(String redirectKey) {
		return web.getRedirects().get(redirectKey);
	}
}
