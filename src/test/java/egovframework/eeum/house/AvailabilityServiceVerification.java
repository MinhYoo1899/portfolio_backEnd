package egovframework.eeum.house;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import egovframework.eeum.base.exception.BusinessException;
import egovframework.eeum.house.exception.RoomErrorCode;
import egovframework.eeum.house.service.availability.impl.AvailabilityServiceImpl;
import egovframework.eeum.house.service.calendar.dao.RoomCalendarMapper;
import egovframework.eeum.house.vo.RoomAvailabilityVO;
import egovframework.eeum.house.web.dto.AvailabilityResponse;
import egovframework.eeum.house.web.dto.AvailabilitySearchRequest;
import egovframework.eeum.house.web.dto.RoomAvailabilityItem;

/**
 * 가용성 조회 검증 — 가용/매진/인원초과/최소숙박/잘못된기간(DB 없이 mock).
 */
class AvailabilityServiceVerification {

	private static final long HOUSE = 5L;
	private static final LocalDate IN = LocalDate.of(2026, 7, 4);
	private static final LocalDate OUT = LocalDate.of(2026, 7, 6); // 2박

	private final RoomCalendarMapper calendarMapper = mock(RoomCalendarMapper.class);
	private final AvailabilityServiceImpl service = new AvailabilityServiceImpl(calendarMapper);

	private static RoomAvailabilityVO vo(int max, int minStay, int sellableNights, Long totalPrice) {
		return RoomAvailabilityVO.builder().roomSn(11L).roomNm("안채").bscPrsnCnt(2).maxPrsnCnt(max)
				.minStayNgtCnt(minStay).sellableNights(sellableNights).totalPrice(totalPrice).build();
	}

	private static AvailabilitySearchRequest req(int guests) {
		return new AvailabilitySearchRequest(HOUSE, IN, OUT, guests);
	}

	private RoomAvailabilityItem firstRoom(int guests) {
		AvailabilityResponse res = service.search(req(guests));
		assertEquals(2, res.nights());
		return res.rooms().get(0);
	}

	@Test
	@DisplayName("가용 — 전 기간 판매가능 + 인원/최소숙박 OK → available, 가격합계")
	void available() {
		when(calendarMapper.selectRoomAvailability(HOUSE, IN, OUT, 1)).thenReturn(List.of(vo(2, 1, 2, 300000L)));
		RoomAvailabilityItem r = firstRoom(2);
		assertTrue(r.available());
		assertEquals(300000L, r.totalPrice());
		assertNull(r.reason());
	}

	@Test
	@DisplayName("매진 — 판매가능 밤 수가 박수보다 적으면 SOLD_OUT, 가격 null")
	void soldOut() {
		when(calendarMapper.selectRoomAvailability(HOUSE, IN, OUT, 1)).thenReturn(List.of(vo(2, 1, 1, 150000L)));
		RoomAvailabilityItem r = firstRoom(2);
		assertFalse(r.available());
		assertEquals("SOLD_OUT", r.reason());
		assertNull(r.totalPrice());
	}

	@Test
	@DisplayName("인원초과 — 최대인원 < 요청인원 → CAPACITY")
	void capacity() {
		when(calendarMapper.selectRoomAvailability(HOUSE, IN, OUT, 1)).thenReturn(List.of(vo(1, 1, 2, 300000L)));
		RoomAvailabilityItem r = firstRoom(2);
		assertFalse(r.available());
		assertEquals("CAPACITY", r.reason());
	}

	@Test
	@DisplayName("최소숙박 미달 — 박수 < 최소숙박 → MIN_STAY")
	void minStay() {
		when(calendarMapper.selectRoomAvailability(HOUSE, IN, OUT, 1)).thenReturn(List.of(vo(2, 3, 2, 300000L)));
		RoomAvailabilityItem r = firstRoom(2);
		assertFalse(r.available());
		assertEquals("MIN_STAY", r.reason());
	}

	@Test
	@DisplayName("잘못된 기간 — 체크아웃이 체크인 이전이면 400, 조회 진입 안 함")
	void invalidRange() {
		BusinessException e = assertThrows(BusinessException.class,
				() -> service.search(new AvailabilitySearchRequest(HOUSE, OUT, IN, 2)));
		assertEquals(RoomErrorCode.CALENDAR_INVALID_RANGE, e.getErrorCode());
		verify(calendarMapper, never()).selectRoomAvailability(anyLong(), any(), any(), anyInt());
	}
}
