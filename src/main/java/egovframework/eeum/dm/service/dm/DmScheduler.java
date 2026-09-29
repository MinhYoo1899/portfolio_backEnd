package egovframework.eeum.dm.service.dm;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 예약 DM 자동발송 스케줄러 — 1분 주기로 예약시각 도래분(SCHEDULED)을 SENT 로 전이(스텁 발송).
 * (실 전송은 알림톡/SMS 벤더 미선정 — SENT 기록만.)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DmScheduler {

	private final DmService dmService;

	/** 매분 0초(UTC) — 예약 DM 자동발송. */
	@Scheduled(cron = "0 * * * * *", zone = "UTC")
	public void sendScheduledDms() {
		int sent = dmService.promoteScheduled();
		if (sent > 0) {
			log.info("[DM] sent scheduled DMs (stub): {}", sent);
		}
	}
}
