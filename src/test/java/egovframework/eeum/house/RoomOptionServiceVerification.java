package egovframework.eeum.house;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import egovframework.eeum.account.service.partner.PartnerService;
import egovframework.eeum.auth.vo.AuthUser;
import egovframework.eeum.base.exception.BusinessException;
import egovframework.eeum.house.exception.RoomErrorCode;
import egovframework.eeum.house.service.house.HouseProxyOwnerResolver;
import egovframework.eeum.house.service.house.HouseReviewGuard;
import egovframework.eeum.house.service.house.dao.HouseMapper;
import egovframework.eeum.house.service.room.dao.RoomMapper;
import egovframework.eeum.house.service.room.dao.RoomOptionMapper;
import egovframework.eeum.house.service.room.impl.RoomOptionServiceImpl;
import egovframework.eeum.house.vo.HouseVO;
import egovframework.eeum.house.vo.RoomOptionVO;
import egovframework.eeum.house.vo.RoomVO;
import egovframework.eeum.house.web.dto.RoomOptionSetRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

/**
 * 객실 추가옵션 서비스 검증 — 부모 객실/고택 소유권 격리/옵션명 중복/금액 제약/교체/운영자 owner 서버해석(DB 없이 mock).
 */
class RoomOptionServiceVerification {

	private static final long MY_PRTNR = 100L;
	private static final long HOUSE = 5L;
	private static final long ROOM = 11L;

	private final RoomOptionMapper roomOptionMapper = mock(RoomOptionMapper.class);
	private final RoomMapper roomMapper = mock(RoomMapper.class);
	private final HouseMapper houseMapper = mock(HouseMapper.class);
	private final PartnerService partnerService = mock(PartnerService.class);
	private final HouseProxyOwnerResolver proxyResolver = mock(HouseProxyOwnerResolver.class);
	private final HouseReviewGuard reviewGuard = mock(HouseReviewGuard.class);
	private final RoomOptionServiceImpl service = new RoomOptionServiceImpl(
			roomOptionMapper, roomMapper, houseMapper, partnerService, proxyResolver, reviewGuard);

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

	private static RoomOptionSetRequest.Item item(String nm, int amt) {
		return new RoomOptionSetRequest.Item(nm, amt);
	}

	/** 변경계 잠금 경로(houseSn 선확보 → 고택 잠금 → 객실 잠금) 스텁(파트너). */
	private void stubLock(long ownerPrtnr) {
		when(partnerService.findManageablePrtnrSn(7L)).thenReturn(MY_PRTNR);
		when(roomMapper.selectHouseSnByRoom(ROOM)).thenReturn(HOUSE);
		when(houseMapper.selectHouseForUpdate(HOUSE)).thenReturn(houseLock(ownerPrtnr, "N"));
		when(roomMapper.selectRoomForUpdate(ROOM)).thenReturn(roomLock("N"));
	}

	@Test
	@DisplayName("설정 — 활성 비움 후 순서대로 재삽입(교체, sortSeq=index, trim)")
	void setReplaces() {
		stubLock(MY_PRTNR);

		service.setOptions(new RoomOptionSetRequest(ROOM,
				List.of(item(" 조식 추가 ", 15000), item("바비큐 세트", 30000))), partner(7));

		verify(roomOptionMapper).softDeleteActiveByRoom(ROOM, MY_PRTNR, 7L);
		ArgumentCaptor<RoomOptionVO> cap = ArgumentCaptor.forClass(RoomOptionVO.class);
		verify(roomOptionMapper, org.mockito.Mockito.times(2)).insertOption(cap.capture());
		assertEquals("조식 추가", cap.getAllValues().get(0).getOptionNm(), "옵션명 trim");
		assertEquals(0, cap.getAllValues().get(0).getSortSeq(), "정렬순서=인덱스");
		assertEquals(1, cap.getAllValues().get(1).getSortSeq());
		assertEquals(7L, cap.getAllValues().get(0).getRgtrSn(), "행위자=파트너 userSn");
	}

	@Test
	@DisplayName("설정 — 옵션명 중복(trim/대소문자 무시)은 400")
	void setDuplicateName() {
		stubLock(MY_PRTNR);
		BusinessException e = assertThrows(BusinessException.class,
				() -> service.setOptions(new RoomOptionSetRequest(ROOM,
						List.of(item("Spa", 1000), item(" spa ", 2000))), partner(7)));
		assertEquals(RoomErrorCode.ROOM_OPTION_DUPLICATE, e.getErrorCode());
		verify(roomOptionMapper, never()).insertOption(any());
	}

	@Test
	@DisplayName("설정 — 타 소유 객실은 404, 옵션 진입 안 함")
	void setForeignRoom() {
		when(partnerService.findManageablePrtnrSn(7L)).thenReturn(MY_PRTNR);
		when(roomMapper.selectHouseSnByRoom(ROOM)).thenReturn(HOUSE);
		when(houseMapper.selectHouseForUpdate(HOUSE)).thenReturn(houseLock(999L, "N"));

		BusinessException e = assertThrows(BusinessException.class,
				() -> service.setOptions(new RoomOptionSetRequest(ROOM, List.of(item("조식", 1000))), partner(7)));
		assertEquals(RoomErrorCode.ROOM_NOT_FOUND, e.getErrorCode());
		verify(roomOptionMapper, never()).insertOption(any());
	}

	@Test
	@DisplayName("금액 검증 — 음수 추가금액은 Bean Validation(@PositiveOrZero) 위반")
	void negativeAmountRejectedByValidation() {
		try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
			Validator validator = factory.getValidator();
			Set<ConstraintViolation<RoomOptionSetRequest>> violations =
					validator.validate(new RoomOptionSetRequest(ROOM, List.of(item("조식", -1))));
			assertFalse(violations.isEmpty(), "음수 금액은 검증 위반이어야 함");
		}
	}

	@Test
	@DisplayName("운영자 대행 설정 — owner는 resolveOwnerForRoom 해석, 행위자=운영자 userSn")
	void setAsOperator() {
		when(proxyResolver.resolveOwnerForRoom(eq(ROOM), any())).thenReturn(MY_PRTNR);
		when(roomMapper.selectHouseSnByRoom(ROOM)).thenReturn(HOUSE);
		when(houseMapper.selectHouseForUpdate(HOUSE)).thenReturn(houseLock(MY_PRTNR, "N"));
		when(roomMapper.selectRoomForUpdate(ROOM)).thenReturn(roomLock("N"));

		service.setOptionsAsOperator(new RoomOptionSetRequest(ROOM, List.of(item("조식", 10000))), operator(9));

		verify(roomOptionMapper).softDeleteActiveByRoom(ROOM, MY_PRTNR, 9L); // owner=파트너, 행위자=운영자(9)
		ArgumentCaptor<RoomOptionVO> cap = ArgumentCaptor.forClass(RoomOptionVO.class);
		verify(roomOptionMapper).insertOption(cap.capture());
		assertEquals(9L, cap.getValue().getRgtrSn(), "행위자=운영자 userSn");
		verify(partnerService, never()).findManageablePrtnrSn(anyLong());
	}

	@Test
	@DisplayName("설정 — 앱검사 통과했으나 DB 콜레이션(ai_ci) 충돌은 500이 아니라 400(ROOM_OPTION_DUPLICATE)")
	void setDbCollisionMappedTo400() {
		stubLock(MY_PRTNR);
		org.mockito.Mockito.doThrow(new org.springframework.dao.DuplicateKeyException("ai_ci collision"))
				.when(roomOptionMapper).insertOption(any());

		BusinessException e = assertThrows(BusinessException.class,
				() -> service.setOptions(new RoomOptionSetRequest(ROOM, List.of(item("Café", 1000))), partner(7)));
		assertEquals(RoomErrorCode.ROOM_OPTION_DUPLICATE, e.getErrorCode());
	}
}
