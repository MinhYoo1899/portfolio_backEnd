package egovframework.eeum.content.service.banner;

import java.time.LocalDate;

/**
 * 콘텐츠 노출 상태 — backend 전담 산출(저장 컬럼 아님, 프론트 임의판단 금지 · 화면정의서 v3 S158).
 *
 * <p>산출 규칙(KST 기준일, PM 확정 2026-07-07):
 * <pre>
 *   isActive=false                              -> HIDDEN
 *   isActive=true AND end != null AND today>end -> ENDED
 *   isActive=true AND bgn != null AND today&lt;bgn -> HIDDEN  (미래 시작)
 *   그 외                                        -> EXPOSED
 * </pre>
 * endDate == today 는 당일까지 노출 → EXPOSED(ENDED 아님). SCHEDULED 상태는 두지 않는다.
 */
public enum ContentBannerStatus {

	/** 노출중. */
	EXPOSED,
	/** 미노출(토글 OFF 또는 시작 전). */
	HIDDEN,
	/** 종료(노출기간 지남). */
	ENDED;

	/**
	 * 노출여부 + 노출기간 + 기준일로 상태를 산출한다(순수 함수 — 테스트 용이).
	 *
	 * @param active 노출여부(EXPOSE_YN='Y')
	 * @param bgn    노출시작일(null=제한 없음)
	 * @param end    노출종료일(null=제한 없음)
	 * @param today  기준일(KST)
	 */
	public static ContentBannerStatus resolve(boolean active, LocalDate bgn, LocalDate end, LocalDate today) {
		if (!active) {
			return HIDDEN;
		}
		if (end != null && today.isAfter(end)) {
			return ENDED;
		}
		if (bgn != null && today.isBefore(bgn)) {
			return HIDDEN;
		}
		return EXPOSED;
	}
}
