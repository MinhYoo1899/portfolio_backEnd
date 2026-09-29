package egovframework.eeum.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.time.Month;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import egovframework.eeum.notification.service.notification.dao.OperatorNotificationMapper;
import egovframework.eeum.notification.service.notification.impl.NotificationRetentionJob;

/**
 * 90일 보관 cleanup job 검증 — 기준시각 - 90일 초과분 삭제 위임 + 삭제 건수 반환(내용 로그 없음은 구현 계약).
 */
class NotificationRetentionJobVerification {

	private final OperatorNotificationMapper mapper = mock(OperatorNotificationMapper.class);
	private final NotificationRetentionJob job = new NotificationRetentionJob(mapper);

	@Test
	@DisplayName("purge — cutoff = 기준시각 - 90일로 hard delete 위임, 삭제 건수 반환")
	void purgesOlderThan90Days() {
		LocalDateTime now = LocalDateTime.of(2026, Month.JULY, 9, 18, 0, 0);
		when(mapper.deleteOlderThan(now.minusDays(90))).thenReturn(4);

		int deleted = job.purge(now);

		assertEquals(4, deleted);
		verify(mapper).deleteOlderThan(now.minusDays(90)); // 2026-04-10T18:00
	}
}
