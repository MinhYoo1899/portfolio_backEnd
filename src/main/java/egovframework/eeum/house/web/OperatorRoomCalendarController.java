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
 * 운영자 대행 객실 캘린더 API — 운영자(HOUSE_PROXY_MNG)가 파트너 객실의 재고/요금 캘린더를 대행 설정·조회.
 *
 * <p>소유자(PRTNR_SN)는 대상 객실에서 서버가 해석(요청에 prtnrSn 없음 — IDOR 차단). 행위자(MDFR_SN)=운영자.
 * 파트너용 {@code /rooms/calendar}(HOUSE_MNG)와 분리하고 서비스 코어는 공유.
 */
@Tag(name = "operator-room-calendar", description = "운영자 대행 (객실 재고/요금 캘린더 관리)")
@RestController
@RequestMapping("/operator/rooms/calendar")
@RequiredArgsConstructor
public class OperatorRoomCalendarController {

	private final RoomCalendarService roomCalendarService;

	@Operation(summary = "캘린더 기간 일괄 설정(운영자 대행)",
			description = "권한 HOUSE_PROXY_MNG. 기간+요일 필터로 재고/가격/마감 일괄 설정(요일별 다른 가격 가능).")
	@PreAuthorize("hasAuthority('HOUSE_PROXY_MNG')")
	@PostMapping("/set")
	public ApiResponse<Void> setRange(@Valid @RequestBody RoomCalendarSetRequest request,
			@AuthenticationPrincipal AuthUser user) {
		roomCalendarService.setRangeAsOperator(request, user);
		return ApiResponse.ok();
	}

	@Operation(summary = "캘린더 가격/수량 설정(운영자 대행)",
			description = "권한 HOUSE_PROXY_MNG. 가격/수량만 설정 — 기존 마감여부(CLOSED_YN) 보존(재오픈 안 함), 신규 행은 N. "
					+ "SOLD_QTY 보존. 소유자=대상객실 서버해석.")
	@PreAuthorize("hasAuthority('HOUSE_PROXY_MNG')")
	@PostMapping("/price-stock/set")
	public ApiResponse<Void> setPriceStock(@Valid @RequestBody RoomCalendarPriceStockSetRequest request,
			@AuthenticationPrincipal AuthUser user) {
		roomCalendarService.setPriceStockRangeAsOperator(request, user);
		return ApiResponse.ok();
	}

	@Operation(summary = "캘린더 마감/해제(운영자 대행)",
			description = "권한 HOUSE_PROXY_MNG. 지정 날짜의 마감여부만 토글(가격/수량/판매수량 보존). "
					+ "Y=마감(미설정 날짜 placeholder)/N=해제(미설정 날짜 포함 시 거부). 과거 날짜 거부. 소유자=대상객실 서버해석.")
	@PreAuthorize("hasAuthority('HOUSE_PROXY_MNG')")
	@PostMapping("/close")
	public ApiResponse<Void> close(@Valid @RequestBody RoomCalendarCloseRequest request,
			@AuthenticationPrincipal AuthUser user) {
		roomCalendarService.closeRangeAsOperator(request, user);
		return ApiResponse.ok();
	}

	@Operation(summary = "캘린더 기간 조회(운영자 대행)",
			description = "권한 HOUSE_PROXY_MNG. 일자별 재고/판매/남은수량/가격/마감. "
					+ "★날짜별 행만 반환(요일 규칙 원본 미저장 — 프론트가 stayDate→요일 그룹핑). 캘린더 행 없는 날짜는 응답에 없음(=미설정).")
	@PreAuthorize("hasAuthority('HOUSE_PROXY_MNG')")
	@GetMapping
	public ApiResponse<List<RoomCalendarResponse>> list(@Valid RoomCalendarSearchRequest request,
			@AuthenticationPrincipal AuthUser user) {
		return ApiResponse.ok(roomCalendarService.listAsOperator(request, user));
	}
}
