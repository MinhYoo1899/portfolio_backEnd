package egovframework.eeum.auth.service.token;

import java.time.Duration;

/**
 * refresh 토큰 세션 저장소 추상화 (D8 — Redis 용도별 컴포넌트).
 *
 * <p>저장 단위는 (사용자, jti). 토큰 원문은 저장하지 않는다 — 서명 검증은 JWT 가, 유효 세션 여부는 이 저장소가 책임.
 * 회전(rotation): refresh 사용 시 {@link #consume}으로 1회 소비 후 새 jti 를 저장한다.
 */
public interface TokenSessionStore {

	/** refresh 세션 저장 (TTL = refresh 만료시간). */
	void save(long userSn, String jti, Duration ttl);

	/**
	 * refresh 세션 1회 소비(원자적 삭제). 존재했으면 true.
	 * false = 만료/회수/이미 사용(재사용 공격 의심 — 호출측에서 {@link #revokeAll} 권장).
	 */
	boolean consume(long userSn, String jti);

	/** 특정 세션 회수(로그아웃). */
	void revoke(long userSn, String jti);

	/** 사용자의 모든 refresh 세션 회수(전체 로그아웃/재사용 탐지/계정 정지). */
	void revokeAll(long userSn);

	/**
	 * 멱등 회전(grace window) — refresh 회전 레이스로 정상 사용자가 로그아웃되는 문제 방어(RFC 9700 + Okta grace period 모델).
	 *
	 * <ul>
	 *   <li>oldJti 가 active → 회전: successor 활성화 + oldJti→successor grace 매핑(graceTtl) + oldJti 폐기. {@code ROTATED}</li>
	 *   <li>oldJti 가 active 아님 + grace 창 내 → 직전 발급한 successor 디스크립터 반환(재회전 금지). {@code GRACE}</li>
	 *   <li>둘 다 아님(창 밖 재사용/만료) → {@code REUSE} (호출측이 회수 처리)</li>
	 * </ul>
	 * DEL(old)+SET(active/grace) 은 단일 원자연산(Lua)으로 처리해 그 사이 레이스를 없앤다.
	 */
	RotationResult rotateOrGrace(long userSn, String oldJti, RefreshDescriptor successor, Duration refreshTtl,
			Duration graceTtl);

	enum RotationOutcome {
		ROTATED, GRACE, REUSE
	}

	/** 회전된 refresh 의 successor 식별(grace 재요청 시 동일 토큰 재서명에 필요). */
	record RefreshDescriptor(String jti, String chnlCd, long generation, long refreshExpEpochMs) {
	}

	/** 회전 결과. {@code GRACE}/{@code ROTATED} 이면 successor 채워짐(REUSE 면 null). */
	record RotationResult(RotationOutcome outcome, RefreshDescriptor successor) {
	}
}
