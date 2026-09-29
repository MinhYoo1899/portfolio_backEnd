package egovframework.eeum.reservation.service.hold.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.test.util.ReflectionTestUtils;

import egovframework.eeum.reservation.service.hold.ReservationHoldStore;
import egovframework.eeum.reservation.service.hold.ReservationHoldStore.HoldNight;
import egovframework.eeum.reservation.service.hold.ReservationHoldStore.HoldResult;

/**
 * 예약 소프트홀드 저장소({@link RedisReservationHoldStore}) 검증.
 *
 * <p>실제 Redis Lua 는 개발서버 스모크가 담당(LoginAttemptGuard 기조). 여기서는 ACQUIRE/RELEASE Lua 를
 * <b>충실히 재현한 인메모리 ZSET 시뮬레이터</b>로 저장소의 인자 전달·결과 해석·알고리즘 계약을 검증한다.
 * 시뮬레이터의 exec 는 {@code synchronized} 로 Redis 단일스레드 EVAL 원자성을 모델링하고, {@code now}는 주입식(시계 전진 테스트 가능).
 *
 * <p>증명: (1)다박 all-or-nothing (2)만료 sweep 후 ZCARD 재고 (3)release 멱등+holdId 격리 (4)holdId별 독립 점유 · 동시성 단일용량.
 */
class RedisReservationHoldStoreVerification {

	private static final long ROOM_SN = 11L;
	private static final LocalDate D1 = LocalDate.of(2026, 8, 1);
	private static final LocalDate D2 = LocalDate.of(2026, 8, 2);
	private static final LocalDate D3 = LocalDate.of(2026, 8, 3);

	private ZsetSim sim;
	private RedisReservationHoldStore store;

	@BeforeEach
	void setUp() {
		sim = new ZsetSim(1_000L); // 서버 now = 1000초(가상)
		// Mockito varargs 매칭을 피해 execute 를 직접 오버라이드 — ACQUIRE/RELEASE Lua 를 인메모리 시뮬레이터로 흘린다.
		StringRedisTemplate redis = new StringRedisTemplate() {
			@Override
			public <T> T execute(RedisScript<T> script, List<String> keys, Object... args) {
				List<String> a = new ArrayList<>(args.length);
				for (Object o : args) {
					a.add(String.valueOf(o));
				}
				@SuppressWarnings("unchecked")
				T result = (T) Long.valueOf(sim.exec(keys, a));
				return result;
			}
		};
		store = new RedisReservationHoldStore(redis);
		ReflectionTestUtils.setField(store, "windowSeconds", 600L);
		ReflectionTestUtils.setField(store, "bufferSeconds", 30L);
	}

	private static List<HoldNight> nights(HoldNight... n) {
		return List.of(n);
	}

	@Test
	@DisplayName("(1) 다박 acquire all-or-nothing — 한 밤 재고 부족이면 어느 밤에도 hold 멤버가 남지 않음")
	void multiNight_partialShortage_leavesNoMembers() {
		// D2 만 매진(total=1,sold=1) — 가운데 밤에서 실패해야 하고, 이미 통과한 D1 에도 멤버가 없어야 한다.
		HoldResult r = store.acquire("hA", ROOM_SN, nights(
				new HoldNight(D1, 2, 0), new HoldNight(D2, 1, 1), new HoldNight(D3, 2, 0)), 1, 600L);

		assertFalse(r.acquired());
		assertEquals(D2, r.soldOutDate());
		assertEquals(0, sim.zcard(key(D1)), "D1 에 잔여 멤버 없어야 함(all-or-nothing)");
		assertEquals(0, sim.zcard(key(D2)));
		assertEquals(0, sim.zcard(key(D3)));
	}

	@Test
	@DisplayName("(2) 만료 멤버 sweep 후 ZCARD 기준 재고 계산 — 만료 전엔 차단, 만료 후엔 재획득")
	void expiredMembers_sweptBeforeCapacityCheck() {
		HoldNight n = new HoldNight(D1, 1, 0); // 용량 1

		assertTrue(store.acquire("hA", ROOM_SN, nights(n), 1, 600L).acquired()); // now=1000 → expireAt=1600
		assertFalse(store.acquire("hB", ROOM_SN, nights(n), 1, 600L).acquired(), "held=1 이라 차단");
		assertEquals(1, sim.zcard(key(D1)));

		sim.advanceTo(1_700L); // hA(만료 1600) 지남
		assertTrue(store.acquire("hB", ROOM_SN, nights(n), 1, 600L).acquired(), "만료 sweep 후 재획득 가능");
		assertEquals(1, sim.zcard(key(D1)), "hA 는 sweep 되고 hB 만 남음");
		assertFalse(sim.has(key(D1), "hA#1"));
		assertTrue(sim.has(key(D1), "hB#1"));
	}

	@Test
	@DisplayName("(3) release 멱등 + holdId 격리 — 같은 holdId 반복 해제해도 다른 holdId 는 삭제 안 됨")
	void release_idempotent_andIsolatedByHoldId() {
		HoldNight n = new HoldNight(D1, 2, 0); // 용량 2
		store.acquire("hA", ROOM_SN, nights(n), 1, 600L);
		store.acquire("hB", ROOM_SN, nights(n), 1, 600L);
		assertEquals(2, sim.zcard(key(D1)));

		store.release("hA", ROOM_SN, List.of(D1), 1);
		store.release("hA", ROOM_SN, List.of(D1), 1); // 이중 해제 — 오류/음수 없이 no-op

		assertEquals(1, sim.zcard(key(D1)));
		assertFalse(sim.has(key(D1), "hA#1"), "hA 는 제거됨");
		assertTrue(sim.has(key(D1), "hB#1"), "hB 는 건드리지 않음(격리)");
	}

	@Test
	@DisplayName("(4) holdId 별 독립 점유 — 서로 다른 holdId 는 용량 내에서 공존하고 각자만 해제됨(서비스는 시도마다 UUID 발급)")
	void distinctHoldIds_coexistAndReleaseIndependently() {
		// 계약: 서비스가 예약 시도마다 전역 유일 UUID holdId 를 발급한다(재사용 금지) → 저장소는 그 holdId 멤버만 격리 관리.
		HoldNight n = new HoldNight(D1, 3, 0);
		assertTrue(store.acquire("uuid-a", ROOM_SN, nights(n), 1, 600L).acquired());
		assertTrue(store.acquire("uuid-b", ROOM_SN, nights(n), 1, 600L).acquired());
		assertTrue(store.acquire("uuid-c", ROOM_SN, nights(n), 1, 600L).acquired());
		assertFalse(store.acquire("uuid-d", ROOM_SN, nights(n), 1, 600L).acquired(), "용량 3 초과 차단");
		assertEquals(3, sim.zcard(key(D1)));

		store.release("uuid-b", ROOM_SN, List.of(D1), 1);
		assertEquals(2, sim.zcard(key(D1)));
		assertTrue(sim.has(key(D1), "uuid-a#1"));
		assertFalse(sim.has(key(D1), "uuid-b#1"));
		assertTrue(sim.has(key(D1), "uuid-c#1"));
	}

	@Test
	@DisplayName("(동시성) 같은 밤 용량 C 에 N 스레드 동시 acquire → 정확히 C 개만 성공(EVAL 원자성)")
	void concurrentAcquire_exactlyCapacitySucceeds() throws Exception {
		int capacity = 5;
		HoldNight n = new HoldNight(D1, capacity, 0);
		int threads = 32;
		ExecutorService pool = Executors.newFixedThreadPool(threads);
		CountDownLatch startGate = new CountDownLatch(1);
		CountDownLatch doneGate = new CountDownLatch(threads);
		AtomicInteger winners = new AtomicInteger();
		try {
			for (int i = 0; i < threads; i++) {
				String holdId = "c-" + i;
				pool.submit(() -> {
					try {
						startGate.await();
						if (store.acquire(holdId, ROOM_SN, nights(n), 1, 600L).acquired()) {
							winners.incrementAndGet();
						}
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
					} finally {
						doneGate.countDown();
					}
				});
			}
			startGate.countDown();
			assertTrue(doneGate.await(5, TimeUnit.SECONDS), "모든 스레드 제한시간 내 완료");
		} finally {
			pool.shutdownNow();
		}
		assertEquals(capacity, winners.get());       // 정확히 용량만큼만 승인
		assertEquals(capacity, sim.zcard(key(D1)));   // 초과 점유 없음
	}

	private static String key(LocalDate d) {
		return RedisReservationHoldStore.key(ROOM_SN, d);
	}

	// ------------------------------------------------------------------
	// ACQUIRE/RELEASE Lua 의 인메모리 재현 — key→(member→score). exec 는 synchronized(EVAL 원자성 모델).
	// ARGV: [holdId, qty, window, buffer, (total,sold)*nights] / release: [holdId, qty].
	// ------------------------------------------------------------------
	private static final class ZsetSim {

		private final Map<String, Map<String, Long>> zsets = new java.util.HashMap<>();
		private final AtomicLong nowSec;

		ZsetSim(long now) {
			this.nowSec = new AtomicLong(now);
		}

		void advanceTo(long now) {
			nowSec.set(now);
		}

		synchronized long exec(List<String> keys, List<String> args) {
			return args.size() == 2 ? release(keys, args) : acquire(keys, args);
		}

		private long acquire(List<String> keys, List<String> args) {
			String holdId = args.get(0);
			int qty = Integer.parseInt(args.get(1));
			long window = Long.parseLong(args.get(2)); // buffer=args.get(3) 은 시뮬레이터 미사용(safety TTL)
			long now = nowSec.get();
			long expireAt = now + window;
			int nkeys = keys.size();
			// Phase 1: 전 밤 사전점검(sweep + 잔여재고). 부족하면 실패 밤 1-based index 반환(멤버 미추가).
			for (int i = 0; i < nkeys; i++) {
				String key = keys.get(i);
				sweep(key, now);
				int total = Integer.parseInt(args.get(4 + 2 * i));
				int sold = Integer.parseInt(args.get(5 + 2 * i));
				if (total - sold - zcard(key) < qty) {
					return i + 1;
				}
			}
			// Phase 2: 전 밤 통과 → 각 밤에 holdId#1..qty 추가.
			for (String key : keys) {
				Map<String, Long> z = zsets.computeIfAbsent(key, k -> new LinkedHashMap<>());
				for (int j = 1; j <= qty; j++) {
					z.put(holdId + "#" + j, expireAt);
				}
			}
			return 0L;
		}

		private long release(List<String> keys, List<String> args) {
			String holdId = args.get(0);
			int qty = Integer.parseInt(args.get(1));
			long removed = 0;
			for (String key : keys) {
				Map<String, Long> z = zsets.get(key);
				if (z == null) {
					continue;
				}
				for (int j = 1; j <= qty; j++) {
					if (z.remove(holdId + "#" + j) != null) {
						removed++;
					}
				}
			}
			return removed;
		}

		private void sweep(String key, long now) {
			Map<String, Long> z = zsets.get(key);
			if (z != null) {
				z.values().removeIf(score -> score <= now);
			}
		}

		int zcard(String key) {
			Map<String, Long> z = zsets.get(key);
			return z == null ? 0 : z.size();
		}

		boolean has(String key, String member) {
			Map<String, Long> z = zsets.get(key);
			return z != null && z.containsKey(member);
		}
	}
}
