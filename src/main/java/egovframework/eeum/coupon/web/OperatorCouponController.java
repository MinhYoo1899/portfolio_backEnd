package egovframework.eeum.coupon.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import egovframework.eeum.auth.vo.AuthUser;
import egovframework.eeum.base.response.ApiResponse;
import egovframework.eeum.base.response.PageResponse;
import egovframework.eeum.coupon.service.coupon.CouponService;
import egovframework.eeum.coupon.web.dto.CouponCreateRequest;
import egovframework.eeum.coupon.web.dto.CouponResponse;
import egovframework.eeum.coupon.web.dto.CouponSearchRequest;
import egovframework.eeum.coupon.web.dto.CouponUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 운영자 쿠폰 — 조회=COUPON_VIEW, 발행/수정/삭제=COUPON_MNG.
 *
 * <p>운영자가 쿠폰을 '정의'하는 CRUD. status(UPCOMING/ACTIVE/ENDED)는 서버 산출값. 실제 회원 발급/사용은 후속 도메인.
 */
@Tag(name = "operator-coupon", description = "운영자 쿠폰 조회/발행/수정/삭제")
@RestController
@RequestMapping("/operator/coupons")
@RequiredArgsConstructor
public class OperatorCouponController {

	private final CouponService couponService;

	@Operation(summary = "쿠폰 목록(운영자)", description = "권한 COUPON_VIEW. 상태/할인유형/제목 필터 + 페이지네이션(기본 10). status 산출.")
	@PreAuthorize("hasAuthority('COUPON_VIEW')")
	@GetMapping
	public ApiResponse<PageResponse<CouponResponse>> list(@Valid CouponSearchRequest request) {
		return ApiResponse.ok(couponService.list(request));
	}

	@Operation(summary = "쿠폰 상세(운영자)", description = "권한 COUPON_VIEW. 없으면 404.")
	@PreAuthorize("hasAuthority('COUPON_VIEW')")
	@GetMapping("/{couponId}")
	public ApiResponse<CouponResponse> detail(@PathVariable("couponId") Long couponId) {
		return ApiResponse.ok(couponService.get(couponId));
	}

	@Operation(summary = "쿠폰 발행(운영자)", description = "권한 COUPON_MNG. 할인유형/할인값/수취인/사용기간 검증. 발급수/사용수 0.")
	@PreAuthorize("hasAuthority('COUPON_MNG')")
	@PostMapping
	public ResponseEntity<ApiResponse<CouponResponse>> create(
			@Valid @RequestBody CouponCreateRequest request, @AuthenticationPrincipal AuthUser user) {
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(couponService.create(request, user)));
	}

	@Operation(summary = "쿠폰 수정(운영자)", description = "권한 COUPON_MNG. 전체 교체(발급수/사용수 보존). 없으면 404.")
	@PreAuthorize("hasAuthority('COUPON_MNG')")
	@PutMapping("/{couponId}")
	public ApiResponse<CouponResponse> update(@PathVariable("couponId") Long couponId,
			@Valid @RequestBody CouponUpdateRequest request, @AuthenticationPrincipal AuthUser user) {
		return ApiResponse.ok(couponService.update(couponId, request, user));
	}

	@Operation(summary = "쿠폰 삭제(운영자)", description = "권한 COUPON_MNG. soft delete(DEL_YN='Y'). 없으면 404.")
	@PreAuthorize("hasAuthority('COUPON_MNG')")
	@DeleteMapping("/{couponId}")
	public ApiResponse<Void> delete(@PathVariable("couponId") Long couponId, @AuthenticationPrincipal AuthUser user) {
		couponService.delete(couponId, user);
		return ApiResponse.ok();
	}
}
