package egovframework.eeum.idempotency;

import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * 멱등키 처리 설정. (RedisEmailVerificationChallengeStore/RedisOperatorSetupTokenStore 의 @Value + @PostConstruct 방식)
 *
 * <p>guarded-paths/replay-headers 는 콤마 구분(@Value → String[] 자동 변환) 후 @PostConstruct 에서 정규화한다.
 * 오설정(0/음수 TTL, 빈 guarded-paths)은 기동 거부(IllegalStateException)로 조용한 오작동을 막는다.
 */
@Component
public class IdempotencyProperties {

	/** 선점(PENDING) TTL(초) — 게이트웨이/서버 요청 타임아웃 이상이어야 함(nginx 기본 proxy_read_timeout 60s 커버). */
	@Value("${eeum.idempotency.pending-ttl-seconds:60}")
	private long pendingTtlSeconds;
	/** 완료(DONE) TTL(초) — 재생 보관기간(기본 24h). */
	@Value("${eeum.idempotency.done-ttl-seconds:86400}")
	private long doneTtlSeconds;
	/** 보호 대상 경로(정확 일치). 이 목록 + POST + 헤더 + 인증 principal 이 모두 성립할 때만 동작. */
	@Value("${eeum.idempotency.guarded-paths:/houses/insert,/operator/houses/insert,/rooms/insert,/operator/rooms/insert,/reservations}")
	private String[] guardedPathsRaw;

	/**
	 * 필수 보호 경로(하드코딩) — 생성(insert)·예약 계열 중복 방어의 최소 집합. 설정이 이 집합을 전부 포함하지 않으면 기동 거부.
	 * (오설정으로 예약/등록 API 의 멱등 보호가 조용히 빠지는 것을 차단 — codex 안전 기본값 기조)
	 */
	private static final Set<String> REQUIRED_GUARDED_PATHS = Set.of(
			"/houses/insert", "/operator/houses/insert", "/rooms/insert", "/operator/rooms/insert", "/reservations");
	/** 재생 대상 응답 헤더 allowlist. Content-Type 은 별도 처리 — Set-Cookie/Authorization/보안헤더는 절대 재생 금지. */
	@Value("${eeum.idempotency.replay-headers:Location}")
	private String[] replayHeadersRaw;

	private Duration pendingTtl;
	private Duration doneTtl;
	private Set<String> guardedPaths;
	private List<String> replayHeaders;

	/** 설정값 검증 — 양수 TTL·비지 않은 guarded-paths 를 강제(오설정 시 기동 거부). */
	@PostConstruct
	void validateConfig() {
		if (pendingTtlSeconds <= 0 || doneTtlSeconds <= 0) {
			throw new IllegalStateException("멱등 TTL 설정값은 모두 양수여야 합니다 — "
					+ "pending-ttl-seconds=" + pendingTtlSeconds + ", done-ttl-seconds=" + doneTtlSeconds);
		}
		this.pendingTtl = Duration.ofSeconds(pendingTtlSeconds);
		this.doneTtl = Duration.ofSeconds(doneTtlSeconds);
		this.guardedPaths = normalize(guardedPathsRaw).stream().collect(Collectors.toCollection(LinkedHashSet::new));
		this.replayHeaders = normalize(replayHeadersRaw);
		if (guardedPaths.isEmpty()) {
			throw new IllegalStateException("멱등 guarded-paths 는 최소 1개 이상이어야 합니다.");
		}
		// 필수 경로 subset 강제 — 오버라이드로 예약/등록 API 의 멱등 보호가 빠지면 기동 거부(조용한 오작동 차단).
		if (!guardedPaths.containsAll(REQUIRED_GUARDED_PATHS)) {
			throw new IllegalStateException("멱등 guarded-paths 는 필수 경로를 모두 포함해야 합니다 — required="
					+ REQUIRED_GUARDED_PATHS + ", configured=" + guardedPaths);
		}
	}

	/** 공백 제거 후 빈 항목을 걸러낸 목록. */
	private static List<String> normalize(String[] raw) {
		if (raw == null) {
			return List.of();
		}
		return Arrays.stream(raw)
				.map(String::trim)
				.filter(s -> !s.isEmpty())
				.toList();
	}

	public Duration pendingTtl() {
		return pendingTtl;
	}

	public Duration doneTtl() {
		return doneTtl;
	}

	/** 해당 경로가 보호 대상인지(정확 일치). */
	public boolean isGuarded(String path) {
		return guardedPaths.contains(path);
	}

	/** 재생 대상 응답 헤더 allowlist. */
	public List<String> replayHeaders() {
		return replayHeaders;
	}
}
