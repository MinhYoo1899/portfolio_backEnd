package egovframework.eeum.reservation.service.hold;

/**
 * 소프트홀드 저장소(Redis) 인프라 장애 전용 예외 — 연결 불가/명령 타임아웃/스크립트 오류 등을 감싼다.
 *
 * <p>이 예외는 <b>fail-open</b> 신호다: 서비스는 acquire 에서 이 예외를 잡으면 소프트홀드 없이 DB insert 로 진행한다
 * (NEVER fail-closed — 재고 최종확정은 슬라이스③ DB 조건부 UPDATE 가 담당하므로 홀드 부재가 예약을 막지 않는다).
 * 원인 예외(RedisException/DataAccessException 등)는 로그 분석용으로 보존하되 사용자에게 노출하지 않는다.
 */
public class ReservationHoldException extends RuntimeException {

	public ReservationHoldException(String message, Throwable cause) {
		super(message, cause);
	}
}
