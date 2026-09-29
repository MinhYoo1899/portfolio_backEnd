package egovframework.eeum.house;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import egovframework.eeum.base.exception.BusinessException;
import egovframework.eeum.house.exception.HouseErrorCode;
import egovframework.eeum.house.exception.RoomErrorCode;
import egovframework.eeum.house.service.amenity.dao.HouseAmenityMapper;
import egovframework.eeum.house.service.availability.AvailabilityService;
import egovframework.eeum.house.service.house.dao.HousePayMethodMapper;
import egovframework.eeum.house.service.image.dao.HouseImageMapper;
import egovframework.eeum.house.service.publichouse.dao.PublicHouseMapper;
import egovframework.eeum.house.service.publichouse.impl.PublicHouseServiceImpl;
import egovframework.eeum.house.vo.AmenityVO;
import egovframework.eeum.house.vo.HouseImageVO;
import egovframework.eeum.house.vo.PublicHouseDetailVO;
import egovframework.eeum.house.vo.PublicRoomSummaryVO;
import egovframework.eeum.house.web.dto.AvailabilityResponse;
import egovframework.eeum.house.web.dto.AvailabilitySearchRequest;
import egovframework.eeum.house.web.dto.PublicAvailabilityResponse;
import egovframework.eeum.house.web.dto.PublicHouseDetailResponse;
import egovframework.eeum.house.web.dto.RoomAvailabilityItem;

/**
 * 공개 고택 상세/가용검색 검증(DB 없이 mock) — 404 은닉, 상세 조립, KST 과거 400, 최저가 계산.
 *
 * <p>비공개(DRAFT/HIDDEN/SUSPENDED/삭제/미승인·정지 파트너)의 SQL 필터 자체는 매퍼 XML 계약 검증
 * ({@code MapperXmlReferenceVerification.publicHouseVisibility_mapperContract})이 잠그고,
 * 여기서는 "가시성 밖 = null/0 → 404 + 하위 조회 미진입" 서비스 계약을 잠근다.
 */
class PublicHouseDetailServiceVerification {

	private static final long HOUSE = 3L;
	private static final ZoneId KST = ZoneId.of("Asia/Seoul");
	/** 미래 숙박일(오늘+10 ~ +12, 2박) — 어떤 TZ 에서도 오늘(KST) 이후. */
	private static final LocalDate IN = LocalDate.now(KST).plusDays(10);
	private static final LocalDate OUT = IN.plusDays(2);

	private final PublicHouseMapper publicHouseMapper = mock(PublicHouseMapper.class);
	private final HouseImageMapper houseImageMapper = mock(HouseImageMapper.class);
	private final HouseAmenityMapper houseAmenityMapper = mock(HouseAmenityMapper.class);
	private final HousePayMethodMapper housePayMethodMapper = mock(HousePayMethodMapper.class);
	private final AvailabilityService availabilityService = mock(AvailabilityService.class);
	private final PublicHouseServiceImpl service = new PublicHouseServiceImpl(
			publicHouseMapper, houseImageMapper, houseAmenityMapper, housePayMethodMapper, availabilityService);

	// ---- fixtures ----

	private static PublicHouseDetailVO houseVo() {
		return PublicHouseDetailVO.builder()
				.houseSn(HOUSE).houseNm("학봉종택").addr("경북 안동시 서후면").houseDc("고즈넉한 종택")
				.adultAddChrgAmt(20000).childAddChrgAmt(10000).infantAddChrgAmt(0)
				.chkinTime(LocalTime.of(15, 0)).chkoutTime(LocalTime.of(11, 0)).lateChkoutTime(null)
				.rsvtCnfirmCd("AUTO").cnclRfndPlcyCn("체크인 3일 전 무료취소").pbtrnspGuideCn("버스 246번")
				.noticeCn("반려동물 불가")
				.sidoNm("경상북도").sigunguNm("안동시")
				.conm("고즈넉한옥").rprsvNm("홍길동").brno("1234567890").mailOrderSalesNo(null)
				.build();
	}

	private static RoomAvailabilityItem item(long roomSn, boolean available, String reason, Long totalPrice) {
		return new RoomAvailabilityItem(roomSn, "객실" + roomSn, 2, 4, available, reason, totalPrice);
	}

	private static AvailabilitySearchRequest req(LocalDate in, LocalDate out) {
		return new AvailabilitySearchRequest(HOUSE, in, out, 2);
	}

	// ---- 상세 ----

	@Test
	@DisplayName("상세 — 가시성 통과 시 이미지/편의시설/결제수단/객실요약/판매자 표시정보를 조립한다")
	void detail_assemblesPublicContract() {
		when(publicHouseMapper.selectPublicHouseDetail(HOUSE)).thenReturn(houseVo());
		when(houseImageMapper.selectActiveImagesByHouse(HOUSE)).thenReturn(List.of(
				HouseImageVO.builder().houseImgSn(1L).atchFileSn(12L).sortSeq(1).rprsYn("Y").build(),
				HouseImageVO.builder().houseImgSn(2L).atchFileSn(13L).sortSeq(2).rprsYn("N").build()));
		when(houseAmenityMapper.selectActiveAmenitiesByHouse(HOUSE)).thenReturn(List.of(
				AmenityVO.builder().amenitySn(5L).amenityCtgryCd("OUTDOOR").applyScopeCd("HOUSE")
						.amenityNm("마당").sortSeq(1).useYn("Y").customYn("N").build()));
		when(housePayMethodMapper.selectActiveCodesByHouse(HOUSE)).thenReturn(List.of("CARD", "BANK_TRANSFER"));
		when(publicHouseMapper.selectPublicRoomsByHouse(HOUSE)).thenReturn(List.of(
				PublicRoomSummaryVO.builder().roomSn(11L).roomNm("안채").bscPrsnCnt(2).maxPrsnCnt(4)
						.minStayNgtCnt(1).roomDc("온돌방").areaSqm(new BigDecimal("33.00"))
						.roomCnt(2).bathrmCnt(1).rprsAtchFileSn(15L).build(),
				PublicRoomSummaryVO.builder().roomSn(12L).roomNm("사랑채").bscPrsnCnt(2).maxPrsnCnt(2)
						.minStayNgtCnt(1).rprsAtchFileSn(null).build()));

		PublicHouseDetailResponse res = service.detail(HOUSE);

		assertEquals(HOUSE, res.houseSn());
		assertEquals("경상북도 안동시", res.regionText());
		assertEquals("15:00", res.chkinTime());
		assertEquals("11:00", res.chkoutTime());
		assertNull(res.lateChkoutTime());
		assertEquals("AUTO", res.rsvtCnfirmCd());
		assertEquals(List.of("CARD", "BANK_TRANSFER"), res.payMethods());
		assertEquals(2, res.images().size());
		assertEquals("/files/download?atchFileSn=12", res.images().get(0).imageUrl());
		assertEquals("Y", res.images().get(0).rprsYn());
		assertEquals(1, res.amenities().size());
		assertEquals("마당", res.amenities().get(0).amenityNm());
		assertEquals(2, res.rooms().size());
		assertEquals("/files/download?atchFileSn=15", res.rooms().get(0).repImgUrl());
		assertNull(res.rooms().get(1).repImgUrl());
		// 판매자 표시정보 4필드(통신판매번호 미신고 = null 허용)
		assertEquals("고즈넉한옥", res.seller().conm());
		assertEquals("홍길동", res.seller().rprsvNm());
		assertEquals("1234567890", res.seller().brno());
		assertNull(res.seller().mailOrderSalesNo());
	}

	@Test
	@DisplayName("상세 — 가시성 밖(미존재/DRAFT/HIDDEN/SUSPENDED/삭제/미승인·정지 파트너 = 0행)은 404, 하위 조회 미진입")
	void detail_hiddenHouse_notFound() {
		when(publicHouseMapper.selectPublicHouseDetail(HOUSE)).thenReturn(null);

		BusinessException e = assertThrows(BusinessException.class, () -> service.detail(HOUSE));

		assertEquals(HouseErrorCode.HOUSE_NOT_FOUND, e.getErrorCode());
		verify(houseImageMapper, never()).selectActiveImagesByHouse(anyLong());
		verify(houseAmenityMapper, never()).selectActiveAmenitiesByHouse(anyLong());
		verify(housePayMethodMapper, never()).selectActiveCodesByHouse(anyLong());
		verify(publicHouseMapper, never()).selectPublicRoomsByHouse(anyLong());
	}

	// ---- 가용검색 ----

	@Test
	@DisplayName("가용검색 — KST 과거 체크인은 400(CALENDAR_INVALID_RANGE), 가시성/판정 미진입")
	void availability_pastCheckIn_badRequest() {
		LocalDate yesterday = LocalDate.now(KST).minusDays(1);

		BusinessException e = assertThrows(BusinessException.class,
				() -> service.availability(req(yesterday, yesterday.plusDays(2))));

		assertEquals(RoomErrorCode.CALENDAR_INVALID_RANGE, e.getErrorCode());
		verify(publicHouseMapper, never()).countPublicVisibleHouse(anyLong());
		verify(availabilityService, never()).search(any());
	}

	@Test
	@DisplayName("가용검색 — 비공개/미존재 고택은 404 은닉(200+빈배열 아님), 판정 미진입")
	void availability_hiddenHouse_notFound() {
		when(publicHouseMapper.countPublicVisibleHouse(HOUSE)).thenReturn(0);

		BusinessException e = assertThrows(BusinessException.class, () -> service.availability(req(IN, OUT)));

		assertEquals(HouseErrorCode.HOUSE_NOT_FOUND, e.getErrorCode());
		verify(availabilityService, never()).search(any());
	}

	@Test
	@DisplayName("가용검색 — 불가 객실은 reason 포함 유지 + 최저가는 가능 객실 총액의 최솟값(오늘 체크인 허용 경계 포함)")
	void availability_lowestTotalPrice_fromAvailableRoomsOnly() {
		LocalDate today = LocalDate.now(KST); // 경계 — 오늘 체크인은 과거가 아님
		when(publicHouseMapper.countPublicVisibleHouse(HOUSE)).thenReturn(1);
		when(availabilityService.search(any())).thenReturn(AvailabilityResponse.of(HOUSE, today, today.plusDays(2), 2,
				List.of(item(11L, true, null, 300000L),
						item(12L, true, null, 250000L),
						item(13L, false, "SOLD_OUT", null),
						item(14L, false, "CAPACITY", null))));

		PublicAvailabilityResponse res = service.availability(req(today, today.plusDays(2)));

		assertEquals(250000L, res.lowestTotalPrice());
		assertEquals(2, res.nights());
		assertEquals(4, res.rooms().size(), "불가 객실도 사유와 함께 포함");
		assertFalse(res.rooms().get(2).available());
		assertEquals("SOLD_OUT", res.rooms().get(2).reason());
		assertEquals("CAPACITY", res.rooms().get(3).reason());
		assertTrue(res.rooms().stream().filter(r -> !r.available()).allMatch(r -> r.totalPrice() == null),
				"불가 객실은 총액 미노출");
	}

	@Test
	@DisplayName("가용검색 — 가능 객실 0건이면 최저가 null(빈 목록 포함)")
	void availability_noAvailableRoom_lowestNull() {
		when(publicHouseMapper.countPublicVisibleHouse(HOUSE)).thenReturn(1);
		when(availabilityService.search(any())).thenReturn(AvailabilityResponse.of(HOUSE, IN, OUT, 2,
				List.of(item(11L, false, "SOLD_OUT", null), item(12L, false, "MIN_STAY", null))));

		PublicAvailabilityResponse res = service.availability(req(IN, OUT));

		assertNull(res.lowestTotalPrice());
		assertEquals(2, res.rooms().size());
	}
}
