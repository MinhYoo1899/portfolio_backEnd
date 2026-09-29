package egovframework.eeum.coupon.service.coupon;

import egovframework.eeum.auth.vo.AuthUser;
import egovframework.eeum.base.response.PageResponse;
import egovframework.eeum.coupon.web.dto.CouponCreateRequest;
import egovframework.eeum.coupon.web.dto.CouponResponse;
import egovframework.eeum.coupon.web.dto.CouponSearchRequest;
import egovframework.eeum.coupon.web.dto.CouponUpdateRequest;

/**
 * 쿠폰 서비스 — 목록(필터·페이지네이션)/상세/발행/수정/삭제(soft). status(UPCOMING/ACTIVE/ENDED)는 조회 시 산출.
 */
public interface CouponService {

	/** 목록(할인유형/제목/상태 필터 + 페이지네이션, status 산출). */
	PageResponse<CouponResponse> list(CouponSearchRequest request);

	/** 단건 조회(상세). 없으면 COUPON_NOT_FOUND. */
	CouponResponse get(long couponId);

	/** 발행 — 할인유형/할인값/수취인/사용기간 검증 후 저장. 발급수/사용수 0. */
	CouponResponse create(CouponCreateRequest request, AuthUser user);

	/** 전체 교체 수정(발급수/사용수 보존). 없으면 COUPON_NOT_FOUND. */
	CouponResponse update(long couponId, CouponUpdateRequest request, AuthUser user);

	/** soft delete(DEL_YN='Y'). 없으면 COUPON_NOT_FOUND. */
	void delete(long couponId, AuthUser user);
}
