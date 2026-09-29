package egovframework.eeum.idempotency;

/**
 * 멱등 저장소 인프라 장애(Redis 연결 불가·직렬화 실패 등)를 나타내는 예외.
 *
 * <p>{@link IdempotencyStore} 구현이 던지며, 필터는 이를 잡아 fail-closed(503 IDEMPOTENCY_UNAVAILABLE)로 응답한다
 * — 저장소가 불확실할 때 요청을 통과시켜 중복 생성이 나는 것을 막는다.
 */
public class IdempotencyInfraException extends RuntimeException {

	public IdempotencyInfraException(String message, Throwable cause) {
		super(message, cause);
	}
}
