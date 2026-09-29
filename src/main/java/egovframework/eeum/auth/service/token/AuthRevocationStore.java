package egovframework.eeum.auth.service.token;

/**
 * 토큰 세대(generation) 저장소 — access/refresh 의 즉시·일괄 무효화 (Redis, TokenSessionStore 와 짝).
 *
 * <p>무상태(stateless) access 토큰은 만료(기본 30분) 전까지 서버가 막을 수 없고, refresh 회전 경합이 있으면
 * {@code revokeAll}(SCAN+DEL)만으로는 "전체 세션 즉시 무효화"가 깨질 수 있다(SCAN 사이 새 jti 저장).
 * 그래서 사용자별 단조 증가 세대 카운터를 둔다 — 모든 토큰은 발급 시점의 세대를 claim 으로 갖고,
 * 비밀번호 변경·탈퇴 시 세대를 올리면(INCR, 원자적) 그 이전 세대의 access·refresh 가 모두 거부된다.
 * (시간 기반 워터마크의 초 단위 경계·downgrade·refresh 경합 문제를 한 번에 제거 — codex 교차검토 MUST.)
 *
 * <p>가용성: 조회 실패(Redis 장애)는 세대 0(=무효화 없음)으로 fail-open 한다 — 미적용 시 최대 노출은
 * access 만료까지(refresh 는 세션스토어에서 별도 폐기)로, 전체 인증 마비보다 위험이 작다.
 */
public interface AuthRevocationStore {

	/** 현재 토큰 세대 — 토큰 발급 시 claim 으로 박는다. 미설정(또는 조회 장애)이면 0. */
	long currentGeneration(long userSn);

	/** 세대 증가(원자적 INCR) — 비밀번호 변경·탈퇴 시. 이후 옛 세대 토큰은 검증에서 거부된다. */
	void bumpGeneration(long userSn);
}
