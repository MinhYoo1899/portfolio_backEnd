package egovframework.eeum.house;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import egovframework.eeum.account.service.partner.PartnerService;
import egovframework.eeum.account.web.dto.PartnerDetailResponse;
import egovframework.eeum.auth.vo.AuthUser;
import egovframework.eeum.base.exception.BusinessException;
import egovframework.eeum.house.exception.HouseErrorCode;
import egovframework.eeum.house.exception.RoomErrorCode;
import egovframework.eeum.house.service.house.HouseProxyOwnerResolver;
import egovframework.eeum.house.service.house.dao.HouseMapper;
import egovframework.eeum.house.service.room.dao.RoomMapper;
import egovframework.eeum.house.vo.HouseVO;

/**
 * 운영자 대행 owner 해석기 검증 — 대상 고택 owner PRTNR_SN 해석 + 승인·활성 게이트(DB 없이 mock).
 */
class HouseProxyOwnerResolverVerification {

	private final HouseMapper houseMapper = mock(HouseMapper.class);
	private final RoomMapper roomMapper = mock(RoomMapper.class);
	private final PartnerService partnerService = mock(PartnerService.class);
	private final HouseProxyOwnerResolver resolver =
			new HouseProxyOwnerResolver(houseMapper, roomMapper, partnerService);

	private static AuthUser operator(long sn) {
		return new AuthUser(sn, "op@eeum.dev", "운영자", "OPERATOR", "ACTIVE", List.of("HOUSE_PROXY_MNG"));
	}

	private static HouseVO house(long prtnrSn, String delYn) {
		return HouseVO.builder().houseSn(5L).prtnrSn(prtnrSn).delYn(delYn).build();
	}

	private static PartnerDetailResponse partner(String aprvStts, String acntStts) {
		return new PartnerDetailResponse("3", "7", "p@eeum.kr", "파트너", acntStts, "상호", "9990001113",
				null, null, null, null, null, null, aprvStts, null, null, null, null, null);
	}

	@Test
	@DisplayName("승인+활성 파트너 고택 → owner PRTNR_SN 반환")
	void resolvesApprovedActive() {
		when(houseMapper.selectHouseDetail(5L)).thenReturn(house(3L, "N"));
		when(partnerService.detail(3L)).thenReturn(partner("APPROVED", "ACTIVE"));
		assertEquals(3L, resolver.resolveOwnerForHouse(5L, operator(9)));
	}

	@Test
	@DisplayName("미존재 고택 → 404")
	void houseNotFound() {
		when(houseMapper.selectHouseDetail(5L)).thenReturn(null);
		assertThrows(BusinessException.class, () -> resolver.resolveOwnerForHouse(5L, operator(9)));
	}

	@Test
	@DisplayName("삭제된 고택 → 404")
	void deletedHouse() {
		when(houseMapper.selectHouseDetail(5L)).thenReturn(house(3L, "Y"));
		assertThrows(BusinessException.class, () -> resolver.resolveOwnerForHouse(5L, operator(9)));
	}

	@Test
	@DisplayName("소유 파트너 정지(SUSPENDED) → 대행 차단")
	void suspendedPartnerBlocked() {
		when(houseMapper.selectHouseDetail(5L)).thenReturn(house(3L, "N"));
		when(partnerService.detail(3L)).thenReturn(partner("APPROVED", "SUSPENDED"));
		assertThrows(BusinessException.class, () -> resolver.resolveOwnerForHouse(5L, operator(9)));
	}

	@Test
	@DisplayName("미인증(operator=null) → 401")
	void unauthenticated() {
		assertThrows(BusinessException.class, () -> resolver.resolveOwnerForHouse(5L, null));
	}

	// ===== resolveOwnerForRoom : roomSn → 부모 houseSn → owner 해석 =====

	@Test
	@DisplayName("객실 → 부모 고택 owner(승인+활성) PRTNR_SN 반환")
	void resolvesRoomOwner() {
		when(roomMapper.selectHouseSnByRoom(11L)).thenReturn(5L);
		when(houseMapper.selectHouseDetail(5L)).thenReturn(house(3L, "N"));
		when(partnerService.detail(3L)).thenReturn(partner("APPROVED", "ACTIVE"));
		assertEquals(3L, resolver.resolveOwnerForRoom(11L, operator(9)));
	}

	@Test
	@DisplayName("미존재/삭제 객실 → 404(ROOM_NOT_FOUND)")
	void roomNotFound() {
		when(roomMapper.selectHouseSnByRoom(11L)).thenReturn(null);
		BusinessException e =
				assertThrows(BusinessException.class, () -> resolver.resolveOwnerForRoom(11L, operator(9)));
		assertEquals(RoomErrorCode.ROOM_NOT_FOUND, e.getErrorCode());
	}

	@Test
	@DisplayName("객실 — 소유 파트너 정지(SUSPENDED) → 대행 차단")
	void roomSuspendedPartnerBlocked() {
		when(roomMapper.selectHouseSnByRoom(11L)).thenReturn(5L);
		when(houseMapper.selectHouseDetail(5L)).thenReturn(house(3L, "N"));
		when(partnerService.detail(3L)).thenReturn(partner("APPROVED", "SUSPENDED"));
		assertThrows(BusinessException.class, () -> resolver.resolveOwnerForRoom(11L, operator(9)));
	}

	@Test
	@DisplayName("객실 — 미인증(operator=null) → 401")
	void roomUnauthenticated() {
		assertThrows(BusinessException.class, () -> resolver.resolveOwnerForRoom(11L, null));
	}

	// ===== 사전점검 전용(resolveOwnerForHouseForPublishCheck) — 승인/활성 게이트 미적용 =====

	@Test
	@DisplayName("사전점검 — 정지/미승인 파트너 고택도 owner 반환(승인게이트 미적용·partnerService 미조회)")
	void forPublishCheck_noApprovalGate() {
		when(houseMapper.selectHouseDetail(5L)).thenReturn(house(3L, "N"));
		assertEquals(3L, resolver.resolveOwnerForHouseForPublishCheck(5L, operator(9)));
		// 승인/활성 검사를 하지 않음 → PARTNER_NOT_APPROVED 는 HousePublishValidator.evaluate 가 수집
		verify(partnerService, never()).detail(anyLong());
	}

	@Test
	@DisplayName("사전점검 — 미존재 고택 → 404")
	void forPublishCheck_houseNotFound() {
		when(houseMapper.selectHouseDetail(5L)).thenReturn(null);
		BusinessException e = assertThrows(BusinessException.class,
				() -> resolver.resolveOwnerForHouseForPublishCheck(5L, operator(9)));
		assertEquals(HouseErrorCode.HOUSE_NOT_FOUND, e.getErrorCode());
	}

	@Test
	@DisplayName("사전점검 — 삭제 고택 → 404(HOUSE_NOT_FOUND)")
	void forPublishCheck_deletedHouse() {
		when(houseMapper.selectHouseDetail(5L)).thenReturn(house(3L, "Y"));
		BusinessException e = assertThrows(BusinessException.class,
				() -> resolver.resolveOwnerForHouseForPublishCheck(5L, operator(9)));
		assertEquals(HouseErrorCode.HOUSE_NOT_FOUND, e.getErrorCode());
	}

	@Test
	@DisplayName("사전점검 — 소유자 미상(prtnrSn null) 고택 → 404(HOUSE_NOT_FOUND)")
	void forPublishCheck_ownerMissing() {
		when(houseMapper.selectHouseDetail(5L)).thenReturn(HouseVO.builder().houseSn(5L).delYn("N").build());
		BusinessException e = assertThrows(BusinessException.class,
				() -> resolver.resolveOwnerForHouseForPublishCheck(5L, operator(9)));
		assertEquals(HouseErrorCode.HOUSE_NOT_FOUND, e.getErrorCode());
	}

	@Test
	@DisplayName("사전점검 — 미인증(operator=null) → 401")
	void forPublishCheck_unauthenticated() {
		assertThrows(BusinessException.class, () -> resolver.resolveOwnerForHouseForPublishCheck(5L, null));
	}
}
