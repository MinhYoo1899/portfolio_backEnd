package egovframework.eeum.reservation.it;

import java.sql.Timestamp;
import java.time.Instant;

/**
 * H2 MySQL 호환 함수 보정 — 매퍼 SQL 이 쓰는 {@code UTC_TIMESTAMP(6)} 를 H2 가 모르므로
 * {@code CREATE ALIAS UTC_TIMESTAMP} 로 이 정적 메서드에 바인딩한다(reservation-it-schema.sql).
 *
 * <p>정밀도 인자(예: {@code 6})는 H2 가 그대로 넘겨주며, 여기서는 무시하고 현재 UTC 시각을 반환한다
 * (감사시각 정합만 검증하면 되므로 정밀도 자체는 무관). 운영(MySQL) 동작과 별개인 테스트 전용 보정이다.
 */
public final class H2Compat {

	private H2Compat() {
	}

	/** {@code UTC_TIMESTAMP(precision)} 매핑 — 정밀도 인자는 무시하고 현재 UTC 시각. */
	public static Timestamp utcTimestamp(int precision) {
		return Timestamp.from(Instant.now());
	}
}
