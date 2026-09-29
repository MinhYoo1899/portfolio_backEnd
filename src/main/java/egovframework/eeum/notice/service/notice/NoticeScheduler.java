package egovframework.eeum.notice.service.notice;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 공지 예약발송 자동승격 스케줄러 — 1분 주기로 예약시각 도래분(SCHEDULED)을 게시중(PUBLISHED_ONGOING)으로 전이.
 * (화면정의서 §5 확정: 서버 배치 cron 1분 주기. 실 푸시는 스텁 — NOTIFY_SEND_YN 기록만.)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NoticeScheduler {

	private final NoticeService noticeService;

	/** 매분 0초(UTC) — 예약공지 자동승격. */
	@Scheduled(cron = "0 * * * * *", zone = "UTC")
	public void promoteScheduledNotices() {
		int promoted = noticeService.promoteScheduled();
		if (promoted > 0) {
			log.info("[NOTICE] promoted scheduled notices to published: {}", promoted);
		}
	}
}
