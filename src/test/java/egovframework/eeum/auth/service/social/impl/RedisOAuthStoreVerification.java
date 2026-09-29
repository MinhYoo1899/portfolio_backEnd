package egovframework.eeum.auth.service.social.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;

import com.fasterxml.jackson.databind.ObjectMapper;

import egovframework.eeum.auth.service.social.OAuthResultStore.OAuthResultData;
import egovframework.eeum.auth.service.social.OAuthStateStore.OAuthStateData;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

/**
 * OAuth state/result Redis 저장소 라운드트립 검증 — set/getAndDelete 를 in-memory map 으로 시뮬레이션(원자 1회 소비).
 * 실제 Redis 무관(순수 로직). 발급 토큰 형식검증·1회 소비·만료(null) 동작을 고정한다.
 */
class RedisOAuthStoreVerification {

	private final ObjectMapper objectMapper = new ObjectMapper();

	/** opsForValue().set / getAndDelete 를 map 으로 흉내내는 StringRedisTemplate mock 구성. */
	private StringRedisTemplate fakeRedis(Map<String, String> store) {
		StringRedisTemplate redis = mock(StringRedisTemplate.class);
		@SuppressWarnings("unchecked")
		ValueOperations<String, String> ops = mock(ValueOperations.class);
		when(redis.opsForValue()).thenReturn(ops);
		// set(key, value, ttl)
		org.mockito.Mockito.doAnswer((InvocationOnMock inv) -> {
			store.put(inv.getArgument(0), inv.getArgument(1));
			return null;
		}).when(ops).set(anyString(), anyString(), org.mockito.ArgumentMatchers.any(Duration.class));
		// getAndDelete(key) — 원자 1회 소비
		when(ops.getAndDelete(anyString())).thenAnswer((InvocationOnMock inv) -> store.remove(inv.getArgument(0)));
		return redis;
	}

	@Test
	@DisplayName("state — issue→consume 라운드트립 후 1회 소비(재소비 null)")
	void stateRoundTripAndSingleUse() {
		Map<String, String> store = new HashMap<>();
		RedisOAuthStateStore stateStore = new RedisOAuthStateStore(fakeRedis(store), objectMapper);

		OAuthStateData data = new OAuthStateData("GOOGLE", "LOGIN", "WEB", "verifier-x", "nonce-y", "default", "bindhash");
		String state = stateStore.issue(data);

		OAuthStateData consumed = stateStore.consume(state);
		assertEquals(data, consumed);
		// 두 번째 소비는 null(GETDEL 1회용)
		assertNull(stateStore.consume(state));
	}

	@Test
	@DisplayName("state — 발급 토큰은 URL-safe, 매번 달라짐")
	void stateTokenIsRandomUrlSafe() {
		Map<String, String> store = new HashMap<>();
		RedisOAuthStateStore stateStore = new RedisOAuthStateStore(fakeRedis(store), objectMapper);
		String s1 = stateStore.issue(new OAuthStateData("GOOGLE", "LOGIN", "WEB", "v", "n", "default", "bh"));
		String s2 = stateStore.issue(new OAuthStateData("GOOGLE", "LOGIN", "WEB", "v", "n", "default", "bh"));
		assertNotEquals(s1, s2);
		assertTrue(s1.matches("[A-Za-z0-9_-]+"), "URL-safe");
	}

	@Test
	@DisplayName("state — 형식 위반(짧음/특수문자/null)은 Redis 조회 없이 null")
	void stateRejectsMalformed() {
		Map<String, String> store = new HashMap<>();
		RedisOAuthStateStore stateStore = new RedisOAuthStateStore(fakeRedis(store), objectMapper);
		assertNull(stateStore.consume(null));
		assertNull(stateStore.consume("short"));
		assertNull(stateStore.consume("has space and+slash/"));
	}

	@Test
	@DisplayName("result — LOGIN issue→consume 라운드트립 + 1회 소비")
	void resultLoginRoundTrip() {
		Map<String, String> store = new HashMap<>();
		RedisOAuthResultStore resultStore = new RedisOAuthResultStore(fakeRedis(store), objectMapper);

		OAuthResultData data = OAuthResultData.login(42L, "WEB", "bindhash");
		String token = resultStore.issue(data);

		OAuthResultData consumed = resultStore.consume(token);
		assertEquals(data, consumed);
		assertEquals(OAuthResultData.TYPE_LOGIN, consumed.type());
		assertNull(resultStore.consume(token));
	}

	@Test
	@DisplayName("result — SIGNUP 저장값에 토큰(access/refresh) 미포함, 가입 식별정보만")
	void resultSignupRoundTrip() {
		Map<String, String> store = new HashMap<>();
		RedisOAuthResultStore resultStore = new RedisOAuthResultStore(fakeRedis(store), objectMapper);

		OAuthResultData data = OAuthResultData.signup("signup-tok", "a@b.com", "홍길동", "bindhash");
		String token = resultStore.issue(data);

		// 저장된 JSON 에 access/refresh 흔적이 없어야 함(토큰 비저장 원칙) — consume 전 캡처(GETDEL 로 비워지기 전)
		String storedJson = store.values().iterator().next();
		assertTrue(!storedJson.contains("accessToken") && !storedJson.contains("refreshToken"),
				"result 저장값에 토큰 미포함: " + storedJson);

		OAuthResultData consumed = resultStore.consume(token);
		assertEquals(OAuthResultData.TYPE_SIGNUP, consumed.type());
		assertEquals("signup-tok", consumed.signupToken());
		assertNull(consumed.userSn());
	}

	@Test
	@DisplayName("result — 미존재 토큰은 null")
	void resultMissingIsNull() {
		Map<String, String> store = new HashMap<>();
		RedisOAuthResultStore resultStore = new RedisOAuthResultStore(fakeRedis(store), objectMapper);
		assertNull(resultStore.consume("AAAAAAAAAAAAAAAAAAAA")); // 형식은 통과하나 미존재
	}
}
