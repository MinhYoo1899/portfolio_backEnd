package egovframework.eeum.coupon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import egovframework.eeum.auth.vo.AuthUser;
import egovframework.eeum.base.exception.BusinessException;
import egovframework.eeum.code.service.code.CodeService;
import egovframework.eeum.code.web.dto.CodeItemResponse;
import egovframework.eeum.coupon.exception.CouponErrorCode;
import egovframework.eeum.coupon.service.coupon.dao.CouponMapper;
import egovframework.eeum.coupon.service.coupon.impl.CouponServiceImpl;
import egovframework.eeum.coupon.vo.CouponVO;
import egovframework.eeum.coupon.web.dto.CouponCreateRequest;
import egovframework.eeum.coupon.web.dto.CouponResponse;
import egovframework.eeum.coupon.web.dto.CouponSearchRequest;

/**
 * 쿠폰 서비스 검증 — 할인유형/할인값/수취인/사용기간 validation + status 산출 + 페이지네이션 + 404.
 */
class CouponServiceVerification {

	private final CouponMapper couponMapper = mock(CouponMapper.class);
	private final CodeService codeService = mock(CodeService.class);
	private final CouponServiceImpl service = new CouponServiceImpl(couponMapper, codeService,
			mock(egovframework.eeum.notification.service.notification.NotificationEmitter.class));

	private static AuthUser user() {
		return new AuthUser(9L, "op@eeum.dev", "운영자", "OPERATOR", "ACTIVE", List.of("COUPON_MNG"));
	}

	private void stubTypes() {
		when(codeService.list("COUPON_DISCOUNT_TYPE")).thenReturn(List.of(
				new CodeItemResponse("COUPON_DISCOUNT_TYPE", "RATE", "정률", 1),
				new CodeItemResponse("COUPON_DISCOUNT_TYPE", "FLAT", "정액", 2)));
	}

	private CouponCreateRequest req(String type, Integer val, String target, LocalDate bgn, LocalDate end) {
		return new CouponCreateRequest("첫 예약 할인", "10만원 이상 결제 시 할인", type, val, target, bgn, end);
	}

	private void stubSaved(long sn) {
		CouponVO saved = new CouponVO();
		saved.setCouponSn(sn);
		saved.setTitle("첫 예약 할인");
		saved.setDiscountTypeCd("RATE");
		saved.setDiscountVal(10);
		saved.setTargetTypeCd("ALL");
		saved.setUsageBgnDate(LocalDate.now().minusDays(1));
		saved.setUsageEndDate(LocalDate.now().plusDays(30));
		saved.setIssuedCnt(0);
		saved.setUsedCnt(0);
		when(couponMapper.selectCouponById(sn)).thenReturn(saved);
	}

	@Test
	@DisplayName("없는 할인유형 → COUPON_DISCOUNT_TYPE_INVALID")
	void discountType_unknown_rejected() {
		stubTypes();
		BusinessException e = assertThrows(() -> service.create(
				req("PERCENT", 10, "ALL", LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31)), user()));
		assertEquals(CouponErrorCode.COUPON_DISCOUNT_TYPE_INVALID, e.getErrorCode());
	}

	@Test
	@DisplayName("정률 할인 100 초과 → COUPON_DISCOUNT_VALUE_INVALID")
	void rate_over100_rejected() {
		stubTypes();
		BusinessException e = assertThrows(() -> service.create(
				req("RATE", 150, "ALL", LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31)), user()));
		assertEquals(CouponErrorCode.COUPON_DISCOUNT_VALUE_INVALID, e.getErrorCode());
	}

	@Test
	@DisplayName("지원하지 않는 수취인 → COUPON_TARGET_TYPE_INVALID")
	void target_notAll_rejected() {
		stubTypes();
		BusinessException e = assertThrows(() -> service.create(
				req("FLAT", 5000, "GRADE_VIP", LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31)), user()));
		assertEquals(CouponErrorCode.COUPON_TARGET_TYPE_INVALID, e.getErrorCode());
	}

	@Test
	@DisplayName("사용 종료일 < 시작일 → COUPON_DATE_RANGE_INVALID")
	void dateReversed_rejected() {
		stubTypes();
		BusinessException e = assertThrows(() -> service.create(
				req("FLAT", 5000, "ALL", LocalDate.of(2026, 7, 31), LocalDate.of(2026, 7, 1)), user()));
		assertEquals(CouponErrorCode.COUPON_DATE_RANGE_INVALID, e.getErrorCode());
	}

	@Test
	@DisplayName("정상 발행(수취인 미지정→ALL) → status 산출, 발급수 0")
	void create_success_defaultTargetAll() {
		stubTypes();
		doAnswer(inv -> {
			inv.getArgument(0, CouponVO.class).setCouponSn(100L);
			return 1;
		}).when(couponMapper).insertCoupon(any());
		stubSaved(100L);
		CouponResponse res = service.create(
				req("RATE", 10, null, LocalDate.now().minusDays(1), LocalDate.now().plusDays(30)), user());
		assertEquals(100L, res.couponId());
		assertEquals("ACTIVE", res.status());
		assertEquals(0, res.issuedCount());
		assertEquals("ALL", res.targetTypeCd());
	}

	@Test
	@DisplayName("목록 — count + offset 페이지네이션(상태/할인유형/today 위임)")
	void list_pagination() {
		CouponVO vo = new CouponVO();
		vo.setCouponSn(1L);
		vo.setUsageBgnDate(LocalDate.now().minusDays(1));
		vo.setUsageEndDate(LocalDate.now().plusDays(1));
		when(couponMapper.countCoupon(eq("RATE"), isNull(), eq("ACTIVE"), any())).thenReturn(12L);
		when(couponMapper.selectCouponList(eq("RATE"), isNull(), eq("ACTIVE"), any(), eq(10), eq(10)))
				.thenReturn(List.of(vo));

		var page = service.list(new CouponSearchRequest("ACTIVE", "RATE", null, 2, 10));

		assertEquals(12L, page.total());
		assertEquals(2, page.totalPages()); // ceil(12/10)
		assertEquals(1, page.items().size());
		verify(couponMapper).selectCouponList(eq("RATE"), isNull(), eq("ACTIVE"), any(), eq(10), eq(10)); // offset=10
	}

	@Test
	@DisplayName("없는 쿠폰 조회/수정/삭제 → COUPON_NOT_FOUND")
	void notFound_paths() {
		when(couponMapper.selectCouponById(404L)).thenReturn(null);
		assertEquals(CouponErrorCode.COUPON_NOT_FOUND, assertThrows(() -> service.get(404L)).getErrorCode());
		when(couponMapper.softDeleteCoupon(anyLong(), anyLong())).thenReturn(0);
		assertEquals(CouponErrorCode.COUPON_NOT_FOUND,
				assertThrows(() -> service.delete(404L, user())).getErrorCode());
	}

	@Test
	@DisplayName("삭제 성공 → softDeleteCoupon 호출")
	void delete_success() {
		when(couponMapper.softDeleteCoupon(5L, 9L)).thenReturn(1);
		service.delete(5L, user());
		verify(couponMapper).softDeleteCoupon(5L, 9L);
	}

	// ------------------------------------------------------------------

	private static BusinessException assertThrows(org.junit.jupiter.api.function.Executable exec) {
		return org.junit.jupiter.api.Assertions.assertThrows(BusinessException.class, exec);
	}
}
