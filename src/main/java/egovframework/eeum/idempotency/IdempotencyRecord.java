package egovframework.eeum.idempotency;

import java.util.Map;

/**
 * 멱등 레코드 — Redis 값(JSON). 선점(PENDING) 시 본문해시만, 완료(DONE) 시 응답 스냅샷까지 담는다.
 *
 * @param state        상태 ({@link #STATE_PENDING}/{@link #STATE_DONE})
 * @param bodyHash     요청 원본 본문의 SHA-256 hex (키 재사용 판별용 — 값에 저장하고 히트 시 비교)
 * @param httpStatus   완료 응답 상태코드 (PENDING 이면 null)
 * @param contentType  완료 응답 Content-Type (PENDING 이면 null)
 * @param headers      재생 대상 응답 헤더(allowlist 만; Set-Cookie/Authorization 등 금지)
 * @param responseBody 완료 응답 본문(직렬화된 그대로)
 * @param createdAt    생성 시각(epoch millis — 관측/디버깅용)
 */
public record IdempotencyRecord(
		String state,
		String bodyHash,
		Integer httpStatus,
		String contentType,
		Map<String, String> headers,
		String responseBody,
		long createdAt) {

	/** 선점 상태 — 소유 요청이 처리 중. */
	public static final String STATE_PENDING = "PENDING";
	/** 완료 상태 — 응답 스냅샷 보유(재생 가능). */
	public static final String STATE_DONE = "DONE";

	public IdempotencyRecord {
		// 역직렬화·팩토리 양쪽에서 headers null 방지(재생 시 NPE 차단).
		headers = headers == null ? Map.of() : Map.copyOf(headers);
	}

	/** 선점(PENDING) 레코드 — 본문해시만 담는다. */
	public static IdempotencyRecord pending(String bodyHash) {
		return new IdempotencyRecord(STATE_PENDING, bodyHash, null, null, Map.of(), null,
				System.currentTimeMillis());
	}

	/** 완료(DONE) 레코드 — 응답 스냅샷을 담는다. */
	public static IdempotencyRecord done(String bodyHash, int httpStatus, String contentType,
			Map<String, String> headers, String responseBody) {
		return new IdempotencyRecord(STATE_DONE, bodyHash, httpStatus, contentType, headers, responseBody,
				System.currentTimeMillis());
	}
}
