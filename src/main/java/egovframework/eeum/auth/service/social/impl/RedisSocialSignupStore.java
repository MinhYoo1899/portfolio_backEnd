package egovframework.eeum.auth.service.social.impl;

import java.time.Duration;
import java.util.UUID;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import egovframework.eeum.auth.social.SocialUser;
import egovframework.eeum.auth.service.social.SocialSignupStore;
import lombok.extern.slf4j.Slf4j;

/**
 * Redis 기반 소셜 가입 대기 저장소. 키 {@code auth:social-signup:<token>}, 값=검증된 신원 JSON, TTL 10분.
 * consume 은 GETDEL(원자 1회 소비 — 재사용 차단).
 */
@Slf4j
@Component
public class RedisSocialSignupStore implements SocialSignupStore {

	private static final String KEY_PREFIX = "auth:social-signup:";
	private static final Duration TTL = Duration.ofMinutes(10);

	private final StringRedisTemplate redis;
	private final ObjectMapper objectMapper;

	public RedisSocialSignupStore(StringRedisTemplate redis, ObjectMapper objectMapper) {
		this.redis = redis;
		this.objectMapper = objectMapper;
	}

	@Override
	public String issue(SocialUser user) {
		String token = UUID.randomUUID().toString();
		try {
			redis.opsForValue().set(KEY_PREFIX + token, objectMapper.writeValueAsString(user), TTL);
		} catch (JsonProcessingException e) {
			// 직렬화 실패는 설정/타입 오류(정상 흐름 아님)
			throw new IllegalStateException("social signup serialize failed", e);
		}
		return token;
	}

	@Override
	public SocialUser consume(String signupToken) {
		if (!isUuid(signupToken)) {
			// 우리가 발급한 토큰은 UUID — 형식부터 거르면 임의 장문 문자열로 Redis 키 조회 유발하는 저비용 남용 차단 (codex 권고)
			return null;
		}
		// GETDEL — 1회 소비(원자). 없으면 null
		String json = redis.opsForValue().getAndDelete(KEY_PREFIX + signupToken);
		if (json == null) {
			return null;
		}
		try {
			return objectMapper.readValue(json, SocialUser.class);
		} catch (JsonProcessingException e) {
			log.error("[SOCIAL] signup payload parse failed", e);
			return null;
		}
	}

	/** 발급 토큰은 UUID — 형식 검증(임의 문자열로 Redis 조회 유발 차단). */
	private static boolean isUuid(String value) {
		if (value == null || value.length() != 36) {
			return false;
		}
		try {
			UUID.fromString(value);
			return true;
		} catch (IllegalArgumentException e) {
			return false;
		}
	}
}
