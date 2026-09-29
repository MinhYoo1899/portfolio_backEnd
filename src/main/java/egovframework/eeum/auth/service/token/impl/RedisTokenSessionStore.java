package egovframework.eeum.auth.service.token.impl;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import egovframework.eeum.auth.service.token.TokenSessionStore;

/**
 * Redis 기반 refresh 세션 저장소. 키: {@code auth:refresh:{userSn}:{jti}} (TTL = refresh 만료).
 */
@Component
public class RedisTokenSessionStore implements TokenSessionStore {

	private static final String KEY_PREFIX = "auth:refresh:";

	private final StringRedisTemplate redisTemplate;

	public RedisTokenSessionStore(StringRedisTemplate redisTemplate) {
		this.redisTemplate = redisTemplate;
	}

	private static String key(long userSn, String jti) {
		return KEY_PREFIX + userSn + ":" + jti;
	}

	@Override
	public void save(long userSn, String jti, Duration ttl) {
		redisTemplate.opsForValue().set(key(userSn, jti), "1", ttl);
	}

	@Override
	public boolean consume(long userSn, String jti) {
		// DEL 은 단일 키 원자 연산 — 동시에 두 번 소비될 수 없다(회전 경쟁 방지)
		return Boolean.TRUE.equals(redisTemplate.delete(key(userSn, jti)));
	}

	@Override
	public void revoke(long userSn, String jti) {
		redisTemplate.delete(key(userSn, jti));
	}

	@Override
	public void revokeAll(long userSn) {
		Set<String> keys = new HashSet<>();
		scanInto(keys, KEY_PREFIX + userSn + ":*");      // active 세션
		scanInto(keys, GRACE_PREFIX + userSn + ":*");    // grace 매핑(회수 시 같이 제거 — codex)
		if (!keys.isEmpty()) {
			redisTemplate.delete(keys);
		}
	}

	private void scanInto(Set<String> keys, String pattern) {
		ScanOptions options = ScanOptions.scanOptions().match(pattern).count(100).build();
		try (Cursor<String> cursor = redisTemplate.scan(options)) {
			cursor.forEachRemaining(keys::add);
		}
	}

	// ── 멱등 회전(grace window) ──
	private static final String GRACE_PREFIX = "auth:refresh:grace:";

	private static String graceKey(long userSn, String oldJti) {
		return GRACE_PREFIX + userSn + ":" + oldJti;
	}

	/**
	 * 원자적 회전-또는-grace. KEYS[1]=active(old), KEYS[2]=active(new), KEYS[3]=grace(old).
	 * ARGV[1]=successor 직렬화, ARGV[2]=refresh TTL초, ARGV[3]=grace TTL초.
	 */
	private static final RedisScript<String> ROTATE_OR_GRACE = new DefaultRedisScript<>(
			"if redis.call('EXISTS', KEYS[1]) == 1 then "
					+ "redis.call('SET', KEYS[2], '1', 'EX', tonumber(ARGV[2])); "
					+ "redis.call('SET', KEYS[3], ARGV[1], 'EX', tonumber(ARGV[3])); "
					+ "redis.call('DEL', KEYS[1]); "
					+ "return 'ROTATED'; "
					+ "elseif redis.call('EXISTS', KEYS[3]) == 1 then "
					+ "return 'GRACE:' .. redis.call('GET', KEYS[3]); "
					+ "else return 'REUSE'; end",
			String.class);

	@Override
	public RotationResult rotateOrGrace(long userSn, String oldJti, RefreshDescriptor successor, Duration refreshTtl,
			Duration graceTtl) {
		String result = redisTemplate.execute(ROTATE_OR_GRACE,
				List.of(key(userSn, oldJti), key(userSn, successor.jti()), graceKey(userSn, oldJti)),
				serialize(successor), String.valueOf(refreshTtl.toSeconds()), String.valueOf(graceTtl.toSeconds()));

		if (result == null || result.equals("REUSE")) {
			return new RotationResult(RotationOutcome.REUSE, null);
		}
		if (result.equals("ROTATED")) {
			return new RotationResult(RotationOutcome.ROTATED, successor);
		}
		return new RotationResult(RotationOutcome.GRACE, deserialize(result.substring("GRACE:".length())));
	}

	private static String serialize(RefreshDescriptor d) {
		return d.jti() + "|" + d.chnlCd() + "|" + d.generation() + "|" + d.refreshExpEpochMs();
	}

	private static RefreshDescriptor deserialize(String s) {
		String[] p = s.split("\\|", 4);
		return new RefreshDescriptor(p[0], p[1], Long.parseLong(p[2]), Long.parseLong(p[3]));
	}
}
