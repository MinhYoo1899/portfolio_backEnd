package egovframework.eeum.idempotency.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import com.fasterxml.jackson.databind.ObjectMapper;

import egovframework.eeum.idempotency.IdempotencyInfraException;
import egovframework.eeum.idempotency.IdempotencyRecord;

/**
 * Redis 멱등 저장소 검증 — setIfAbsent/get/set/delete 를 in-memory map 으로 시뮬레이션(실제 Redis 무관, 순수 로직).
 * claim 선점(true/false)·find 라운드트립·promote(DONE)·release·인프라 예외 감싸기를 고정한다.
 */
class RedisIdempotencyStoreVerification {

	private static final Duration PENDING_TTL = Duration.ofSeconds(60);
	private static final Duration DONE_TTL = Duration.ofSeconds(86400);

	private final ObjectMapper objectMapper = new ObjectMapper();

	/** setIfAbsent(원자 선점)/get/set/delete 를 map 으로 흉내내는 StringRedisTemplate mock 구성. */
	private StringRedisTemplate fakeRedis(Map<String, String> store) {
		StringRedisTemplate redis = mock(StringRedisTemplate.class);
		@SuppressWarnings("unchecked")
		ValueOperations<String, String> ops = mock(ValueOperations.class);
		when(redis.opsForValue()).thenReturn(ops);
		// setIfAbsent(key, value, ttl) — 원자 put-if-absent 로 모델링(ConcurrentHashMap.putIfAbsent).
		// check-then-put(비원자) 대신 putIfAbsent 를 써야 동시성 테스트가 실제 원자성 계약을 검증할 수 있다.
		when(ops.setIfAbsent(anyString(), anyString(), org.mockito.ArgumentMatchers.any(Duration.class)))
				.thenAnswer((InvocationOnMock inv) ->
						store.putIfAbsent(inv.getArgument(0), inv.getArgument(1)) == null
								? Boolean.TRUE : Boolean.FALSE);
		// set(key, value, ttl) — 덮어쓰기
		org.mockito.Mockito.doAnswer((InvocationOnMock inv) -> {
			store.put(inv.getArgument(0), inv.getArgument(1));
			return null;
		}).when(ops).set(anyString(), anyString(), org.mockito.ArgumentMatchers.any(Duration.class));
		// get(key)
		when(ops.get(anyString())).thenAnswer((InvocationOnMock inv) -> store.get(inv.getArgument(0)));
		// delete(key)
		when(redis.delete(anyString())).thenAnswer((InvocationOnMock inv) -> {
			return store.remove(inv.getArgument(0)) != null;
		});
		return redis;
	}

	@Test
	@DisplayName("claim — 최초 선점 true, 같은 키 재선점 false(setIfAbsent 원자성)")
	void claimTrueThenFalse() {
		Map<String, String> store = new HashMap<>();
		RedisIdempotencyStore idem = new RedisIdempotencyStore(fakeRedis(store), objectMapper);

		assertTrue(idem.claim("idem:v1:1:PARTNER:POST:/houses/insert:k1", "hashA", PENDING_TTL));
		assertFalse(idem.claim("idem:v1:1:PARTNER:POST:/houses/insert:k1", "hashA", PENDING_TTL));
	}

	@Test
	@DisplayName("find — 선점 후 PENDING 레코드 라운드트립(역직렬화)")
	void findDeserializesPending() {
		Map<String, String> store = new HashMap<>();
		RedisIdempotencyStore idem = new RedisIdempotencyStore(fakeRedis(store), objectMapper);
		idem.claim("k", "hashA", PENDING_TTL);

		Optional<IdempotencyRecord> found = idem.find("k");
		assertTrue(found.isPresent());
		assertEquals(IdempotencyRecord.STATE_PENDING, found.get().state());
		assertEquals("hashA", found.get().bodyHash());
	}

	@Test
	@DisplayName("find — 미존재 키는 empty")
	void findMissingIsEmpty() {
		Map<String, String> store = new HashMap<>();
		RedisIdempotencyStore idem = new RedisIdempotencyStore(fakeRedis(store), objectMapper);
		assertTrue(idem.find("nope").isEmpty());
	}

	@Test
	@DisplayName("promote — DONE 레코드로 승격 후 find 라운드트립(상태/본문/헤더/응답 보존)")
	void promoteRoundTrip() {
		Map<String, String> store = new HashMap<>();
		RedisIdempotencyStore idem = new RedisIdempotencyStore(fakeRedis(store), objectMapper);
		idem.claim("k", "hashA", PENDING_TTL);

		IdempotencyRecord done = IdempotencyRecord.done("hashA", 201, "application/json",
				Map.of("Location", "/houses/9"), "{\"resultCode\":\"SUCCESS\"}");
		idem.promote("k", done, DONE_TTL);

		IdempotencyRecord found = idem.find("k").orElseThrow();
		assertEquals(IdempotencyRecord.STATE_DONE, found.state());
		assertEquals(201, found.httpStatus());
		assertEquals("application/json", found.contentType());
		assertEquals("/houses/9", found.headers().get("Location"));
		assertEquals("{\"resultCode\":\"SUCCESS\"}", found.responseBody());
		assertEquals("hashA", found.bodyHash());
	}

	@Test
	@DisplayName("release — 키 삭제 후 find empty")
	void releaseDeletes() {
		Map<String, String> store = new HashMap<>();
		RedisIdempotencyStore idem = new RedisIdempotencyStore(fakeRedis(store), objectMapper);
		idem.claim("k", "hashA", PENDING_TTL);
		idem.release("k");
		assertTrue(idem.find("k").isEmpty());
	}

	@Test
	@DisplayName("인프라 장애 — claim/find 는 IdempotencyInfraException 으로 감싼다(fail-closed 신호)")
	void infraFailureWrapped() {
		StringRedisTemplate redis = mock(StringRedisTemplate.class);
		@SuppressWarnings("unchecked")
		ValueOperations<String, String> ops = mock(ValueOperations.class);
		when(redis.opsForValue()).thenReturn(ops);
		when(ops.setIfAbsent(anyString(), anyString(), org.mockito.ArgumentMatchers.any(Duration.class)))
				.thenThrow(new RedisConnectionFailureException("down"));
		when(ops.get(anyString())).thenThrow(new RedisConnectionFailureException("down"));

		RedisIdempotencyStore idem = new RedisIdempotencyStore(redis, objectMapper);
		assertThrows(IdempotencyInfraException.class, () -> idem.claim("k", "hashA", PENDING_TTL));
		assertThrows(IdempotencyInfraException.class, () -> idem.find("k"));
	}

	@Test
	@DisplayName("claim — N스레드가 같은 키를 동시 선점해도 정확히 1개만 성공(setIfAbsent 원자성)")
	void claimAtomicUnderConcurrency() throws Exception {
		// 원자 put-if-absent 를 보장하는 ConcurrentHashMap 백엔드 — 이 기능의 존재 이유(동시요청 단일승자)를 회귀에서 지킨다.
		Map<String, String> store = new ConcurrentHashMap<>();
		RedisIdempotencyStore idem = new RedisIdempotencyStore(fakeRedis(store), objectMapper);
		String key = "idem:v1:1:PARTNER:POST:/houses/insert:concurrent";

		int threads = 32;
		ExecutorService pool = Executors.newFixedThreadPool(threads);
		CountDownLatch startGate = new CountDownLatch(1);
		CountDownLatch doneGate = new CountDownLatch(threads);
		AtomicInteger winners = new AtomicInteger();
		try {
			for (int i = 0; i < threads; i++) {
				pool.submit(() -> {
					try {
						startGate.await(); // 모든 스레드를 같은 출발선에 세운다
						if (idem.claim(key, "hashA", PENDING_TTL)) {
							winners.incrementAndGet();
						}
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
					} finally {
						doneGate.countDown();
					}
				});
			}
			startGate.countDown(); // 동시 출발
			assertTrue(doneGate.await(5, TimeUnit.SECONDS), "모든 스레드가 제한시간 내 완료해야 한다");
		} finally {
			pool.shutdownNow();
		}
		assertEquals(1, winners.get()); // 정확히 1개만 선점 성공
	}
}
