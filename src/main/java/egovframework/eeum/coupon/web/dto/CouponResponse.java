package egovframework.eeum.coupon.web.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import egovframework.eeum.coupon.service.coupon.CouponStatus;
import egovframework.eeum.coupon.vo.CouponVO;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 쿠폰 응답. {@code status}(UPCOMING/ACTIVE/ENDED)는 backend 산출값. 발급수/사용수는 읽기전용 표시값.
 */
@Schema(description = "쿠폰 응답")
public record CouponResponse(
		@Schema(description = "쿠폰 식별자") Long couponId,
		@Schema(description = "제목") String title,
		@Schema(description = "내용") String content,
		@Schema(description = "할인유형코드(RATE/FLAT)") String discountTypeCd,
		@Schema(description = "할인값(RATE=%, FLAT=원)") Integer discountVal,
		@Schema(description = "수취인코드") String targetTypeCd,
		@Schema(description = "사용시작일(KST)") LocalDate usageBgnDate,
		@Schema(description = "사용종료일(KST)") LocalDate usageEndDate,
		@Schema(description = "발급수(읽기전용)") int issuedCount,
		@Schema(description = "사용수(읽기전용)") int usedCount,
		@Schema(description = "산출 상태(UPCOMING/ACTIVE/ENDED)") String status,
		@Schema(description = "발행일시(UTC, ISO-8601 Z)") Instant issuedDate) {

	/** VO + 산출 상태 → 응답. */
	public static CouponResponse from(CouponVO vo, CouponStatus status) {
		return new CouponResponse(
				vo.getCouponSn(),
				vo.getTitle(),
				vo.getContent(),
				vo.getDiscountTypeCd(),
				vo.getDiscountVal(),
				vo.getTargetTypeCd(),
				vo.getUsageBgnDate(),
				vo.getUsageEndDate(),
				vo.getIssuedCnt() == null ? 0 : vo.getIssuedCnt(),
				vo.getUsedCnt() == null ? 0 : vo.getUsedCnt(),
				status.name(),
				vo.getRegDt() == null ? null : vo.getRegDt().toInstant(ZoneOffset.UTC));
	}
}
