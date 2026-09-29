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
import egovframework.eeum.house.exception.HouseErrorCode;
import egovframework.eeum.house.service.amenity.dao.AmenityMapper;
import egovframework.eeum.house.service.amenity.dao.HouseAmenityMapper;
import egovframework.eeum.house.service.amenity.impl.HouseAmenityServiceImpl;
import egovframework.eeum.house.service.house.HouseProxyOwnerResolver;
import egovframework.eeum.house.service.house.HouseReviewGuard;
import egovframework.eeum.house.service.house.dao.HouseMapper;
import egovframework.eeum.house.vo.HouseVO;
import egovframework.eeum.house.web.dto.HouseAmenitySetRequest;

/**
 * 고택-편의시설 매핑 서비스 검증 — 소유권 격리/유효성(활성·승인·고택범위)/집합 교체 동기화(DB 없이 mock).
 */
class HouseAmenityServiceVerification {

	private static final long MY_PRTNR = 100L;
	private static final long HOUSE = 5L;
	/** 고택 매핑 허용 적용범위(서비스 상수와 동일). */
	private static final List<String> HOUSE_SCOPES = List.of("HOUSE", "BOTH");

	private final HouseAmenityMapper haMapper = mock(HouseAmenityMapper.class);
	private final AmenityMapper amenityMapper = mock(AmenityMapper.class);
	private final HouseMapper houseMapper = mock(HouseMapper.class);
	private final PartnerService partnerService = mock(PartnerService.class);
	private final HouseProxyOwnerResolver proxyResolver = mock(HouseProxyOwnerResolver.class);
	private final HouseReviewGuard reviewGuard = mock(HouseReviewGuard.class);
	private final HouseAmenityServiceImpl service = new HouseAmenityServiceImpl(
			haMapper, amenityMapper, houseMapper, partnerService, proxyResolver, reviewGuard);

	private static AuthUser partner(long userSn) {
		return new AuthUser(userSn, "p@eeum.kr", "김파트너", "PARTNER", "ACTIVE", List.of("HOUSE_MNG"));
	}

	private static HouseVO house(long prtnrSn, String delYn) {
		return HouseVO.builder().houseSn(HOUSE).prtnrSn(prtnrSn).delYn(delYn).build();
	}

	@Test
	@DisplayName("설정 — 신규 항목만 삽입, 보존 외 항목 해제(집합 교체)")
	void setReplacesSet() {
		when(partnerService.findManageablePrtnrSn(7L)).thenReturn(MY_PRTNR);
		when(houseMapper.selectHouseForUpdate(HOUSE)).thenReturn(house(MY_PRTNR, "N"));
		when(amenityMapper.countActiveScopedByIds(List.of(10L, 20L), HOUSE_SCOPES)).thenReturn(2);
		when(haMapper.selectActiveAmenitySnsByHouse(HOUSE)).thenReturn(List.of(10L)); // 10 기존, 20 신규

		service.setAmenities(new HouseAmenitySetRequest(HOUSE, List.of(10L, 20L)), partner(7));

		verify(haMapper).softDeleteHouseAmenitiesNotIn(HOUSE, MY_PRTNR, List.of(10L, 20L), 7L);
		verify(haMapper).insertHouseAmenity(HOUSE, 20L, 7L);
		verify(haMapper, never()).insertHouseAmenity(eq(HOUSE), eq(10L), anyLong());
	}

	@Test
	@DisplayName("설정 — 타 소유 고택은 404")
	void setForeignHouse() {
		when(partnerService.findManageablePrtnrSn(7L)).thenReturn(MY_PRTNR);
		when(houseMapper.selectHouseForUpdate(HOUSE)).thenReturn(house(999L, "N"));
		BusinessException e = assertThrows(BusinessException.class,
				() -> service.setAmenities(new HouseAmenitySetRequest(HOUSE, List.of(10L)), partner(7)));
		assertEquals(HouseErrorCode.HOUSE_NOT_FOUND, e.getErrorCode());
		verify(haMapper, never()).insertHouseAmenity(anyLong(), anyLong(), anyLong());
	}

	@Test
	@DisplayName("설정 — 비활성/미승인/객실전용/임의 편의시설 포함 시 404(검증 실패)")
	void setInvalidAmenity() {
		when(partnerService.findManageablePrtnrSn(7L)).thenReturn(MY_PRTNR);
		when(houseMapper.selectHouseForUpdate(HOUSE)).thenReturn(house(MY_PRTNR, "N"));
		when(amenityMapper.countActiveScopedByIds(List.of(10L, 99L), HOUSE_SCOPES)).thenReturn(1); // 99 무효

		BusinessException e = assertThrows(BusinessException.class,
				() -> service.setAmenities(new HouseAmenitySetRequest(HOUSE, List.of(10L, 99L)), partner(7)));
		assertEquals(AmenityErrorCode.AMENITY_NOT_FOUND, e.getErrorCode());
		verify(haMapper, never()).softDeleteHouseAmenitiesNotIn(anyLong(), anyLong(), anyList(), anyLong());
	}

	@Test
	@DisplayName("설정 — 빈 배열이면 전부 해제(검증/삽입 없음)")
	void setEmptyClearsAll() {
		when(partnerService.findManageablePrtnrSn(7L)).thenReturn(MY_PRTNR);
		when(houseMapper.selectHouseForUpdate(HOUSE)).thenReturn(house(MY_PRTNR, "N"));
		when(haMapper.selectActiveAmenitySnsByHouse(HOUSE)).thenReturn(List.of(10L));

		service.setAmenities(new HouseAmenitySetRequest(HOUSE, List.of()), partner(7));

		verify(haMapper).softDeleteHouseAmenitiesNotIn(HOUSE, MY_PRTNR, List.of(), 7L);
		verify(amenityMapper, never()).countActiveScopedByIds(anyList(), anyList());
		verify(haMapper, never()).insertHouseAmenity(anyLong(), anyLong(), anyLong());
	}

	@Test
	@DisplayName("조회 — 타 소유 고택은 404")
	void listForeignHouse() {
		when(partnerService.findManageablePrtnrSn(7L)).thenReturn(MY_PRTNR);
		when(houseMapper.selectHouseDetail(HOUSE)).thenReturn(house(999L, "N"));
		BusinessException e = assertThrows(BusinessException.class, () -> service.listByHouse(HOUSE, partner(7)));
		assertEquals(HouseErrorCode.HOUSE_NOT_FOUND, e.getErrorCode());
		verify(haMapper, never()).selectActiveAmenitiesByHouse(anyLong());
	}

	// ===== 운영자 대행(HOUSE_PROXY_MNG) — owner=resolver 해석, 행위자=운영자 =====

	private static AuthUser operator(long userSn) {
		return new AuthUser(userSn, "op@eeum.dev", "운영자", "OPERATOR", "ACTIVE", List.of("HOUSE_PROXY_MNG"));
	}

	@Test
	@DisplayName("운영자 대행 설정 — owner는 resolver 해석, 행위자=운영자 userSn")
	void setAsOperator() {
		when(proxyResolver.resolveOwnerForHouse(eq(HOUSE), any())).thenReturn(MY_PRTNR);
		when(houseMapper.selectHouseForUpdate(HOUSE)).thenReturn(house(MY_PRTNR, "N"));
		when(amenityMapper.countActiveScopedByIds(List.of(10L, 20L), HOUSE_SCOPES)).thenReturn(2);
		when(haMapper.selectActiveAmenitySnsByHouse(HOUSE)).thenReturn(List.of(10L));

		service.setAmenitiesAsOperator(new HouseAmenitySetRequest(HOUSE, List.of(10L, 20L)), operator(9));

		verify(haMapper).softDeleteHouseAmenitiesNotIn(HOUSE, MY_PRTNR, List.of(10L, 20L), 9L); // owner=파트너, 행위자=운영자(9)
		verify(haMapper).insertHouseAmenity(HOUSE, 20L, 9L);
		verify(partnerService, never()).findManageablePrtnrSn(anyLong()); // 운영자는 토큰 prtnrSn 해석 안 함
	}

	@Test
	@DisplayName("운영자 대행 설정 — 정지(비승인/비활성) 파트너 고택은 resolver가 차단(매핑 진입 안 함)")
	void setAsOperatorBlockedWhenOwnerSuspended() {
		when(proxyResolver.resolveOwnerForHouse(eq(HOUSE), any()))
				.thenThrow(BusinessException.conflict("승인된 활성 파트너의 고택만 대행 관리할 수 있습니다."));

		assertThrows(BusinessException.class,
				() -> service.setAmenitiesAsOperator(new HouseAmenitySetRequest(HOUSE, List.of(10L)), operator(9)));
		verify(haMapper, never()).softDeleteHouseAmenitiesNotIn(anyLong(), anyLong(), anyList(), anyLong());
	}
}
