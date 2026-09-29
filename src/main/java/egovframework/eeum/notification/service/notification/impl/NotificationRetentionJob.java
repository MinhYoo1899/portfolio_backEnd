package egovframework.eeum.notification.service.notification.impl;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import egovframework.eeum.notification.service.notification.dao.OperatorNotificationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 운영자 알림 90일 보관 — UTC 기준 일 1회, 90일 초과분을 hard delete. (@EnableScheduling 은 애플리케이션에 이미 설정)
 *
 * <p>삭제 <b>건수만</b> 로그로 남긴다 — 제목/본문/수신자 등 알림 내용은 로그에 남기지 않는다. 조회/미읽음수의 90일 창은
 * 매퍼에서 별도 유지(이 job 과 무관하게 창 밖은 애초에 안 보임). 단일 DELETE 라 autocommit 원자성으로 충분.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationRetentionJob {

	/** 보관 기간(일). */
	static final int RETENTION_DAYS = 90;

	private final OperatorNotificationMapper notificationMapper;

	/** 매일 UTC 18:00 (KST 03:00) — 90일 초과 알림 정리. */
	@Scheduled(cron = "0 0 18 * * *", zone = "UTC")
	public void purgeExpired() {
		purge(LocalDateTime.now(ZoneOffset.UTC));
	}

	/**
	 * 기준시각을 받아 90일 초과분을 삭제한다(테스트 가능하도록 분리). 삭제 건수만 반환/로그.
	 *
	 * @param now 기준시각(UTC) — cutoff = now - 90일
	 * @return 삭제 건수
	 */
	public int purge(LocalDateTime now) {
		int deleted = notificationMapper.deleteOlderThan(now.minusDays(RETENTION_DAYS));
		log.info("[NOTI-RETENTION] purged {} notifications older than {} days (UTC)", deleted, RETENTION_DAYS);
		return deleted;
	}
}
