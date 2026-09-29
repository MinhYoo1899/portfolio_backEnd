package egovframework.eeum.reservation.service.hold.impl;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import egovframework.eeum.reservation.service.hold.ReservationHoldException;
import egovframework.eeum.reservation.service.hold.ReservationHoldStore;
import jakarta.annotation.PostConstruct;

/**
 * Redis ZSET 기반 예약 소프트홀드 저장소.
 *
 * <p>키 = {@code resv:hold:{roomSn}:{stayDate}}(ISO), 값 = ZSET(member = {@code holdId#n}(n=1..qty), score = 만료 epoch초).
 * 한 숙박일에 여러 홀드/여러 객실이 각자의 멤버로 공존하고, ZCARD 로 현재 임시점유 수를 센다.
 *
 * <p><b>토폴로지 = 단일 Redis</b> — 여러 숙박일 키를 하나의 EVAL 로 원자 처리한다(다박 all-or-nothing).
 * ⚠ Redis Cluster 로 전환하면 멀티키 EVAL 은 같은 슬롯을 요구하므로 키에 hash-tag 를 넣어야 한다
 *   (예: {@code resv:hold:{<roomSn>}:<stayDate>} — 한 객실의 모든 숙박일이 같은 슬롯). 현재는 단일 노드라 미적용.
 *
 * <p>보안/원자성:
 * <ul>
 *   <li>서버 시각을 EVAL 내부에서 {@code redis.call('TIME')} 로 <b>한 번만</b> 읽어 now/expireAt/ttl 을 파생(시계 이중읽기 금지).</li>
 *   <li>Phase 1(전 밤 사전점검) — 만료 멤버 sweep 후 남은 재고가 부족하면 아무것도 추가하지 않고 실패 밤 index 반환.</li>
 *   <li>Phase 2(전 밤 통과 시에만) — 각 밤에 ZADD + EXPIRE. TTL = window + buffer.</li>
 *   <li><b>TTL 불변식</b>: safety TTL(=window+buffer)은 살아있는 멤버 최대 score(=expireAt)보다 반드시 길어야 한다
 *       (buffer 는 시계 skew + sweep 지연을 흡수 — 기본 30s, {@code >> } skew). 그래야 EXPIRE 가 유효 멤버를 조기 파기하지 않는다.</li>
 * </ul>
 * 인프라 장애(연결/타임아웃/스크립트)는 {@link ReservationHoldException} 으로 감싸 던진다(서비스 fail-open).
 */
@Component
public class RedisReservationHoldStore implements ReservationHoldStore {

	/** 키 접두 — {@code resv:hold:{roomSn}:{stayDate}}. (Cluster 전환 시 roomSn 을 hash-tag 로 감쌀 것) */
	private static final String KEY_PREFIX = "resv:hold:";
	/** 멤버 구분자 — {@code holdId#n}. */
	private static final String MEMBER_SEP = "#";

	/**
	 * 획득(원자, 다박 all-or-nothing) — KEYS = 전 숙박일 키.
	 * ARGV[1]=holdId, [2]=qty, [3]=windowSeconds, [4]=bufferSeconds, 이후 밤별 쌍: [3+2i]=totalQty_i, [4+2i]=soldQty_i.
     * 반환: 0=전 밤 획득 성공, i(&gt;=1)=재고 부족으로 실패한 밤의 1-based index(그 시점까지 아무 멤버도 추가 안 함).
	 */
	private static final RedisScript<Long> ACQUIRE_SCRIPT = new DefaultRedisScript<>(
			"local holdId = ARGV[1] "
					+ "local qty = tonumber(ARGV[2]) "
					+ "local windowSeconds = tonumber(ARGV[3]) "
					+ "local bufferSeconds = tonumber(ARGV[4]) "
					+ "local t = redis.call('TIME') "                         // 서버 시각 1회만 읽음
					+ "local now = tonumber(t[1]) "
					+ "local expireAt = now + windowSeconds "
					+ "local ttl = windowSeconds + bufferSeconds "
					+ "local n = #KEYS "
					// Phase 1: 전 밤 사전점검(만료 sweep + 잔여재고 확인) — 부족하면 실패 index 반환(멤버 미추가)
					+ "for i = 1, n do "
					+ "  local key = KEYS[i] "
					+ "  redis.call('ZREMRANGEBYSCORE', key, '-inf', now) "     // 만료 멤버 청소
					+ "  local total = tonumber(ARGV[3 + 2 * i]) "
					+ "  local sold = tonumber(ARGV[4 + 2 * i]) "
					+ "  local held = redis.call('ZCARD', key) "
					+ "  if (total - sold - held) < qty then return i end "
					+ "end "
					// Phase 2: 전 밤 통과 → 각 밤에 멤버 추가 + safety TTL
					+ "for i = 1, n do "
					+ "  local key = KEYS[i] "
					+ "  for j = 1, qty do redis.call('ZADD', key, expireAt, holdId .. '" + MEMBER_SEP + "' .. j) end "
					+ "  redis.call('EXPIRE', key, ttl) "
					+ "end "
					+ "return 0",
			Long.class);

	/**
	 * 해제(멱등) — KEYS = 숙박일 키, ARGV[1]=holdId, [2]=qty. 각 밤에서 {@code holdId#1..qty} 만 ZREM(없으면 no-op). 반환: 제거 수.
	 */
	private static final RedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>(
			"local holdId = ARGV[1] "
					+ "local qty = tonumber(ARGV[2]) "
					+ "local removed = 0 "
					+ "for i = 1, #KEYS do "
					+ "  for j = 1, qty do "
					+ "    removed = removed + redis.call('ZREM', KEYS[i], holdId .. '" + MEMBER_SEP + "' .. j) "
					+ "  end "
					+ "end "
					+ "return removed",
			Long.class);

	private final StringRedisTemplate redis;

	/** 홀드 유효창(초) — 결제 착수까지 임시점유 유지. 기본 10분. */
	@Value("${eeum.reservation.hold.window-seconds:600}")
	private long windowSeconds;
	/** safety TTL 여유(초) — 시계 skew + sweep 지연 흡수용. window 와 합쳐 EXPIRE. 기본 30s. */
	@Value("${eeum.reservation.hold.buffer-seconds:30}")
	private long bufferSeconds;

	public RedisReservationHoldStore(StringRedisTemplate redis) {
		this.redis = redis;
	}

	/** 설정값 양수 검증 — 오설정(0/음수) 시 홀드가 조용히 오작동하지 않게 기동 거부(email/idempotency store 기조). */
	@PostConstruct
	void validateConfig() {
		if (windowSeconds <= 0 || bufferSeconds <= 0) {
			throw new IllegalStateException("예약 홀드 설정값은 모두 양수여야 합니다 — "
					+ "window-seconds=" + windowSeconds + ", buffer-seconds=" + bufferSeconds);
		}
	}

	@Override
	public long windowSeconds() {
		return windowSeconds;
	}

	@Override
	public HoldResult acquire(String holdId, long roomSn, List<HoldNight> nights, int qty, long windowSeconds) {
		List<String> keys = new ArrayList<>(nights.size());
		// ARGV: [holdId, qty, window, buffer, (total,sold) * nights]
		List<String> args = new ArrayList<>(4 + nights.size() * 2);
		args.add(holdId);
		args.add(String.valueOf(qty));
		args.add(String.valueOf(windowSeconds));
		args.add(String.valueOf(bufferSeconds));
		for (HoldNight night : nights) {
			keys.add(key(roomSn, night.stayDate()));
			args.add(String.valueOf(night.totalQty()));
			args.add(String.valueOf(night.soldQty()));
		}
		Long result = eval(ACQUIRE_SCRIPT, keys, args, "acquire");
		long r = result == null ? -1L : result;
		if (r == 0L) {
			return new HoldResult(true, null);
		}
		// r = 실패 밤 1-based index → 매진 숙박일. (음수/예상밖도 안전하게 미획득 처리)
		LocalDate soldOutDate = (r >= 1 && r <= nights.size()) ? nights.get((int) r - 1).stayDate() : null;
		return new HoldResult(false, soldOutDate);
	}

	@Override
	public void release(String holdId, long roomSn, List<LocalDate> stayDates, int qty) {
		if (stayDates == null || stayDates.isEmpty()) {
			return;
		}
		List<String> keys = new ArrayList<>(stayDates.size());
		for (LocalDate d : stayDates) {
			keys.add(key(roomSn, d));
		}
		eval(RELEASE_SCRIPT, keys, List.of(holdId, String.valueOf(qty)), "release");
	}

	/** EVAL 공통 — Redis 인프라 장애(연결/타임아웃/스크립트 등 모든 RuntimeException)를 감싸 fail-open 신호로 던진다(NEVER fail-closed). */
	private Long eval(RedisScript<Long> script, List<String> keys, List<String> args, String op) {
		try {
			return redis.execute(script, keys, args.toArray());
		} catch (RuntimeException e) {
			throw new ReservationHoldException("예약 홀드 저장소(" + op + ") 접근 실패", e);
		}
	}

	/**
	 * 홀드 키 — {@code resv:hold:{roomSn}:{stayDate}}(ISO). (검증에서 동일 키를 재구성하므로 package-private static)
	 * ⚠ Cluster 전환 시 멀티키 EVAL 을 위해 {@code resv:hold:{<roomSn>}:<stayDate>} hash-tag 형태로 바꿀 것.
	 */
	static String key(long roomSn, LocalDate stayDate) {
		return KEY_PREFIX + roomSn + ":" + stayDate;
	}
}
