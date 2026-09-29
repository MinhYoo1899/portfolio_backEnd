package egovframework.eeum.house;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

import egovframework.eeum.account.service.partner.PartnerService;
import egovframework.eeum.auth.vo.AuthUser;
import egovframework.eeum.base.exception.BusinessException;
import egovframework.eeum.house.exception.RoomErrorCode;
import egovframework.eeum.house.service.calendar.dao.RoomCalendarMapper;
import egovframework.eeum.house.service.calendar.impl.RoomCalendarServiceImpl;
import egovframework.eeum.house.service.house.HouseProxyOwnerResolver;
import egovframework.eeum.house.service.house.HousePublishGuard;
import egovframework.eeum.house.service.house.HouseReviewGuard;
import egovframework.eeum.house.service.room.dao.RoomMapper;
import egovframework.eeum.house.vo.RoomCalendarVO;
import egovframework.eeum.house.vo.RoomVO;
import egovframework.eeum.house.web.dto.RoomCalendarCloseRequest;
import egovframework.eeum.house.web.dto.RoomCalendarPriceStockSetRequest;
import egovframework.eeum.house.web.dto.RoomCalendarResponse;
import egovframework.eeum.house.web.dto.RoomCalendarSearchRequest;
import egovframework.eeum.house.web.dto.RoomCalendarSetRequest;

/**
 * 객실 캘린더 서비스 검증 — 소유권/기간·요일 산출/총수량<판매 가드(DB 없이 mock).
 */
class RoomCalendarServiceVerification {

	private static final long MY_PRTNR = 100L;
	private static final long ROOM = 11L;

	private final RoomCalendarMapper calendarMapper = mock(RoomCalendarMapper.class);
	private final RoomMapper roomMapper = mock(RoomMapper.class);
	private final PartnerService partnerService = mock(PartnerService.class);
	private final HouseProxyOwnerResolver proxyResolver = mock(HouseProxyOwnerResolver.class);
	private final HousePublishGuard publishGuard = mock(HousePublishGuard.class);
	private final HouseReviewGuard reviewGuard = mock(HouseReviewGuard.class);
	private final RoomCalendarServiceImpl service = new RoomCalendarServiceImpl(
			calendarMapper, roomMapper, partnerService, proxyResolver, publishGuard, reviewGuard);

	private static AuthUser partner(long userSn) {
		return new AuthUser(userSn, "p@eeum.kr", "김파트너", "PARTNER", "ACTIVE", List.of("HOUSE_MNG"));
	}

	private static RoomVO roomDetail(long ownerPrtnr) {
		return RoomVO.builder().roomSn(ROOM).ownerPrtnrSn(ownerPrtnr).build();
	}

	private static RoomCalendarSetRequest req(LocalDate start, LocalDate end, List<Integer> dow) {
		return new RoomCalendarSetRequest(ROOM, start, end, dow, 1, 150000, "N");
	}

	@SuppressWarnings("unchecked")
	private static ArgumentCaptor<List<LocalDate>> dateCaptor() {
		return ArgumentCaptor.forClass(List.class);
	}

	@Test
	@DisplayName("설정 — 요일 필터 없으면 기간 전체 날짜 upsert")
	void setFullRange() {
		when(partnerService.findManageablePrtnrSn(7L)).thenReturn(MY_PRTNR);
		when(roomMapper.selectRoomDetail(ROOM)).thenReturn(roomDetail(MY_PRTNR));
		ArgumentCaptor<List<LocalDate>> cap = dateCaptor();

		service.setRange(req(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 3), null), partner(7));

		verify(calendarMapper).upsertCalendarRange(eq(ROOM), cap.capture(), eq(1), eq(150000), eq("N"), eq(7L));
		assertEquals(List.of(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 2), LocalDate.of(2026, 7, 3)),
				cap.getValue());
	}

	@Test
	@DisplayName("설정 — 요일 필터 시 해당 요일만 (주말=토6/일7)")
	void setWeekdayFilter() {
		when(partnerService.findManageablePrtnrSn(7L)).thenReturn(MY_PRTNR);
		when(roomMapper.selectRoomDetail(ROOM)).thenReturn(roomDetail(MY_PRTNR));
		ArgumentCaptor<List<LocalDate>> cap = dateCaptor();

		service.setRange(req(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 14), List.of(6, 7)), partner(7));

		verify(calendarMapper).upsertCalendarRange(eq(ROOM), cap.capture(), anyInt(), anyInt(), anyString(), anyLong());
		assertTrue(cap.getValue().stream().allMatch(d -> d.getDayOfWeek().getValue() == 6 || d.getDayOfWeek().getValue() == 7),
				"토/일만 포함되어야 함");
		assertEquals(4, cap.getValue().size(), "2주간 주말 4일");
	}

	@Test
	@DisplayName("설정 — 타 소유 객실은 404, upsert 진입 안 함")
	void setForeignRoom() {
		when(partnerService.findManageablePrtnrSn(7L)).thenReturn(MY_PRTNR);
		when(roomMapper.selectRoomDetail(ROOM)).thenReturn(roomDetail(999L));
		BusinessException e = assertThrows(BusinessException.class,
				() -> service.setRange(req(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 3), null), partner(7)));
		assertEquals(RoomErrorCode.ROOM_NOT_FOUND, e.getErrorCode());
		verify(calendarMapper, never()).upsertCalendarRange(anyLong(), any(), anyInt(), anyInt(), anyString(), anyLong());
	}

	@Test
	@DisplayName("설정 — 종료일이 시작일보다 빠르면 400")
	void setInvalidRange() {
		when(partnerService.findManageablePrtnrSn(7L)).thenReturn(MY_PRTNR);
		when(roomMapper.selectRoomDetail(ROOM)).thenReturn(roomDetail(MY_PRTNR));
		BusinessException e = assertThrows(BusinessException.class,
				() -> service.setRange(req(LocalDate.of(2026, 7, 10), LocalDate.of(2026, 7, 1), null), partner(7)));
		assertEquals(RoomErrorCode.CALENDAR_INVALID_RANGE, e.getErrorCode());
	}

	@Test
	@DisplayName("설정 — 이미 판매된 수량보다 적게(제약 위반) 설정 시 409")
	void setTotalBelowSold() {
		when(partnerService.findManageablePrtnrSn(7L)).thenReturn(MY_PRTNR);
		when(roomMapper.selectRoomDetail(ROOM)).thenReturn(roomDetail(MY_PRTNR));
		doThrow(new DataIntegrityViolationException("check"))
				.when(calendarMapper).upsertCalendarRange(anyLong(), any(), anyInt(), anyInt(), anyString(), anyLong());
		BusinessException e = assertThrows(BusinessException.class,
				() -> service.setRange(req(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 3), null), partner(7)));
		assertEquals(RoomErrorCode.CALENDAR_TOTAL_BELOW_SOLD, e.getErrorCode());
	}

	// ===== 운영자 대행(HOUSE_PROXY_MNG) — owner=resolver 해석, 행위자=운영자 =====

	private static AuthUser operator(long userSn) {
		return new AuthUser(userSn, "op@eeum.dev", "운영자", "OPERATOR", "ACTIVE", List.of("HOUSE_PROXY_MNG"));
	}

	@Test
	@DisplayName("운영자 대행 설정 — owner는 resolver 해석, 행위자=운영자 userSn")
	void setRangeAsOperator() {
		when(proxyResolver.resolveOwnerForRoom(eq(ROOM), any())).thenReturn(MY_PRTNR);
		when(roomMapper.selectRoomDetail(ROOM)).thenReturn(roomDetail(MY_PRTNR));
		ArgumentCaptor<List<LocalDate>> cap = dateCaptor();

		service.setRangeAsOperator(req(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 3), null), operator(9));

		verify(calendarMapper).upsertCalendarRange(eq(ROOM), cap.capture(), eq(1), eq(150000), eq("N"), eq(9L));
		verify(partnerService, never()).findManageablePrtnrSn(anyLong()); // 운영자는 토큰 prtnrSn 해석 안 함
	}

	@Test
	@DisplayName("운영자 대행 설정 — 정지(비승인/비활성) 파트너 객실은 resolver가 차단(upsert 진입 안 함)")
	void setRangeAsOperatorBlockedWhenOwnerSuspended() {
		when(proxyResolver.resolveOwnerForRoom(eq(ROOM), any()))
				.thenThrow(BusinessException.conflict("승인된 활성 파트너의 고택만 대행 관리할 수 있습니다."));

		assertThrows(BusinessException.class,
				() -> service.setRangeAsOperator(req(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 3), null), operator(9)));
		verify(calendarMapper, never()).upsertCalendarRange(anyLong(), any(), anyInt(), anyInt(), anyString(), anyLong());
	}

	// ===== 운영자 대행 조회(listAsOperator) — owner=resolver 해석, 읽기 전용 =====

	private static RoomCalendarSearchRequest searchReq(LocalDate start, LocalDate end) {
		return new RoomCalendarSearchRequest(ROOM, start, end);
	}

	@Test
	@DisplayName("운영자 대행 조회 — resolver owner 해석 후 기간 조회, 토큰 prtnrSn 미사용")
	void listAsOperatorOk() {
		AuthUser op = operator(9);
		when(proxyResolver.resolveOwnerForRoom(eq(ROOM), same(op))).thenReturn(MY_PRTNR);
		when(roomMapper.selectRoomDetail(ROOM)).thenReturn(roomDetail(MY_PRTNR));
		when(calendarMapper.selectCalendarByRoomAndRange(ROOM, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 3)))
				.thenReturn(List.of(RoomCalendarVO.builder().stayDate(LocalDate.of(2026, 7, 1))
						.totalQty(1).soldQty(0).priceAmt(150000).closedYn("N").build()));

		List<RoomCalendarResponse> res = service.listAsOperator(
				searchReq(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 3)), op);

		assertEquals(1, res.size());
		assertEquals(1, res.get(0).availableQty()); // total 1 - sold 0
		verify(proxyResolver).resolveOwnerForRoom(eq(ROOM), same(op)); // 행위자=운영자 객체 그대로 전달
		verify(calendarMapper).selectCalendarByRoomAndRange(ROOM, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 3));
		verify(partnerService, never()).findManageablePrtnrSn(anyLong());
	}

	@Test
	@DisplayName("운영자 대행 조회 — 최대기간(365일) 초과면 400, 조회 미진입")
	void listAsOperatorRangeTooLong() {
		when(proxyResolver.resolveOwnerForRoom(eq(ROOM), any())).thenReturn(MY_PRTNR);
		when(roomMapper.selectRoomDetail(ROOM)).thenReturn(roomDetail(MY_PRTNR));

		BusinessException e = assertThrows(BusinessException.class, () -> service.listAsOperator(
				searchReq(LocalDate.of(2026, 7, 1), LocalDate.of(2027, 7, 10)), operator(9))); // 365일 초과
		assertEquals(RoomErrorCode.CALENDAR_INVALID_RANGE, e.getErrorCode());
		verify(calendarMapper, never()).selectCalendarByRoomAndRange(anyLong(), any(), any());
	}

	@Test
	@DisplayName("운영자 대행 조회 — 타 소유 객실은 404, 조회 미진입")
	void listAsOperatorForeignRoom() {
		when(proxyResolver.resolveOwnerForRoom(eq(ROOM), any())).thenReturn(MY_PRTNR);
		when(roomMapper.selectRoomDetail(ROOM)).thenReturn(roomDetail(999L));

		BusinessException e = assertThrows(BusinessException.class, () -> service.listAsOperator(
				searchReq(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 3)), operator(9)));
		assertEquals(RoomErrorCode.ROOM_NOT_FOUND, e.getErrorCode());
		verify(calendarMapper, never()).selectCalendarByRoomAndRange(anyLong(), any(), any());
	}

	@Test
	@DisplayName("운영자 대행 조회 — 종료일<시작일이면 400, 조회 미진입")
	void listAsOperatorInvalidRange() {
		when(proxyResolver.resolveOwnerForRoom(eq(ROOM), any())).thenReturn(MY_PRTNR);
		when(roomMapper.selectRoomDetail(ROOM)).thenReturn(roomDetail(MY_PRTNR));

		BusinessException e = assertThrows(BusinessException.class, () -> service.listAsOperator(
				searchReq(LocalDate.of(2026, 7, 10), LocalDate.of(2026, 7, 1)), operator(9)));
		assertEquals(RoomErrorCode.CALENDAR_INVALID_RANGE, e.getErrorCode());
		verify(calendarMapper, never()).selectCalendarByRoomAndRange(anyLong(), any(), any());
	}

	@Test
	@DisplayName("운영자 대행 조회 — 정지(비승인/비활성) 파트너 객실은 resolver가 차단(조회 미진입)")
	void listAsOperatorBlockedWhenOwnerSuspended() {
		when(proxyResolver.resolveOwnerForRoom(eq(ROOM), any()))
				.thenThrow(BusinessException.conflict("승인된 활성 파트너의 고택만 대행 관리할 수 있습니다."));

		assertThrows(BusinessException.class, () -> service.listAsOperator(
				searchReq(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 3)), operator(9)));
		verify(calendarMapper, never()).selectCalendarByRoomAndRange(anyLong(), any(), any());
	}

	// ===== 마감/해제 토글(close/open) — CLOSED_YN만, 가격/수량 보존 =====

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");

	private static LocalDate future(int plusDays) {
		return LocalDate.now(KST).plusDays(plusDays);
	}

	private static RoomCalendarCloseRequest closeReq(List<LocalDate> dates, String closedYn) {
		return new RoomCalendarCloseRequest(ROOM, dates, closedYn, null);
	}

	@Test
	@DisplayName("마감(Y) — CLOSED 전용 매퍼 호출(가격설정 upsert 미사용) + ACTIVE 재검증")
	void closeExistingDates() {
		when(partnerService.findManageablePrtnrSn(7L)).thenReturn(MY_PRTNR);
		when(roomMapper.selectRoomDetail(ROOM)).thenReturn(roomDetail(MY_PRTNR));
		when(roomMapper.selectHouseSnByRoom(ROOM)).thenReturn(50L);
		List<LocalDate> dates = List.of(future(3), future(4));
		ArgumentCaptor<List<LocalDate>> cap = dateCaptor();

		service.closeRange(closeReq(dates, "Y"), partner(7));

		verify(calendarMapper).closeDatesUpsert(eq(ROOM), cap.capture(), any(), eq(7L));
		assertEquals(dates, cap.getValue());
		verify(calendarMapper, never()).upsertCalendarRange(anyLong(), any(), anyInt(), anyInt(), anyString(), anyLong());
		verify(publishGuard).snapshotIfActive(50L, MY_PRTNR); // 닫기는 판매가능 감소 → 비퇴행 가드 진입(전 캡처)
	}

	@Test
	@DisplayName("해제(N) — 모든 날짜에 행 존재 시 openDates 호출, publishGuard 미적용")
	void openExistingDates() {
		when(partnerService.findManageablePrtnrSn(7L)).thenReturn(MY_PRTNR);
		when(roomMapper.selectRoomDetail(ROOM)).thenReturn(roomDetail(MY_PRTNR));
		List<LocalDate> dates = List.of(future(3), future(4));
		when(calendarMapper.countExistingDates(eq(ROOM), any())).thenReturn(dates.size());

		service.closeRange(closeReq(dates, "N"), partner(7));

		verify(calendarMapper).openDates(eq(ROOM), any(), eq(7L));
		verify(calendarMapper, never()).closeDatesUpsert(anyLong(), any(), any(), anyLong());
		verify(publishGuard, never()).snapshotIfActive(anyLong(), anyLong()); // 열기는 증가 방향 — 가드 미진입
	}

	@Test
	@DisplayName("해제(N) — 설정되지 않은 날짜 포함 시 거부(CALENDAR_ROW_NOT_FOUND), update 미진입")
	void openMissingDateRejected() {
		when(partnerService.findManageablePrtnrSn(7L)).thenReturn(MY_PRTNR);
		when(roomMapper.selectRoomDetail(ROOM)).thenReturn(roomDetail(MY_PRTNR));
		List<LocalDate> dates = List.of(future(3), future(4));
		when(calendarMapper.countExistingDates(eq(ROOM), any())).thenReturn(1); // 1개만 존재

		BusinessException e = assertThrows(BusinessException.class,
				() -> service.closeRange(closeReq(dates, "N"), partner(7)));
		assertEquals(RoomErrorCode.CALENDAR_ROW_NOT_FOUND, e.getErrorCode());
		verify(calendarMapper, never()).openDates(anyLong(), any(), anyLong());
	}

	@Test
	@DisplayName("과거 날짜 — 마감/해제 모두 400(CALENDAR_INVALID_RANGE), 매퍼 미진입")
	void pastDateRejected() {
		when(partnerService.findManageablePrtnrSn(7L)).thenReturn(MY_PRTNR);
		when(roomMapper.selectRoomDetail(ROOM)).thenReturn(roomDetail(MY_PRTNR));
		List<LocalDate> past = List.of(future(-1));

		BusinessException close = assertThrows(BusinessException.class,
				() -> service.closeRange(closeReq(past, "Y"), partner(7)));
		assertEquals(RoomErrorCode.CALENDAR_INVALID_RANGE, close.getErrorCode());
		BusinessException open = assertThrows(BusinessException.class,
				() -> service.closeRange(closeReq(past, "N"), partner(7)));
		assertEquals(RoomErrorCode.CALENDAR_INVALID_RANGE, open.getErrorCode());
		verify(calendarMapper, never()).closeDatesUpsert(anyLong(), any(), any(), anyLong());
		verify(calendarMapper, never()).openDates(anyLong(), any(), anyLong());
	}

	@Test
	@DisplayName("마감 — 타 소유 객실은 404, 매퍼 미진입")
	void closeForeignRoom() {
		when(partnerService.findManageablePrtnrSn(7L)).thenReturn(MY_PRTNR);
		when(roomMapper.selectRoomDetail(ROOM)).thenReturn(roomDetail(999L));
		BusinessException e = assertThrows(BusinessException.class,
				() -> service.closeRange(closeReq(List.of(future(3)), "Y"), partner(7)));
		assertEquals(RoomErrorCode.ROOM_NOT_FOUND, e.getErrorCode());
		verify(calendarMapper, never()).closeDatesUpsert(anyLong(), any(), any(), anyLong());
	}

	@Test
	@DisplayName("운영자 대행 마감 — owner=resolver 해석, 행위자=운영자 userSn, 토큰 prtnrSn 미사용")
	void closeAsOperator() {
		when(proxyResolver.resolveOwnerForRoom(eq(ROOM), any())).thenReturn(MY_PRTNR);
		when(roomMapper.selectRoomDetail(ROOM)).thenReturn(roomDetail(MY_PRTNR));
		when(roomMapper.selectHouseSnByRoom(ROOM)).thenReturn(50L);

		service.closeRangeAsOperator(closeReq(List.of(future(3)), "Y"), operator(9));

		verify(calendarMapper).closeDatesUpsert(eq(ROOM), any(), any(), eq(9L));
		verify(partnerService, never()).findManageablePrtnrSn(anyLong());
	}

	@Test
	@DisplayName("마감 — ACTIVE 고택 미래 판매가능 전부 소멸 시 publishGuard 롤백(전파)")
	void closeRollbackWhenPublishGuardFails() {
		when(partnerService.findManageablePrtnrSn(7L)).thenReturn(MY_PRTNR);
		when(roomMapper.selectRoomDetail(ROOM)).thenReturn(roomDetail(MY_PRTNR));
		when(roomMapper.selectHouseSnByRoom(ROOM)).thenReturn(50L);
		doThrow(BusinessException.conflict("게시에 필요한 판매가능 객실이 없습니다."))
				.when(publishGuard).enforceNoRegression(eq(50L), eq(MY_PRTNR), any());

		assertThrows(BusinessException.class,
				() -> service.closeRange(closeReq(List.of(future(3)), "Y"), partner(7)));
		verify(calendarMapper).closeDatesUpsert(eq(ROOM), any(), any(), eq(7L)); // 닫기 후 가드에서 롤백
	}

	// ===== 가격/수량 전용(setPriceStockRange) — CLOSED_YN 보존, set(upsertCalendarRange) 미사용 =====

	private static RoomCalendarPriceStockSetRequest priceStockReq(LocalDate start, LocalDate end, List<Integer> dow) {
		return new RoomCalendarPriceStockSetRequest(ROOM, start, end, dow, 1, 150000);
	}

	@Test
	@DisplayName("가격/수량 설정 — 전용 매퍼(upsertPriceStockRange) 호출, set(upsertCalendarRange) 미사용 + ACTIVE 재검증")
	void setPriceStock() {
		when(partnerService.findManageablePrtnrSn(7L)).thenReturn(MY_PRTNR);
		when(roomMapper.selectRoomDetail(ROOM)).thenReturn(roomDetail(MY_PRTNR));
		when(roomMapper.selectHouseSnByRoom(ROOM)).thenReturn(50L);
		ArgumentCaptor<List<LocalDate>> cap = dateCaptor();

		service.setPriceStockRange(priceStockReq(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 3), null), partner(7));

		verify(calendarMapper).upsertPriceStockRange(eq(ROOM), cap.capture(), eq(1), eq(150000), eq(7L));
		assertEquals(3, cap.getValue().size());
		verify(calendarMapper, never()).upsertCalendarRange(anyLong(), any(), anyInt(), anyInt(), anyString(), anyLong());
		verify(publishGuard).snapshotIfActive(50L, MY_PRTNR);
	}

	@Test
	@DisplayName("가격/수량 설정 — 타 소유 객실은 404, 매퍼 미진입")
	void setPriceStockForeignRoom() {
		when(partnerService.findManageablePrtnrSn(7L)).thenReturn(MY_PRTNR);
		when(roomMapper.selectRoomDetail(ROOM)).thenReturn(roomDetail(999L));
		BusinessException e = assertThrows(BusinessException.class, () -> service.setPriceStockRange(
				priceStockReq(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 3), null), partner(7)));
		assertEquals(RoomErrorCode.ROOM_NOT_FOUND, e.getErrorCode());
		verify(calendarMapper, never()).upsertPriceStockRange(anyLong(), any(), anyInt(), anyInt(), anyLong());
	}

	@Test
	@DisplayName("가격/수량 설정 — 이미 판매수량보다 적게(CK 위반) → 409")
	void setPriceStockTotalBelowSold() {
		when(partnerService.findManageablePrtnrSn(7L)).thenReturn(MY_PRTNR);
		when(roomMapper.selectRoomDetail(ROOM)).thenReturn(roomDetail(MY_PRTNR));
		doThrow(new DataIntegrityViolationException("check"))
				.when(calendarMapper).upsertPriceStockRange(anyLong(), any(), anyInt(), anyInt(), anyLong());
		BusinessException e = assertThrows(BusinessException.class, () -> service.setPriceStockRange(
				priceStockReq(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 3), null), partner(7)));
		assertEquals(RoomErrorCode.CALENDAR_TOTAL_BELOW_SOLD, e.getErrorCode());
	}

	@Test
	@DisplayName("운영자 대행 가격/수량 설정 — owner=resolver, 행위자=운영자, 토큰 prtnrSn 미사용")
	void setPriceStockAsOperator() {
		when(proxyResolver.resolveOwnerForRoom(eq(ROOM), any())).thenReturn(MY_PRTNR);
		when(roomMapper.selectRoomDetail(ROOM)).thenReturn(roomDetail(MY_PRTNR));
		when(roomMapper.selectHouseSnByRoom(ROOM)).thenReturn(50L);

		service.setPriceStockRangeAsOperator(
				priceStockReq(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 3), null), operator(9));

		verify(calendarMapper).upsertPriceStockRange(eq(ROOM), any(), eq(1), eq(150000), eq(9L));
		verify(partnerService, never()).findManageablePrtnrSn(anyLong());
	}
}
