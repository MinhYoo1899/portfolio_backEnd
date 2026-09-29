package egovframework.eeum.auth.service.social.impl;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import egovframework.eeum.auth.service.social.OAuthResultStore;
import lombok.extern.slf4j.Slf4j;

/**
 * Redis 기반 OAuth result 저장소. 키 {@code oauth:result:<resultToken>}, 값=결과 종류 JSON(토큰 미포함), TTL 120초.
 * consume 은 GETDEL(원자 1회 소비). resultToken=난수(URL-safe Base64, SecureRandom 32바이트).
 *
 * <p>❗access/refresh 토큰은 저장하지 않는다 — LOGIN(userSn,clientType) / SIGNUP(signupToken,email,name) 만.
 */
@Slf4j
@Component
public class RedisOAuthResultStore implements OAuthResultStore {

	private static final String KEY_PREFIX = "oauth:result:";
	private static final Duration TTL = Duration.ofSeconds(120);
	private static final int RANDOM_BYTES = 32;

	private final SecureRandom secureRandom = new SecureRandom();
	private final Base64.Encoder urlEncoder = Base64.getUrlEncoder().withoutPadding();

	private final StringRedisTemplate redis;
	private final ObjectMapper objectMapper;

	public RedisOAuthResultStore(StringRedisTemplate redis, ObjectMapper objectMapper) {
		this.redis = redis;
		this.objectMapper = objectMapper;
	}

	@Override
	public String issue(OAuthResultData data) {
		String resultToken = randomToken();
		try {
			redis.opsForValue().set(KEY_PREFIX + resultToken, objectMapper.writeValueAsString(data), TTL);
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("oauth result serialize failed", e);
		}
		return resultToken;
	}

	@Override
	public OAuthResultData consume(String resultToken) {
		if (!isToken(resultToken)) {
			return null;
		}
		String json = redis.opsForValue().getAndDelete(KEY_PREFIX + resultToken);
		if (json == null) {
			return null;
		}
		try {
			return objectMapper.readValue(json, OAuthResultData.class);
		} catch (JsonProcessingException e) {
			log.error("[OAUTH] result payload parse failed", e);
			return null;
		}
	}

	private String randomToken() {
		byte[] bytes = new byte[RANDOM_BYTES];
		secureRandom.nextBytes(bytes);
		return urlEncoder.encodeToString(bytes);
	}

	/** 발급 토큰은 URL-safe Base64(무패딩) — 형식·길이 검증으로 임의 문자열 Redis 조회 차단. */
	private static boolean isToken(String value) {
		if (value == null || value.length() < 16 || value.length() > 128) {
			return false;
		}
		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			boolean ok = (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')
					|| (c >= '0' && c <= '9') || c == '-' || c == '_';
			if (!ok) {
				return false;
			}
		}
		return true;
	}
}
