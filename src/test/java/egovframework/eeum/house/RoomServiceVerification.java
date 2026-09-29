package egovframework.eeum.house;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.dao.DuplicateKeyException;

import egovframework.eeum.account.service.partner.PartnerService;
import egovframework.eeum.auth.vo.AuthUser;
import egovframework.eeum.base.exception.BusinessException;
import egovframework.eeum.code.service.code.CodeService;
import egovframework.eeum.code.web.dto.CodeItemResponse;
import egovframework.eeum.house.exception.HouseErrorCode;
import egovframework.eeum.house.exception.RoomErrorCode;
import egovframework.eeum.house.service.house.HouseProxyOwnerResolver;
import egovframework.eeum.house.service.house.HousePublishGuard;
import egovframework.eeum.house.service.house.HouseReviewGuard;
import egovframework.eeum.house.service.house.ReadinessSnapshot;
import egovframework.eeum.house.service.house.dao.HouseMapper;
import egovframework.eeum.house.service.room.dao.RoomAmenityMapper;
import egovframework.eeum.house.service.room.dao.RoomBeddingMapper;
import egovframework.eeum.house.service.room.dao.RoomMapper;
import egovframework.eeum.house.service.room.dao.RoomClimateFacilityMapper;
import egovframework.eeum.house.service.room.dao.RoomOptionMapper;
import egovframework.eeum.house.service.room.impl.RoomServiceImpl;
import egovframework.eeum.house.vo.HouseVO;
import egovframework.eeum.house.vo.RoomVO;
import egovframework.eeum.house.web.dto.RoomBeddingSetRequest;
import egovframework.eeum.house.web.dto.RoomInsertRequest;
import egovframework.eeum.house.web.dto.RoomStatusChangeRequest;
import egovframework.eeum.house.web.dto.RoomUpdateRequest;

/**
 * 객실 서비스 검증 — 부모 고택 소유권 격리/인원 정합/상태가드(SUSPENDED)/중복명/락 순서(HOUSE→ROOM)(DB 없이 mock).
 */
class RoomServiceVerification {

	private static final long MY_PRTNR = 100L;
	private static final long HOUSE = 5L;
	private static final long ROOM = 11L;

	private final RoomMapper roomMapper = mock(RoomMapper.class);
	private final RoomBeddingMapper roomBeddingMapper = mock(RoomBeddingMapper.class);
	private final RoomAmenityMapper roomAmenityMapper = mock(RoomAmenityMapper.class);
	private final RoomOptionMapper roomOptionMapper = mock(RoomOptionMapper.class);
	private final RoomClimateFacilityMapper roomClimateFacilityMapper = mock(RoomClimateFacilityMapper.class);
	private final HouseMapper houseMapper = mock(HouseMapper.class);
	private final PartnerService partnerService = mock(PartnerService.class);
	private final CodeService codeService = mock(CodeService.class);
	private final HouseProxyOwnerResolver proxyResolver = mock(HouseProxyOwnerResolver.class);
	private final HousePublishGuard publishGuard = mock(HousePublishGuard.class);
	private final HouseReviewGuard reviewGuard = mock(HouseReviewGuard.class);
	private final RoomServiceImpl service = new RoomServiceImpl(
			roomMapper, roomBeddingMapper, roomAmenityMapper, roomOptionMapper, roomClimateFacilityMapper,
			houseMapper, partnerService, codeService, proxyResolver, publishGuard, reviewGuard);

	private static AuthUser partner(long userSn) {
		return new AuthUser(userSn, "p@eeum.kr", "김파트너", "PARTNER", "ACTIVE", List.of("HOUSE_MNG"));
	}

	private static HouseVO houseLock(long prtnrSn, String delYn) {
		return HouseVO.builder().houseSn(HOUSE).prtnrSn(prtnrSn).delYn(delYn).build();
	}

	private static RoomVO roomLock(String stts, String delYn) {
		return RoomVO.builder().roomSn(ROOM).houseSn(HOUSE).roomSttsCd(stts).delYn(delYn).build();
	}

	private static RoomInsertRequest insertReq(int bsc, int max) {
		return new RoomInsertRequest(HOUSE, "안채", bsc, max, null, null, null, null, null, null);
	}

	/** 변경계 잠금 경로(houseSn 선확보 → 고택 잠금 → 객실 잠금) 스텁. */
	private void stubLock(long ownerPrtnr, String roomStts) {
		when(partnerService.findManageablePrtnrSn(7L)).thenReturn(MY_PRTNR);
		when(roomMapper.selectHouseSnByRoom(ROOM)).thenReturn(HOUSE);
		when(houseMapper.selectHouseForUpdate(HOUSE)).thenReturn(houseLock(ownerPrtnr, "N"));
		when(roomMapper.selectRoomForUpdate(ROOM)).thenReturn(roomLock(roomStts, "N"));
	}

	@Test
	@DisplayName("등록 — 부모 고택 본인 소유면 생성, 행위자=USER_SN, 재고 기본 1")
	void insertSuccess() {
		when(partnerService.findManageablePrtnrSn(7L)).thenReturn(MY_PRTNR);
		when(houseMapper.selectHouseForUpdate(HOUSE)).thenReturn(houseLock(MY_PRTNR, "N"));
		doAnswer(inv -> {
			inv.getArgument(0, RoomVO.class).setRoomSn(ROOM);
			return null;
		}).when(roomMapper).insertRoom(any(RoomVO.class));
		when(roomMapper.selectRoomDetail(ROOM))
				.thenReturn(RoomVO.builder().roomSn(ROOM).houseSn(HOUSE).ownerPrtnrSn(MY_PRTNR).build());
		ArgumentCaptor<RoomVO> cap = ArgumentCaptor.forClass(RoomVO.class);

		service.insert(insertReq(2, 4), partner(7));

		verify(roomMapper).insertRoom(cap.capture());
		assertEquals(HOUSE, cap.getValue().getHouseSn());
		assertEquals(7L, cap.getValue().getRgtrSn(), "행위자=로그인 USER_SN");
		assertEquals(1, cap.getValue().getDfltStockQty(), "재고 기본 1");
	}

	@Test
	@DisplayName("편집복귀 계약 증명(운영자 대행) — REVIEW_REQUESTED 부모 고택에 객실 등록은 안 막힘, reviewGuard 위임")
	void insertAsOperatorUnderReviewNotBlocked() {
		AuthUser op = new AuthUser(9L, "op@eeum.kr", "운영자", "OPERATOR", "ACTIVE", List.of("HOUSE_PROXY_MNG"));
		when(proxyResolver.resolveOwnerForHouse(HOUSE, op)).thenReturn(MY_PRTNR);
		when(houseMapper.selectHouseForUpdate(HOUSE)).thenReturn(
				HouseVO.builder().houseSn(HOUSE).prtnrSn(MY_PRTNR).houseSttsCd("REVIEW_REQUESTED").delYn("N").build());
		doAnswer(inv -> {
			inv.getArgument(0, RoomVO.class).setRoomSn(ROOM);
			return null;
		}).when(roomMapper).insertRoom(any(RoomVO.class));
		when(roomMapper.selectRoomDetail(ROOM))
				.thenReturn(RoomVO.builder().roomSn(ROOM).houseSn(HOUSE).ownerPrtnrSn(MY_PRTNR).build());

		service.insertAsOperator(insertReq(2, 4), op);

		verify(roomMapper).insertRoom(any(RoomVO.class));       // 편집 코어 진입(=REVIEW_REQUESTED 안 막힘)
		verify(reviewGuard).markDirtyIfUnderReview(HOUSE, 9L);  // DRAFT 복귀 위임
	}

	@Test
	@DisplayName("등록 — 부모 고택이 타 소유면 404")
	void insertForeignHouse() {
		when(partnerService.findManageablePrtnrSn(7L)).thenReturn(MY_PRTNR);
		when(houseMapper.selectHouseForUpdate(HOUSE)).thenReturn(houseLock(999L, "N"));
		BusinessException e = assertThrows(BusinessException.class, () -> service.insert(insertReq(2, 4), partner(7)));
		assertEquals(HouseErrorCode.HOUSE_NOT_FOUND, e.getErrorCode());
		verify(roomMapper, never()).insertRoom(any());
	}

	@Test
	@DisplayName("등록 — 최대인원 < 기준인원이면 400(PERSON_INVALID)")
	void insertPersonInvalid() {
		when(partnerService.findManageablePrtnrSn(7L)).thenReturn(MY_PRTNR);
		when(houseMapper.selectHouseForUpdate(HOUSE)).thenReturn(houseLock(MY_PRTNR, "N"));
		BusinessException e = assertThrows(BusinessException.class, () -> service.insert(insertReq(4, 2), partner(7)));
		assertEquals(RoomErrorCode.ROOM_PERSON_INVALID, e.getErrorCode());
		verify(roomMapper, never()).insertRoom(any());
	}

	@Test
	@DisplayName("등록 — 같은 고택 내 객실명 중복은 409")
	void insertDuplicateName() {
		when(partnerService.findManageablePrtnrSn(7L)).thenReturn(MY_PRTNR);
		when(houseMapper.selectHouseForUpdate(HOUSE)).thenReturn(houseLock(MY_PRTNR, "N"));
		doAnswer(inv -> {
			throw new DuplicateKeyException("dup");
		}).when(roomMapper).insertRoom(any(RoomVO.class));
		BusinessException e = assertThrows(BusinessException.class, () -> service.insert(insertReq(2, 4), partner(7)));
		assertEquals(RoomErrorCode.ROOM_NAME_DUPLICATE, e.getErrorCode());
	}

	@Test
	@DisplayName("상세 — 타 소유 객실은 404")
	void detailForeignOwner() {
		when(partnerService.findManageablePrtnrSn(7L)).thenReturn(MY_PRTNR);
		when(roomMapper.selectRoomDetail(ROOM))
				.thenReturn(RoomVO.builder().roomSn(ROOM).ownerPrtnrSn(999L).build());
		BusinessException e = assertThrows(BusinessException.class, () -> service.detail(ROOM, partner(7)));
		assertEquals(RoomErrorCode.ROOM_NOT_FOUND, e.getErrorCode());
	}

	@Test
	@DisplayName("수정 — 부모 고택이 타 소유면 404, updateRoom 진입 안 함")
	void updateForeignOwner() {
		when(partnerService.findManageablePrtnrSn(7L)).thenReturn(MY_PRTNR);
		when(roomMapper.selectHouseSnByRoom(ROOM)).thenReturn(HOUSE);
		when(houseMapper.selectHouseForUpdate(HOUSE)).thenReturn(houseLock(999L, "N"));
		BusinessException e = assertThrows(BusinessException.class,
				() -> service.update(new RoomUpdateRequest(ROOM, "수정", 2, 4, null, null, null, null, null, null),
						partner(7)));
		assertEquals(RoomErrorCode.ROOM_NOT_FOUND, e.getErrorCode());
		verify(roomMapper, never()).updateRoom(anyLong(), anyLong(), anyString(), anyInt(), anyInt(), anyInt(),
				anyInt(), any(), any(), any(), any(), anyLong());
	}

	@Test
	@DisplayName("수정 — 성공 시 snapshotIfActive → updateRoom → enforceNoRegression(동일 스냅샷) 순서")
	void updateSuccessAppliesGuardInOrder() {
		stubLock(MY_PRTNR, "ACTIVE");
		when(roomMapper.updateRoom(anyLong(), anyLong(), anyString(), anyInt(), anyInt(), anyInt(),
				anyInt(), any(), any(), any(), any(), anyLong())).thenReturn(1);
		ReadinessSnapshot snap = mock(ReadinessSnapshot.class);
		when(publishGuard.snapshotIfActive(HOUSE, MY_PRTNR)).thenReturn(snap);

		service.update(new RoomUpdateRequest(ROOM, "수정", 2, 4, null, null, null, null, null, null), partner(7));

		InOrder ord = inOrder(publishGuard, roomMapper);
		ord.verify(publishGuard).snapshotIfActive(HOUSE, MY_PRTNR);
		ord.verify(roomMapper).updateRoom(anyLong(), anyLong(), anyString(), anyInt(), anyInt(), anyInt(),
				anyInt(), any(), any(), any(), any(), anyLong());
		ord.verify(publishGuard).enforceNoRegression(HOUSE, MY_PRTNR, snap);
	}

	@Test
	@DisplayName("상태변경 — SUSPENDED 객실은 판매자 자가복구 불가(409)")
	void changeStatusBlockedWhenSuspended() {
		stubLock(MY_PRTNR, "SUSPENDED");
		BusinessException e = assertThrows(BusinessException.class,
				() -> service.changeStatus(new RoomStatusChangeRequest(ROOM, "ACTIVE"), partner(7)));
		assertEquals(RoomErrorCode.ROOM_SUSPENDED, e.getErrorCode());
		verify(roomMapper, never()).updateRoomStatus(anyLong(), anyLong(), anyString(), anyLong());
	}

	@Test
	@DisplayName("상태변경 — 본인 소유 + 비SUSPENDED면 성공")
	void changeStatusSuccess() {
		stubLock(MY_PRTNR, "DRAFT");
		when(roomMapper.updateRoomStatus(ROOM, MY_PRTNR, "ACTIVE", 7L)).thenReturn(1);
		ReadinessSnapshot snap = mock(ReadinessSnapshot.class);
		when(publishGuard.snapshotIfActive(HOUSE, MY_PRTNR)).thenReturn(snap);
		service.changeStatus(new RoomStatusChangeRequest(ROOM, "ACTIVE"), partner(7));
		// snapshotIfActive → mutation → enforceNoRegression(동일 스냅샷) 순서
		InOrder ord = inOrder(publishGuard, roomMapper);
		ord.verify(publishGuard).snapshotIfActive(HOUSE, MY_PRTNR);
		ord.verify(roomMapper).updateRoomStatus(ROOM, MY_PRTNR, "ACTIVE", 7L);
		ord.verify(publishGuard).enforceNoRegression(HOUSE, MY_PRTNR, snap);
	}

	@Test
	@DisplayName("삭제 — 본인 활성 객실이면 soft delete")
	void deleteSuccess() {
		stubLock(MY_PRTNR, "ACTIVE");
		when(roomMapper.softDeleteRoom(ROOM, MY_PRTNR, 7L)).thenReturn(1);
		ReadinessSnapshot snap = mock(ReadinessSnapshot.class);
		when(publishGuard.snapshotIfActive(HOUSE, MY_PRTNR)).thenReturn(snap);
		service.delete(ROOM, partner(7));
		// snapshotIfActive → mutation → enforceNoRegression(동일 스냅샷) 순서
		InOrder ord = inOrder(publishGuard, roomMapper);
		ord.verify(publishGuard).snapshotIfActive(HOUSE, MY_PRTNR);
		ord.verify(roomMapper).softDeleteRoom(ROOM, MY_PRTNR, 7L);
		ord.verify(publishGuard).enforceNoRegression(HOUSE, MY_PRTNR, snap);
	}

	@Test
	@DisplayName("목록 — 타 소유 고택은 404")
	void listForeignHouse() {
		when(partnerService.findManageablePrtnrSn(7L)).thenReturn(MY_PRTNR);
		when(houseMapper.selectHouseDetail(HOUSE)).thenReturn(houseLock(999L, "N"));
		BusinessException e = assertThrows(BusinessException.class, () -> service.listByHouse(HOUSE, partner(7)));
		assertEquals(HouseErrorCode.HOUSE_NOT_FOUND, e.getErrorCode());
		verify(roomMapper, never()).selectRoomsByHouse(anyLong());
	}

	// ===== 운영자 대행(HOUSE_PROXY_MNG) — owner=resolver 해석, 행위자=운영자 =====

	private static AuthUser operator(long userSn) {
		return new AuthUser(userSn, "op@eeum.dev", "운영자", "OPERATOR", "ACTIVE", List.of("HOUSE_PROXY_MNG"));
	}

	@Test
	@DisplayName("운영자 대행 등록 — owner=resolver(고택) 해석, 행위자=운영자 userSn, 토큰 prtnrSn 미사용")
	void insertAsOperator() {
		when(proxyResolver.resolveOwnerForHouse(eq(HOUSE), any())).thenReturn(MY_PRTNR);
		when(houseMapper.selectHouseForUpdate(HOUSE)).thenReturn(houseLock(MY_PRTNR, "N"));
		doAnswer(inv -> {
			inv.getArgument(0, RoomVO.class).setRoomSn(ROOM);
			return null;
		}).when(roomMapper).insertRoom(any(RoomVO.class));
		when(roomMapper.selectRoomDetail(ROOM))
				.thenReturn(RoomVO.builder().roomSn(ROOM).houseSn(HOUSE).ownerPrtnrSn(MY_PRTNR).build());
		ArgumentCaptor<RoomVO> cap = ArgumentCaptor.forClass(RoomVO.class);

		service.insertAsOperator(insertReq(2, 4), operator(9));

		verify(roomMapper).insertRoom(cap.capture());
		assertEquals(9L, cap.getValue().getRgtrSn(), "행위자=운영자 userSn");
		verify(partnerService, never()).findManageablePrtnrSn(anyLong()); // 운영자는 토큰 prtnrSn 해석 안 함
	}

	@Test
	@DisplayName("운영자 대행 삭제 — resolveOwnerForRoom owner로 코어 위임, 행위자=운영자")
	void deleteAsOperator() {
		when(proxyResolver.resolveOwnerForRoom(eq(ROOM), any())).thenReturn(MY_PRTNR);
		when(roomMapper.selectHouseSnByRoom(ROOM)).thenReturn(HOUSE);
		when(houseMapper.selectHouseForUpdate(HOUSE)).thenReturn(houseLock(MY_PRTNR, "N"));
		when(roomMapper.selectRoomForUpdate(ROOM)).thenReturn(roomLock("ACTIVE", "N"));
		when(roomMapper.softDeleteRoom(ROOM, MY_PRTNR, 9L)).thenReturn(1);

		service.deleteAsOperator(ROOM, operator(9));

		verify(roomMapper).softDeleteRoom(ROOM, MY_PRTNR, 9L); // owner=파트너, 행위자=운영자(9)
		verify(partnerService, never()).findManageablePrtnrSn(anyLong());
	}

	@Test
	@DisplayName("운영자 대행 상태변경 — resolver owner로 위임")
	void changeStatusAsOperator() {
		when(proxyResolver.resolveOwnerForRoom(eq(ROOM), any())).thenReturn(MY_PRTNR);
		when(roomMapper.selectHouseSnByRoom(ROOM)).thenReturn(HOUSE);
		when(houseMapper.selectHouseForUpdate(HOUSE)).thenReturn(houseLock(MY_PRTNR, "N"));
		when(roomMapper.selectRoomForUpdate(ROOM)).thenReturn(roomLock("DRAFT", "N"));
		when(roomMapper.updateRoomStatus(ROOM, MY_PRTNR, "ACTIVE", 9L)).thenReturn(1);

		service.changeStatusAsOperator(new RoomStatusChangeRequest(ROOM, "ACTIVE"), operator(9));

		verify(roomMapper).updateRoomStatus(ROOM, MY_PRTNR, "ACTIVE", 9L);
	}

	// ===== 침구 설정(교체) =====

	private static CodeItemResponse code(String cd) {
		return new CodeItemResponse("BEDDING_TYPE", cd, cd, 1);
	}

	private void stubBeddingCodes() {
		when(codeService.list("BEDDING_TYPE"))
				.thenReturn(List.of(code("QUEEN_BED"), code("KING_BED"), code("SINGLE_BED")));
	}

	@Test
	@DisplayName("침구 설정 — 활성 비움 후 재삽입(교체)")
	void setBeddingsReplaces() {
		stubLock(MY_PRTNR, "ACTIVE");
		stubBeddingCodes();
		ReadinessSnapshot snap = mock(ReadinessSnapshot.class);
		when(publishGuard.snapshotIfActive(HOUSE, MY_PRTNR)).thenReturn(snap);

		service.setBeddings(new RoomBeddingSetRequest(ROOM,
				List.of(new RoomBeddingSetRequest.Item("QUEEN_BED", 1),
						new RoomBeddingSetRequest.Item("SINGLE_BED", 2))), partner(7));

		ArgumentCaptor<egovframework.eeum.house.vo.RoomBeddingVO> cap =
				ArgumentCaptor.forClass(egovframework.eeum.house.vo.RoomBeddingVO.class);
		verify(roomBeddingMapper, org.mockito.Mockito.times(2)).insertBedding(cap.capture());
		assertEquals(7L, cap.getAllValues().get(0).getRgtrSn(), "행위자=파트너 userSn");
		// snapshotIfActive → mutation(softDelete) → enforceNoRegression(동일 스냅샷) 순서
		InOrder ord = inOrder(publishGuard, roomBeddingMapper);
		ord.verify(publishGuard).snapshotIfActive(HOUSE, MY_PRTNR);
		ord.verify(roomBeddingMapper).softDeleteActiveByRoom(ROOM, 7L);
		ord.verify(publishGuard).enforceNoRegression(HOUSE, MY_PRTNR, snap);
	}

	@Test
	@DisplayName("침구 설정 — COMM_CD 미존재 타입은 400")
	void setBeddingsInvalidType() {
		stubLock(MY_PRTNR, "ACTIVE");
		stubBeddingCodes();
		BusinessException e = assertThrows(BusinessException.class,
				() -> service.setBeddings(new RoomBeddingSetRequest(ROOM,
						List.of(new RoomBeddingSetRequest.Item("HAMMOCK", 1))), partner(7)));
		assertEquals(RoomErrorCode.ROOM_BEDDING_TYPE_INVALID, e.getErrorCode());
		verify(roomBeddingMapper, never()).insertBedding(any());
	}

	@Test
	@DisplayName("침구 설정 — 중복 타입은 400")
	void setBeddingsDuplicateType() {
		stubLock(MY_PRTNR, "ACTIVE");
		stubBeddingCodes();
		BusinessException e = assertThrows(BusinessException.class,
				() -> service.setBeddings(new RoomBeddingSetRequest(ROOM,
						List.of(new RoomBeddingSetRequest.Item("QUEEN_BED", 1),
								new RoomBeddingSetRequest.Item("QUEEN_BED", 2))), partner(7)));
		assertEquals(RoomErrorCode.ROOM_BEDDING_TYPE_INVALID, e.getErrorCode());
		verify(roomBeddingMapper, never()).insertBedding(any());
	}

	@Test
	@DisplayName("운영자 대행 침구 설정 — resolveOwnerForRoom owner로 위임, 행위자=운영자")
	void setBeddingsAsOperator() {
		when(proxyResolver.resolveOwnerForRoom(eq(ROOM), any())).thenReturn(MY_PRTNR);
		when(roomMapper.selectHouseSnByRoom(ROOM)).thenReturn(HOUSE);
		when(houseMapper.selectHouseForUpdate(HOUSE)).thenReturn(houseLock(MY_PRTNR, "N"));
		when(roomMapper.selectRoomForUpdate(ROOM)).thenReturn(roomLock("ACTIVE", "N"));
		stubBeddingCodes();

		service.setBeddingsAsOperator(new RoomBeddingSetRequest(ROOM,
				List.of(new RoomBeddingSetRequest.Item("KING_BED", 1))), operator(9));

		verify(roomBeddingMapper).softDeleteActiveByRoom(ROOM, 9L);
		ArgumentCaptor<egovframework.eeum.house.vo.RoomBeddingVO> cap =
				ArgumentCaptor.forClass(egovframework.eeum.house.vo.RoomBeddingVO.class);
		verify(roomBeddingMapper).insertBedding(cap.capture());
		assertEquals(9L, cap.getValue().getRgtrSn(), "행위자=운영자 userSn");
		verify(partnerService, never()).findManageablePrtnrSn(anyLong());
	}
}
