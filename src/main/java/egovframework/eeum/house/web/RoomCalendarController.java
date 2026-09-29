package egovframework.eeum.house.web;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import egovframework.eeum.auth.vo.AuthUser;
import egovframework.eeum.base.response.ApiResponse;
import egovframework.eeum.house.service.calendar.RoomCalendarService;
import egovframework.eeum.house.web.dto.RoomCalendarCloseRequest;
import egovframework.eeum.house.web.dto.RoomCalendarPriceStockSetRequest;
import egovframework.eeum.house.web.dto.RoomCalendarResponse;
import egovframework.eeum.house.web.dto.RoomCalendarSearchRequest;
import egovframework.eeum.house.web.dto.RoomCalendarSetRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 상품(객실 재고/요금 캘린더) API — 판매자 본인 소유 객실의 일자별 재고/가격/마감 관리. 권한 {@code HOUSE_MNG}.
 */
@Tag(name = "room-calendar", description = "상품 (객실 재고/요금 캘린더 — 판매자 관리)")
@RestController
@RequestMapping("/rooms/calendar")
@RequiredArgsConstructor
public class RoomCalendarController {

	private final RoomCalendarService roomCalendarService;

	@Operation(summary = "캘린더 기간 일괄 설정", description = "판매자 본인 소유. 기간+요일 필터로 재고/가격/마감 일괄 설정(요일별 다른 가격 가능).")
	@PreAuthorize("hasAuthority('HOUSE_MNG')")
	@PostMapping("/set")
	public ApiResponse<Void> setRange(@Valid @RequestBody RoomCalendarSetRequest request,
			@AuthenticationPrincipal AuthUser user) {
		roomCalendarService.setRange(request, user);
		return ApiResponse.ok();
	}

	@Operation(summary = "캘린더 가격/수량 설정", description = "판매자 본인 소유. 가격/수량만 일괄 설정 — "
			+ "기존 마감여부(CLOSED_YN) 보존(요금 변경이 마감일을 재오픈하지 않음), 신규 행은 N. SOLD_QTY 보존.")
	@PreAuthorize("hasAuthority('HOUSE_MNG')")
	@PostMapping("/price-stock/set")
	public ApiResponse<Void> setPriceStock(@Valid @RequestBody RoomCalendarPriceStockSetRequest request,
			@AuthenticationPrincipal AuthUser user) {
		roomCalendarService.setPriceStockRange(request, user);
		return ApiResponse.ok();
	}

	@Operation(summary = "캘린더 마감/해제", description = "판매자 본인 소유. 지정 날짜의 마감여부(CLOSED_YN)만 토글 — 가격/수량/판매수량 보존. "
			+ "Y=마감(미설정 날짜는 placeholder 생성)/N=해제(미설정 날짜 포함 시 거부). 과거 날짜 거부.")
	@PreAuthorize("hasAuthority('HOUSE_MNG')")
	@PostMapping("/close")
	public ApiResponse<Void> close(@Valid @RequestBody RoomCalendarCloseRequest request,
			@AuthenticationPrincipal AuthUser user) {
		roomCalendarService.closeRange(request, user);
		return ApiResponse.ok();
	}

	@Operation(summary = "캘린더 기간 조회", description = "판매자 본인 소유. 일자별 재고/판매/남은수량/가격/마감.")
	@PreAuthorize("hasAuthority('HOUSE_MNG')")
	@GetMapping
	public ApiResponse<List<RoomCalendarResponse>> list(@Valid RoomCalendarSearchRequest request,
			@AuthenticationPrincipal AuthUser user) {
		return ApiResponse.ok(roomCalendarService.list(request, user));
	}
}
