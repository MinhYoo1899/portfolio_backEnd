package egovframework.eeum.reservation.web;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import egovframework.eeum.base.response.ApiResponse;
import egovframework.eeum.base.response.PageResponse;
import egovframework.eeum.reservation.service.operator.OperatorReservationService;
import egovframework.eeum.reservation.web.dto.OperatorReservationDetailResponse;
import egovframework.eeum.reservation.web.dto.OperatorReservationListItemResponse;
import egovframework.eeum.reservation.web.dto.OperatorReservationSearchRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 운영자 예약 콘솔 — 조회 전용(RESERVATION_VIEW). 전 판매자 예약 목록/상세.
 *
 * <p>결제수단·예약경로·환불·투숙상태는 스키마 부재로 미제공(상세 unsupported 플래그). 게스트 전화는 미노출(PII).
 * 환불/취소 처리(mutation)는 PG 스텁·상태전이 별도 슬라이스로 분리.
 */
@Tag(name = "operator-reservation", description = "운영자 예약 콘솔 조회(목록/상세)")
@RestController
@RequestMapping("/operator/reservations")
@RequiredArgsConstructor
public class OperatorReservationController {

	private final OperatorReservationService operatorReservationService;

	@Operation(summary = "예약 목록(운영자)", description = "권한 RESERVATION_VIEW. 상태/기간(체크인)/키워드(고택명·예약자명·주문번호) 필터 + 페이지네이션(기본 10).")
	@PreAuthorize("hasAuthority('RESERVATION_VIEW')")
	@GetMapping
	public ApiResponse<PageResponse<OperatorReservationListItemResponse>> list(
			@Valid OperatorReservationSearchRequest request) {
		return ApiResponse.ok(operatorReservationService.list(request));
	}

	@Operation(summary = "예약 상세(운영자)", description = "권한 RESERVATION_VIEW. 없으면 404. 결제수단/예약경로/환불/투숙상태는 unsupported.")
	@PreAuthorize("hasAuthority('RESERVATION_VIEW')")
	@GetMapping("/{reservationId}")
	public ApiResponse<OperatorReservationDetailResponse> detail(@PathVariable("reservationId") Long reservationId) {
		return ApiResponse.ok(operatorReservationService.detail(reservationId));
	}
}
