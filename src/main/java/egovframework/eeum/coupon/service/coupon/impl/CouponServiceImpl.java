package egovframework.eeum.coupon.service.coupon.impl;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import egovframework.eeum.auth.vo.AuthUser;
import egovframework.eeum.base.audit.Audit;
import egovframework.eeum.base.exception.BusinessException;
import egovframework.eeum.base.response.PageResponse;
import egovframework.eeum.code.service.code.CodeService;
import egovframework.eeum.coupon.exception.CouponErrorCode;
import egovframework.eeum.coupon.service.coupon.CouponService;
import egovframework.eeum.coupon.service.coupon.CouponStatus;
import egovframework.eeum.coupon.service.coupon.dao.CouponMapper;
import egovframework.eeum.coupon.vo.CouponVO;
import egovframework.eeum.coupon.web.dto.CouponCreateRequest;
import egovframework.eeum.coupon.web.dto.CouponResponse;
import egovframework.eeum.coupon.web.dto.CouponSearchRequest;
import egovframework.eeum.coupon.web.dto.CouponUpdateRequest;
import egovframework.eeum.notification.service.notification.NotificationEmitter;
import egovframework.eeum.notification.service.notification.NotificationType;
import lombok.RequiredArgsConstructor;

/**
 * 쿠폰 서비스 구현. status(UPCOMING/ACTIVE/ENDED)는 조회 시 {@link CouponStatus#resolve} 산출.
 * 발급수/사용수는 읽기전용(수정 시 보존), 삭제는 soft delete. 수취인은 현 단계 ALL 만 허용.
 */
@Service
@RequiredArgsConstructor
public class CouponServiceImpl implements CouponService {

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");
	private static final String DISCOUNT_TYPE_GROUP = "COUPON_DISCOUNT_TYPE";
	private static final String TARGET_ALL = "ALL";

	private final CouponMapper couponMapper;
	private final CodeService codeService;
	private final NotificationEmitter notificationEmitter;

	@Override
	@Transactional(readOnly = true)
	public PageResponse<CouponResponse> list(CouponSearchRequest request) {
		LocalDate today = LocalDate.now(KST);
		String discountTypeCd = blankToNull(request.discountTypeCd());
		String keyword = blankToNull(request.keyword());
		String status = blankToNull(request.status());
		int page = request.pageOrDefault();
		int size = request.sizeOrDefault();

		long total = couponMapper.countCoupon(discountTypeCd, keyword, status, today);
		List<CouponResponse> items = couponMapper
				.selectCouponList(discountTypeCd, keyword, status, today, request.offset(), size).stream()
				.map(vo -> toResponse(vo, today))
				.toList();
		return PageResponse.of(items, total, page, size);
	}

	@Override
	@Transactional(readOnly = true)
	public CouponResponse get(long couponId) {
		CouponVO vo = couponMapper.selectCouponById(couponId);
		if (vo == null) {
			throw new BusinessException(CouponErrorCode.COUPON_NOT_FOUND);
		}
		return toResponse(vo, LocalDate.now(KST));
	}

	@Override
	@Transactional
	@Audit(actn = "COUPON_INSERT", trgtType = "COUPON", trgtId = "#result.couponId()")
	public CouponResponse create(CouponCreateRequest request, AuthUser user) {
		validateDiscount(request.discountTypeCd(), request.discountVal());
		String targetType = normalizeTarget(request.targetTypeCd());
		validateDateRange(request.usageBgnDate(), request.usageEndDate());

		CouponVO vo = new CouponVO();
		vo.setTitle(request.title());
		vo.setContent(request.content());
		vo.setDiscountTypeCd(request.discountTypeCd());
		vo.setDiscountVal(request.discountVal());
		vo.setTargetTypeCd(targetType);
		vo.setUsageBgnDate(request.usageBgnDate());
		vo.setUsageEndDate(request.usageEndDate());
		vo.setRgtrSn(user.userSn());
		couponMapper.insertCoupon(vo);
		notificationEmitter.emit(user.userSn(), "COUPON:" + vo.getCouponSn(), NotificationType.COUPON,
				"쿠폰 발급", request.title(), String.valueOf(vo.getCouponSn()));

		return get(vo.getCouponSn());
	}

	@Override
	@Transactional
	@Audit(actn = "COUPON_UPDATE", trgtType = "COUPON", trgtId = "#couponId")
	public CouponResponse update(long couponId, CouponUpdateRequest request, AuthUser user) {
		validateDiscount(request.discountTypeCd(), request.discountVal());
		String targetType = normalizeTarget(request.targetTypeCd());
		validateDateRange(request.usageBgnDate(), request.usageEndDate());

		CouponVO vo = new CouponVO();
		vo.setCouponSn(couponId);
		vo.setTitle(request.title());
		vo.setContent(request.content());
		vo.setDiscountTypeCd(request.discountTypeCd());
		vo.setDiscountVal(request.discountVal());
		vo.setTargetTypeCd(targetType);
		vo.setUsageBgnDate(request.usageBgnDate());
		vo.setUsageEndDate(request.usageEndDate());
		vo.setMdfrSn(user.userSn());
		if (couponMapper.updateCoupon(vo) == 0) {
			throw new BusinessException(CouponErrorCode.COUPON_NOT_FOUND);
		}
		return get(couponId);
	}

	@Override
	@Transactional
	@Audit(actn = "COUPON_DELETE", trgtType = "COUPON", trgtId = "#couponId")
	public void delete(long couponId, AuthUser user) {
		if (couponMapper.softDeleteCoupon(couponId, user.userSn()) == 0) {
			throw new BusinessException(CouponErrorCode.COUPON_NOT_FOUND);
		}
	}

	// ------------------------------------------------------------------

	private CouponResponse toResponse(CouponVO vo, LocalDate today) {
		return CouponResponse.from(vo, CouponStatus.resolve(vo.getUsageBgnDate(), vo.getUsageEndDate(), today));
	}

	/** 할인유형(공통코드 존재) + 할인값(RATE 1~100, FLAT 1 이상) 검증. */
	private void validateDiscount(String discountTypeCd, Integer discountVal) {
		String cd = blankToNull(discountTypeCd);
		if (cd == null || codeService.list(DISCOUNT_TYPE_GROUP).stream().noneMatch(item -> item.cd().equals(cd))) {
			throw new BusinessException(CouponErrorCode.COUPON_DISCOUNT_TYPE_INVALID);
		}
		if (discountVal == null || discountVal <= 0) {
			throw new BusinessException(CouponErrorCode.COUPON_DISCOUNT_VALUE_INVALID);
		}
		if ("RATE".equals(cd) && discountVal > 100) {
			throw new BusinessException(CouponErrorCode.COUPON_DISCOUNT_VALUE_INVALID);
		}
	}

	/** 수취인 정규화 — null/blank→ALL, ALL 외 값은 미지원(현 단계). */
	private String normalizeTarget(String targetTypeCd) {
		String target = blankToNull(targetTypeCd);
		if (target == null) {
			return TARGET_ALL;
		}
		if (!TARGET_ALL.equals(target)) {
			throw new BusinessException(CouponErrorCode.COUPON_TARGET_TYPE_INVALID);
		}
		return TARGET_ALL;
	}

	private void validateDateRange(LocalDate bgn, LocalDate end) {
		if (bgn != null && end != null && end.isBefore(bgn)) {
			throw new BusinessException(CouponErrorCode.COUPON_DATE_RANGE_INVALID);
		}
	}

	private static String blankToNull(String s) {
		return (s == null || s.isBlank()) ? null : s;
	}
}
