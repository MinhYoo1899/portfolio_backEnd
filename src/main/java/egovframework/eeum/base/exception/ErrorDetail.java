package egovframework.eeum.base.exception;

/**
 * 오류 응답 envelope 의 {@code result} 에 실릴 구조화 데이터를 제공하는 예외용 인터페이스.
 *
 * <p>{@code GlobalExceptionHandler} 가 {@link BusinessException} 을 처리할 때, 예외가 이 인터페이스를 구현하면
 * {@link #detail()} 반환값을 {@code result} 로 함께 내려준다(미구현이면 {@code result=null}, 기존 동작 유지).
 *
 * <p><b>base 격리 계약(D15):</b> base 는 구체 payload 타입을 알지 못하고 {@code Object} 로만 다룬다 —
 * 실제 payload(도메인 DTO)는 각 도메인이 보유하고 Jackson 이 직렬화한다.
 */
public interface ErrorDetail {

	/** 오류 응답 {@code result} 에 실을 구조화 데이터(없으면 null). */
	Object detail();
}
