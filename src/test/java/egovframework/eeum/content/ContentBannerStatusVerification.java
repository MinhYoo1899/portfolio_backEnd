package egovframework.eeum.content;

import static egovframework.eeum.content.service.banner.ContentBannerStatus.ENDED;
import static egovframework.eeum.content.service.banner.ContentBannerStatus.EXPOSED;
import static egovframework.eeum.content.service.banner.ContentBannerStatus.HIDDEN;
import static egovframework.eeum.content.service.banner.ContentBannerStatus.resolve;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 콘텐츠 상태 산출(backend 전담) 검증 — PM 확정 규칙(KST 기준일, SCHEDULED 없음).
 */
class ContentBannerStatusVerification {

	private static final LocalDate TODAY = LocalDate.of(2026, 7, 7);

	@Test
	@DisplayName("토글 OFF → HIDDEN")
	void inactive_hidden() {
		assertEquals(HIDDEN, resolve(false, null, null, TODAY));
		assertEquals(HIDDEN, resolve(false, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31), TODAY));
	}

	@Test
	@DisplayName("활성 + 기간 내(또는 무기한) → EXPOSED")
	void active_withinPeriod_exposed() {
		assertEquals(EXPOSED, resolve(true, null, null, TODAY));
		assertEquals(EXPOSED, resolve(true, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31), TODAY));
	}

	@Test
	@DisplayName("활성 + 종료일 지남 → ENDED")
	void active_pastEnd_ended() {
		assertEquals(ENDED, resolve(true, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 6), TODAY));
	}

	@Test
	@DisplayName("활성 + 시작일 미래 → HIDDEN (SCHEDULED 없음)")
	void active_futureStart_hidden() {
		assertEquals(HIDDEN, resolve(true, LocalDate.of(2026, 7, 10), LocalDate.of(2026, 7, 31), TODAY));
	}

	@Test
	@DisplayName("종료일 == 오늘 → EXPOSED (당일까지 노출, ENDED 아님)")
	void active_endEqualsToday_exposed() {
		assertEquals(EXPOSED, resolve(true, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 7), TODAY));
	}

	@Test
	@DisplayName("시작일 == 오늘 → EXPOSED")
	void active_startEqualsToday_exposed() {
		assertEquals(EXPOSED, resolve(true, LocalDate.of(2026, 7, 7), LocalDate.of(2026, 7, 31), TODAY));
	}
}
