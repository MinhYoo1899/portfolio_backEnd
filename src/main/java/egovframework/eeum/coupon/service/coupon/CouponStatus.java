package egovframework.eeum.coupon.service.coupon;

import java.time.LocalDate;

/**
 * 쿠폰 상태 — backend 전담 산출(저장 컬럼 아님). 사용기간 vs 기준일(KST) 비교.
 *
 * <pre>
 *   today &lt; usageBgnDate  -> UPCOMING(예정)
 *   today &gt; usageEndDate  -> ENDED(종료)
 *   그 외                   -> ACTIVE(진행중)
 * </pre>
 */
public enum CouponStatus {

	/** 예정. */
	UPCOMING,
	/** 진행중. */
	ACTIVE,
	/** 종료. */
	ENDED;

	/** 사용기간 + 기준일로 상태 산출(순수 함수). */
	public static CouponStatus resolve(LocalDate bgn, LocalDate end, LocalDate today) {
		if (bgn != null && today.isBefore(bgn)) {
			return UPCOMING;
		}
		if (end != null && today.isAfter(end)) {
			return ENDED;
		}
		return ACTIVE;
	}
}
