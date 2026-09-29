package egovframework.eeum.coupon.vo;

import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

/**
 * 쿠폰 VO — COUPON 매핑(mybatis map-underscore-to-camel).
 *
 * <p>{@code status}(UPCOMING/ACTIVE/ENDED)는 저장 컬럼이 아니라 서비스가 (사용기간 vs 오늘)으로 산출한다.
 * {@code issuedCnt}/{@code usedCnt}는 읽기전용(등록/수정 대상 아님 — 회원 발급·사용 플로우에서 증감).
 */
@Getter
@Setter
public class CouponVO {

	/** 쿠폰일련번호(PK). */
	private Long couponSn;

	/** 제목. */
	private String title;

	/** 내용. */
	private String content;

	/** 할인유형(COUPON_DISCOUNT_TYPE — RATE/FLAT). */
	private String discountTypeCd;

	/** 할인값(RATE=%, FLAT=원). */
	private Integer discountVal;

	/** 수취인(현 단계 ALL=전체회원). */
	private String targetTypeCd;

	/** 사용시작일(KST). */
	private LocalDate usageBgnDate;

	/** 사용종료일(KST). */
	private LocalDate usageEndDate;

	/** 발급수(읽기전용). */
	private Integer issuedCnt;

	/** 사용수(읽기전용). */
	private Integer usedCnt;

	/** 등록일시(=발행일). */
	private LocalDateTime regDt;

	/** 등록자(사용자일련번호). */
	private Long rgtrSn;

	/** 수정일시. */
	private LocalDateTime mdfcnDt;

	/** 수정자(사용자일련번호). */
	private Long mdfrSn;
}
