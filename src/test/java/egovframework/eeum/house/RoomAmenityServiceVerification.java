package egovframework.eeum.house;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import egovframework.eeum.account.service.partner.PartnerService;
import egovframework.eeum.auth.vo.AuthUser;
import egovframework.eeum.base.exception.BusinessException;
import egovframework.eeum.house.exception.AmenityErrorCode;
import egovframework.eeum.house.service.amenity.dao.AmenityMapper;
import egovframework.eeum.house.service.house.HouseProxyOwnerResolver;
import egovframework.eeum.house.service.house.HouseReviewGuard;
import egovframework.eeum.house.service.house.dao.HouseMapper;
import egovframework.eeum.house.service.room.dao.RoomAmenityMapper;
import egovframework.eeum.house.service.room.dao.RoomMapper;
import egovframework.eeum.house.service.room.impl.RoomAmenityServiceImpl;
import egovframework.eeum.house.vo.HouseVO;
import egovframework.eeum.house.vo.RoomVO;
import egovframework.eeum.house.web.dto.RoomAmenitySetRequest;

/**
 * 객실-편의시설 매핑 서비스 검증 — 소유권 격리/카탈로그 유효성(활성·범위)/커스텀(직접입력)/집합 교체/운영자 owner 서버해석(DB 없이 mock).
 * 승인 게이트 없음. 선택 전체 검증(이미 매핑된 항목도 재검증 — 우회 차단).
 */
class RoomAmenityServiceVerification {

	private static final long MY_PRTNR = 100L;
	private static final long HOUSE = 5L;
	private static final long ROOM = 11L;
	/** 객실 매핑 허용 적용범위(서비스 상수와 동일). */
	private static final List<String> ROOM_SCOPES = List.of("ROOM", "BOTH");

	private final RoomAmenityMapper roomAmenityMapper = mock(RoomAmenityMapper.class);
	private final AmenityMapper amenityMapper = mock(AmenityMapper.class);
	private final RoomMapper roomMapper = mock(RoomMapper.class);
	private final HouseMapper houseMapper = mock(HouseMapper.class);
	private final PartnerService partnerService = mock(PartnerService.class);
	private final HouseProxyOwnerResolver proxyResolver = mock(HouseProxyOwnerResolver.class);
	private final HouseReviewGuard reviewGuard = mock(HouseReviewGuard.class);
	private final RoomAmenityServiceImpl service = new RoomAmenityServiceImpl(
			roomAmenityMapper, amenityMapper, roomMapper, houseMapper, partnerService, proxyResolver, reviewGuard);

	/** customAmenityNms 없는 일반 설정요청. */
	private static RoomAmenitySetRequest req(List<Long> amenitySns) {
		return new RoomAmenitySetRequest(ROOM, amenitySns, List.of());
	}

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

	/** 변경계 잠금 경로(houseSn 선확보 → 고택 잠금 → 객실 잠금) 스텁(파트너). */
	private void stubLock(long ownerPrtnr) {
		when(partnerService.findManageablePrtnrSn(7L)).thenReturn(MY_PRTNR);
		when(roomMapper.selectHouseSnByRoom(ROOM)).thenReturn(HOUSE);
		when(houseMapper.selectHouseForUpdate(HOUSE)).thenReturn(houseLock(ownerPrtnr, "N"));
		when(roomMapper.selectRoomForUpdate(ROOM)).thenReturn(roomLock("N"));
	}

	@Test
	@DisplayName("설정 — 선택 전체(이미 매핑 포함) 활성·범위 검증 후 신규만 삽입, 보존 외 해제(집합 교체)")
	void setReplacesSet() {
		stubLock(MY_PRTNR);
		when(amenityMapper.countActiveScopedByIds(List.of(10L, 20L), ROOM_SCOPES)).thenReturn(2); // 선택 전체 검증
		when(roomAmenityMapper.selectActiveAmenitySnsByRoom(ROOM)).thenReturn(List.of(10L)); // 10 기존, 20 신규

		service.setAmenities(req(List.of(10L, 20L)), partner(7));

		verify(roomAmenityMapper).softDeleteRoomAmenitiesNotIn(ROOM, MY_PRTNR, List.of(10L, 20L), 7L);
		verify(roomAmenityMapper).insertRoomAmenity(ROOM, 20L, 7L);
		verify(roomAmenityMapper, never()).insertRoomAmenity(eq(ROOM), eq(10L), anyLong());
	}

	@Test
	@DisplayName("설정 — 비활성/타범위/임의 편의시설 포함 시 404(검증 실패, 매핑 미진입)")
	void setInvalidAmenity() {
		stubLock(MY_PRTNR);
		// 선택 전체 검증. 99 무효라 개수 불일치(1≠2)
		when(amenityMapper.countActiveScopedByIds(List.of(10L, 99L), ROOM_SCOPES)).thenReturn(1);

		BusinessException e = assertThrows(BusinessException.class,
				() -> service.setAmenities(req(List.of(10L, 99L)), partner(7)));
		assertEquals(AmenityErrorCode.AMENITY_NOT_FOUND, e.getErrorCode());
		verify(roomAmenityMapper, never()).softDeleteRoomAmenitiesNotIn(anyLong(), anyLong(), anyList(), anyLong());
	}

	@Test
	@DisplayName("설정 — 이미 매핑된 항목이라도 비활성/타범위로 바뀌었으면 404(ID 재전송 우회 차단)")
	void setKeepInvalidExistingRejected() {
		stubLock(MY_PRTNR);
		// 10이 이미 매핑돼 있어도(예: HOUSE 전용으로 변경) 선택 전체 검증에서 0건 → 불일치(0≠1)
		when(amenityMapper.countActiveScopedByIds(List.of(10L), ROOM_SCOPES)).thenReturn(0);

		BusinessException e = assertThrows(BusinessException.class,
				() -> service.setAmenities(req(List.of(10L)), partner(7)));
		assertEquals(AmenityErrorCode.AMENITY_NOT_FOUND, e.getErrorCode());
		verify(roomAmenityMapper, never()).softDeleteRoomAmenitiesNotIn(anyLong(), anyLong(), anyList(), anyLong());
	}

	@Test
	@DisplayName("설정 — 타 소유 객실은 404, 매핑 진입 안 함")
	void setForeignRoom() {
		when(partnerService.findManageablePrtnrSn(7L)).thenReturn(MY_PRTNR);
		when(roomMapper.selectHouseSnByRoom(ROOM)).thenReturn(HOUSE);
		when(houseMapper.selectHouseForUpdate(HOUSE)).thenReturn(houseLock(999L, "N"));

		assertThrows(BusinessException.class, () -> service.setAmenities(req(List.of(10L)), partner(7)));
		verify(roomAmenityMapper, never()).insertRoomAmenity(anyLong(), anyLong(), anyLong());
	}

	@Test
	@DisplayName("설정 — 빈 배열이면 전부 해제(검증/삽입 없음)")
	void setEmptyClearsAll() {
		stubLock(MY_PRTNR);
		when(roomAmenityMapper.selectActiveAmenitySnsByRoom(ROOM)).thenReturn(List.of(10L));

		service.setAmenities(req(List.of()), partner(7));

		verify(roomAmenityMapper).softDeleteRoomAmenitiesNotIn(ROOM, MY_PRTNR, List.of(), 7L);
		verify(amenityMapper, never()).countActiveScopedByIds(anyList(), anyList());
		verify(roomAmenityMapper, never()).insertRoomAmenity(anyLong(), anyLong(), anyLong());
	}

	@Test
	@DisplayName("설정 — 커스텀(직접입력)은 즉시 활성 등록 후 매핑에 합류")
	void setWithCustom() {
		stubLock(MY_PRTNR);
		when(amenityMapper.countActiveScopedByIds(List.of(10L), ROOM_SCOPES)).thenReturn(1); // 선택 검증
		// 커스텀 "다도 공간" → insert-or-get 으로 신규 amenitySn=77 부여 + 매핑가능 검증 통과
		org.mockito.Mockito.doAnswer(inv -> {
			inv.getArgument(0, egovframework.eeum.house.vo.AmenityVO.class).setAmenitySn(77L);
			return null;
		}).when(amenityMapper).insertCustomAmenity(any());
		when(amenityMapper.countActiveScopedByIds(List.of(77L), ROOM_SCOPES)).thenReturn(1);
		when(roomAmenityMapper.selectActiveAmenitySnsByRoom(ROOM)).thenReturn(List.of());

		service.setAmenities(new RoomAmenitySetRequest(ROOM, List.of(10L), List.of("다도 공간")), partner(7));

		verify(amenityMapper).insertCustomAmenity(any());
		verify(roomAmenityMapper).softDeleteRoomAmenitiesNotIn(ROOM, MY_PRTNR, List.of(10L, 77L), 7L);
		verify(roomAmenityMapper).insertRoomAmenity(ROOM, 10L, 7L);
		verify(roomAmenityMapper).insertRoomAmenity(ROOM, 77L, 7L);
	}

	@Test
	@DisplayName("운영자 대행 설정 — owner는 resolveOwnerForRoom 해석, 행위자=운영자 userSn, 토큰 prtnrSn 미사용")
	void setAsOperator() {
		when(proxyResolver.resolveOwnerForRoom(eq(ROOM), any())).thenReturn(MY_PRTNR);
		when(roomMapper.selectHouseSnByRoom(ROOM)).thenReturn(HOUSE);
		when(houseMapper.selectHouseForUpdate(HOUSE)).thenReturn(houseLock(MY_PRTNR, "N"));
		when(roomMapper.selectRoomForUpdate(ROOM)).thenReturn(roomLock("N"));
		when(amenityMapper.countActiveScopedByIds(List.of(10L, 20L), ROOM_SCOPES)).thenReturn(2);
		when(roomAmenityMapper.selectActiveAmenitySnsByRoom(ROOM)).thenReturn(List.of(10L));

		service.setAmenitiesAsOperator(req(List.of(10L, 20L)), operator(9));

		verify(roomAmenityMapper).softDeleteRoomAmenitiesNotIn(ROOM, MY_PRTNR, List.of(10L, 20L), 9L);
		verify(roomAmenityMapper).insertRoomAmenity(ROOM, 20L, 9L); // owner=파트너, 행위자=운영자(9)
		verify(partnerService, never()).findManageablePrtnrSn(anyLong());
	}
}
