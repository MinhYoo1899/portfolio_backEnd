package egovframework.eeum.house;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import egovframework.eeum.account.service.partner.PartnerService;
import egovframework.eeum.auth.vo.AuthUser;
import egovframework.eeum.base.exception.BusinessException;
import egovframework.eeum.code.service.code.CodeService;
import egovframework.eeum.code.web.dto.CodeItemResponse;
import egovframework.eeum.house.exception.RoomErrorCode;
import egovframework.eeum.house.service.house.HouseProxyOwnerResolver;
import egovframework.eeum.house.service.house.HouseReviewGuard;
import egovframework.eeum.house.service.house.dao.HouseMapper;
import egovframework.eeum.house.service.room.dao.RoomClimateFacilityMapper;
import egovframework.eeum.house.service.room.dao.RoomMapper;
import egovframework.eeum.house.service.room.impl.RoomClimateServiceImpl;
import egovframework.eeum.house.vo.HouseVO;
import egovframework.eeum.house.vo.RoomClimateFacilityVO;
import egovframework.eeum.house.vo.RoomVO;
import egovframework.eeum.house.web.dto.RoomClimateSetRequest;

/**
 * 객실 냉난방시설 서비스 검증 — 냉방/난방 분리저장/그룹코드 검증/교체/운영자 owner 서버해석/빈배열 전체해제(DB 없이 mock).
 */
class RoomClimateServiceVerification {

	private static final long MY_PRTNR = 100L;
	private static final long HOUSE = 5L;
	private static final long ROOM = 11L;

	private final RoomClimateFacilityMapper roomClimateFacilityMapper = mock(RoomClimateFacilityMapper.class);
	private final RoomMapper roomMapper = mock(RoomMapper.class);
	private final HouseMapper houseMapper = mock(HouseMapper.class);
	private final PartnerService partnerService = mock(PartnerService.class);
	private final CodeService codeService = mock(CodeService.class);
	private final HouseProxyOwnerResolver proxyResolver = mock(HouseProxyOwnerResolver.class);
	private final HouseReviewGuard reviewGuard = mock(HouseReviewGuard.class);
	private final RoomClimateServiceImpl service = new RoomClimateServiceImpl(
			roomClimateFacilityMapper, roomMapper, houseMapper, partnerService, codeService, proxyResolver, reviewGuard);

	private static AuthUser partner(long userSn) {
		return new AuthUser(userSn, "p@eeum.kr", "김파트너", "PARTNER", "ACTIVE", List.of("HOUSE_MNG"));
	}

	private static AuthUser operator(long userSn) {
		return new AuthUser(userSn, "op@eeum.dev", "운영자", "OPERATOR", "ACTIVE", List.of("HOUSE_PROXY_MNG"));
	}

	private static HouseVO houseLock(long prtnrSn, String delYn) {
		return HouseVO.builder().houseSn(HOUSE).prtnrSn(prtnrSn).delYn(delYn).build();
	}

	private static RoomVO roomLock(String delYn) {
		return RoomVO.builder().roomSn(ROOM).houseSn(HOUSE).delYn(delYn).build();
	}

	private static CodeItemResponse code(String groupCd, String cd) {
		return new CodeItemResponse(groupCd, cd, cd, 1);
	}

	/** 변경계 잠금 경로(houseSn 선확보 → 고택 잠금 → 객실 잠금) 스텁(파트너). */
	private void stubLock(long ownerPrtnr) {
		when(partnerService.findManageablePrtnrSn(7L)).thenReturn(MY_PRTNR);
		when(roomMapper.selectHouseSnByRoom(ROOM)).thenReturn(HOUSE);
		when(houseMapper.selectHouseForUpdate(HOUSE)).thenReturn(houseLock(ownerPrtnr, "N"));
		when(roomMapper.selectRoomForUpdate(ROOM)).thenReturn(roomLock("N"));
	}

	private void stubCodes() {
		when(codeService.list("COOLING_TYPE")).thenReturn(List.of(
				code("COOLING_TYPE", "AIRCON"), code("COOLING_TYPE", "FAN"), code("COOLING_TYPE", "SYSTEM_AIRCON")));
		when(codeService.list("HEATING_TYPE")).thenReturn(List.of(
				code("HEATING_TYPE", "ONDOL"), code("HEATING_TYPE", "BOILER")));
	}

	@Test
	@DisplayName("설정 — 냉방/난방 분리저장(softDelete 후 div별 insert)")
	void setReplacesSplitByDiv() {
		stubLock(MY_PRTNR);
		stubCodes();

		service.setClimate(new RoomClimateSetRequest(ROOM, List.of("AIRCON", "FAN"), List.of("ONDOL")), partner(7));

		verify(roomClimateFacilityMapper).softDeleteActiveByRoom(ROOM, MY_PRTNR, 7L);
		ArgumentCaptor<RoomClimateFacilityVO> cap = ArgumentCaptor.forClass(RoomClimateFacilityVO.class);
		verify(roomClimateFacilityMapper, org.mockito.Mockito.times(3)).insertClimateFacility(cap.capture());
		assertEquals("COOLING", cap.getAllValues().get(0).getFcltyDivCd(), "냉방 div");
		assertEquals("AIRCON", cap.getAllValues().get(0).getFcltyCd());
		assertEquals("COOLING", cap.getAllValues().get(1).getFcltyDivCd());
		assertEquals("FAN", cap.getAllValues().get(1).getFcltyCd());
		assertEquals("HEATING", cap.getAllValues().get(2).getFcltyDivCd(), "난방 div");
		assertEquals("ONDOL", cap.getAllValues().get(2).getFcltyCd());
		assertEquals(7L, cap.getAllValues().get(0).getRgtrSn(), "행위자=파트너 userSn");
	}

	@Test
	@DisplayName("설정 — 난방코드를 cooling 에 넣으면 그룹 불일치 400, insert 진입 안 함")
	void setWrongGroupCode() {
		stubLock(MY_PRTNR);
		stubCodes();

		BusinessException e = assertThrows(BusinessException.class,
				() -> service.setClimate(new RoomClimateSetRequest(ROOM, List.of("ONDOL"), List.of()), partner(7)));
		assertEquals(RoomErrorCode.ROOM_CLIMATE_CD_INVALID, e.getErrorCode());
		verify(roomClimateFacilityMapper, never()).insertClimateFacility(any());
	}

	@Test
	@DisplayName("설정 — 빈 배열 둘 다면 전부 해제(softDelete만, insert 없음)")
	void setEmptyClearsAll() {
		stubLock(MY_PRTNR);
		stubCodes();

		service.setClimate(new RoomClimateSetRequest(ROOM, List.of(), List.of()), partner(7));

		verify(roomClimateFacilityMapper).softDeleteActiveByRoom(ROOM, MY_PRTNR, 7L);
		verify(roomClimateFacilityMapper, never()).insertClimateFacility(any());
	}

	@Test
	@DisplayName("운영자 대행 설정 — owner는 resolveOwnerForRoom 해석, 행위자=운영자 userSn")
	void setAsOperator() {
		when(proxyResolver.resolveOwnerForRoom(eq(ROOM), any())).thenReturn(MY_PRTNR);
		when(roomMapper.selectHouseSnByRoom(ROOM)).thenReturn(HOUSE);
		when(houseMapper.selectHouseForUpdate(HOUSE)).thenReturn(houseLock(MY_PRTNR, "N"));
		when(roomMapper.selectRoomForUpdate(ROOM)).thenReturn(roomLock("N"));
		stubCodes();

		service.setClimateAsOperator(new RoomClimateSetRequest(ROOM, List.of("AIRCON"), List.of("BOILER")), operator(9));

		verify(roomClimateFacilityMapper).softDeleteActiveByRoom(ROOM, MY_PRTNR, 9L); // owner=파트너, 행위자=운영자(9)
		ArgumentCaptor<RoomClimateFacilityVO> cap = ArgumentCaptor.forClass(RoomClimateFacilityVO.class);
		verify(roomClimateFacilityMapper, org.mockito.Mockito.times(2)).insertClimateFacility(cap.capture());
		assertEquals(9L, cap.getAllValues().get(0).getRgtrSn(), "행위자=운영자 userSn");
		verify(partnerService, never()).findManageablePrtnrSn(anyLong());
	}
}
