package egovframework.eeum.auth.service.social.impl;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import egovframework.eeum.auth.service.social.OAuthStateStore;
import lombok.extern.slf4j.Slf4j;

/**
 * Redis 기반 OAuth state 저장소. 키 {@code oauth:state:<state>}, 값=컨텍스트 JSON, TTL 300초.
 * consume 은 GETDEL(원자 1회 소비 — 재사용/재생 차단). state=난수(URL-safe Base64, SecureRandom 32바이트).
 */
@Slf4j
@Component
public class RedisOAuthStateStore implements OAuthStateStore {

	private static final String KEY_PREFIX = "oauth:state:";
	private static final Duration TTL = Duration.ofSeconds(300);
	private static final int RANDOM_BYTES = 32;

	private final SecureRandom secureRandom = new SecureRandom();
	private final Base64.Encoder urlEncoder = Base64.getUrlEncoder().withoutPadding();

	private final StringRedisTemplate redis;
	private final ObjectMapper objectMapper;

	public RedisOAuthStateStore(StringRedisTemplate redis, ObjectMapper objectMapper) {
		this.redis = redis;
		this.objectMapper = objectMapper;
	}

	@Override
	public String issue(OAuthStateData data) {
		String state = randomToken();
		try {
			redis.opsForValue().set(KEY_PREFIX + state, objectMapper.writeValueAsString(data), TTL);
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("oauth state serialize failed", e);
		}
		return state;
	}

	@Override
	public OAuthStateData consume(String state) {
		if (!isToken(state)) {
			// 우리가 발급한 state 는 URL-safe Base64 — 형식부터 거르면 임의 장문 문자열로 Redis 조회 유발하는 남용 차단
			return null;
		}
		String json = redis.opsForValue().getAndDelete(KEY_PREFIX + state);
		if (json == null) {
			return null;
		}
		try {
			return objectMapper.readValue(json, OAuthStateData.class);
		} catch (JsonProcessingException e) {
			log.error("[OAUTH] state payload parse failed", e);
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
