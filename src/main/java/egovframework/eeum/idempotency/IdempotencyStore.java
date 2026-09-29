package egovframework.eeum.idempotency;

import java.time.Duration;
import java.util.Optional;

/**
 * 멱등 레코드 저장소. 선점(claim)→완료 승격(promote) 또는 해제(release), 히트 시 조회(find).
 *
 * <p>인프라 장애(연결 불가·직렬화 실패)는 {@link IdempotencyInfraException} 로 감싸 던진다
 * — 호출측(필터)이 fail-closed 로 처리할 수 있게(불확실할 때 통과시키지 않음).
 */
public interface IdempotencyStore {

	/**
	 * 키 선점 — PENDING 레코드를 setIfAbsent(TTL) 로 기록. 획득하면 true(이 요청이 소유), 이미 있으면 false.
	 */
	boolean claim(String key, String bodyHash, Duration pendingTtl);

	/** 키 조회 — 없으면 empty(만료 경합). */
	Optional<IdempotencyRecord> find(String key);

	/** 완료 승격 — DONE 레코드로 덮어쓰고 TTL 을 doneTtl 로 재설정. */
	void promote(String key, IdempotencyRecord doneRecord, Duration doneTtl);

	/** 선점 해제 — 실패(비2xx/예외) 시 재시도가 가능하도록 키 삭제. */
	void release(String key);
}
