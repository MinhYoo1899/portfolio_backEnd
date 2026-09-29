package egovframework.eeum.reservation.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import egovframework.eeum.auth.vo.AuthUser;
import egovframework.eeum.base.response.ApiResponse;
import egovframework.eeum.reservation.service.reservation.ReservationService;
import egovframework.eeum.reservation.web.dto.ReservationCreateRequest;
import egovframework.eeum.reservation.web.dto.ReservationResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 예약 API(회원 채널) — 손님(EEUM 회원)이 본인 예약을 생성/조회. 권한 {@code CHNL_LOGIN_USER}(회원 전용).
 *
 * <p>파트너({@code CHNL_LOGIN_PARTNER})/운영자({@code CHNL_LOGIN_MASTER})는 이 채널에서 제외. 소유권은 토큰 USER_SN 으로 판정.
 */
@Tag(name = "reservation", description = "예약 (회원 — 본인 예약 생성/조회)")
@RestController
@RequestMapping("/reservations")
@RequiredArgsConstructor
public class ReservationController {

	private final ReservationService reservationService;

	@Operation(summary = "예약 생성(HOLD)", description = "회원 본인. 객실+기간+게스트로 HOLD 예약 생성(결제 전 임시점유). "
			+ "Idempotency-Key 헤더로 재시도 중복 차단(원시값 미저장, sha256hex 만).")
	@PreAuthorize("hasAuthority('CHNL_LOGIN_USER')")
	@PostMapping
	public ResponseEntity<ApiResponse<ReservationResponse>> create(
			@Valid @RequestBody ReservationCreateRequest request,
			@RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
			@AuthenticationPrincipal AuthUser user) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiResponse.ok(reservationService.create(request, idempotencyKey, user)));
	}

	@Operation(summary = "예약 확정(RESERVED)", description = "회원 본인. HOLD/PAYMENT_PENDING 예약을 확정 — 결제 승인 후 "
			+ "숙박일별 재고 최종확정(SOLD_QTY) + 상태 RESERVED. 이미 RESERVED 면 멱등 no-op(재고 이중차감 없음).")
	@PreAuthorize("hasAuthority('CHNL_LOGIN_USER')")
	@PostMapping("/{reservationSn}/confirm")
	public ApiResponse<ReservationResponse> confirm(@PathVariable("reservationSn") Long reservationSn,
			@AuthenticationPrincipal AuthUser user) {
		return ApiResponse.ok(reservationService.confirm(reservationSn, user));
	}

	@Operation(summary = "예약 상세", description = "회원 본인 예약만(RESERVER_USER_SN 일치, 타 소유는 404).")
	@PreAuthorize("hasAuthority('CHNL_LOGIN_USER')")
	@GetMapping("/{reservationSn}")
	public ApiResponse<ReservationResponse> detail(@PathVariable("reservationSn") Long reservationSn,
			@AuthenticationPrincipal AuthUser user) {
		return ApiResponse.ok(reservationService.detail(reservationSn, user));
	}

	@Operation(summary = "내 예약 목록", description = "회원 본인 예약 전체(최신순).")
	@PreAuthorize("hasAuthority('CHNL_LOGIN_USER')")
	@GetMapping
	public ApiResponse<List<ReservationResponse>> myList(@AuthenticationPrincipal AuthUser user) {
		return ApiResponse.ok(reservationService.listMine(user));
	}
}
