package egovframework.eeum.house;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
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
import egovframework.eeum.house.service.house.HousePublishGuard;
import egovframework.eeum.house.service.house.HousePublishValidator;
import egovframework.eeum.house.service.house.ReadinessSnapshot;
import egovframework.eeum.house.service.house.dao.HouseMapper;
import egovframework.eeum.house.service.house.dao.HousePayMethodMapper;
import egovframework.eeum.house.service.house.impl.HouseServiceImpl;
import egovframework.eeum.house.service.room.dao.RoomMapper;
import egovframework.eeum.house.vo.HousePayMethodVO;
import egovframework.eeum.house.vo.HouseVO;
import egovframework.eeum.house.web.dto.HouseInsertRequest;
import egovframework.eeum.house.web.dto.HousePayMethodSetRequest;
import egovframework.eeum.house.web.dto.HousePolicyUpdateRequest;
import egovframework.eeum.house.web.dto.HouseStatusChangeRequest;
import egovframework.eeum.house.web.dto.HouseUpdateRequest;
import egovframework.eeum.region.service.region.RegionService;

/**
 * 고택 서비스 검증 — 소유자 해석/소유권 격리/상태가드(SUSPENDED)/중복명(DB 없이 mock).
 */
class HouseServiceVerification {

	private static final long MY_PRTNR = 100L;

	private final HouseMapper mapper = mock(HouseMapper.class);
	private final RoomMapper roomMapper = mock(RoomMapper.class);
	private final PartnerService partnerService = mock(PartnerService.class);
	private final HousePayMethodMapper payMethodMapper = mock(HousePayMethodMapper.class);
	private final CodeService codeService = mock(CodeService.class);
	private final HousePublishValidator publishValidator = mock(HousePublishValidator.class);
	private final HousePublishGuard publishGuard = mock(HousePublishGuard.class);
	private final RegionService regionService = mock(RegionService.class);
	private final HouseServiceImpl service = new HouseServiceImpl(
			mapper, roomMapper, partnerService, payMethodMapper, codeService, publishValidator, publishGuard,
			regionService);

	private static CodeItemResponse code(String group, String cd) {
		return new CodeItemResponse(group, cd, cd, 1);
	}

	private static AuthUser partner(long userSn) {
		return new AuthUser(userSn, "p@eeum.kr", "김파트너", "PARTNER", "ACTIVE", List.of("HOUSE_MNG"));
	}

	private static HouseVO vo(long houseSn, long prtnrSn, String stts, String delYn) {
		return HouseVO.builder().houseSn(houseSn).prtnrSn(prtnrSn).houseNm("고택")
				.houseSttsCd(stts).delYn(delYn).build();
	}

	@Test
	@DisplayName("등록 — 소유자(PRTNR_SN)는 토큰에서 해석, 요청 body 불신")
	void insertResolvesOwnerFromToken() {
		when(partnerService.findManageablePrtnrSn(7L)).thenReturn(MY_PRTNR);
		when(regionService.isValidHouseRegion(any(), any())).thenReturn(true);
		doAnswer(inv -> {
			inv.getArgument(0, HouseVO.class).setHouseSn(1L);
			return null;
		}).when(mapper).insertHouse(any(HouseVO.class));
		when(mapper.selectHouseDetail(1L)).thenReturn(vo(1L, MY_PRTNR, "DRAFT", "N"));
		ArgumentCaptor<HouseVO> cap = ArgumentCaptor.forClass(HouseVO.class);

		service.insert(new HouseInsertRequest("고택", null, null, null, null, null, null), partner(7));

		verify(mapper).insertHouse(cap.capture());
		assertEquals(MY_PRTNR, cap.getValue().getPrtnrSn(), "소유자=해석된 PRTNR_SN");
		assertEquals(7L, cap.getValue().getRgtrSn(), "행위자=로그인 USER_SN");
	}

	@Test
	@DisplayName("등록 — 활성 파트너가 아니면 403(NOT_PARTNER)")
	void insertRejectedForNonPartner() {
		when(partnerService.findManageablePrtnrSn(7L)).thenReturn(null);
		BusinessException e = assertThrows(BusinessException.class,
				() -> service.insert(new HouseInsertRequest("고택", null, null, null, null, null, null), partner(7)));
		assertEquals(HouseErrorCode.HOUSE_NOT_PARTNER, e.getErrorCode());
		verify(mapper, never()).insertHouse(any());
	}

	@Test
	@DisplayName("등록 — 같은 파트너 내 고택명 중복은 409(DuplicateKey→NAME_DUPLICATE)")
	void insertDuplicateName() {
		when(partnerService.findManageablePrtnrSn(7L)).thenReturn(MY_PRTNR);
		when(regionService.isValidHouseRegion(any(), any())).thenReturn(true);
		doAnswer(inv -> {
			throw new DuplicateKeyException("dup");
		}).when(mapper).insertHouse(any(HouseVO.class));
		BusinessException e = assertThrows(BusinessException.class,
				() -> service.insert(new HouseInsertRequest("고택", null, null, null, null, null, null), partner(7)));
		assertEquals(HouseErrorCode.HOUSE_NAME_DUPLICATE, e.getErrorCode());
	}

	@Test
	@DisplayName("등록 — 유효 지역코드는 HOUSE 에 저장(시도/시군구)")
	void insertStoresRegion() {
		when(partnerService.findManageablePrtnrSn(7L)).thenReturn(MY_PRTNR);
		when(regionService.isValidHouseRegion("47", "47170")).thenReturn(true);
		doAnswer(inv -> {
			inv.getArgument(0, HouseVO.class).setHouseSn(1L);
			return null;
		}).when(mapper).insertHouse(any(HouseVO.class));
		when(mapper.selectHouseDetail(1L)).thenReturn(vo(1L, MY_PRTNR, "DRAFT", "N"));
		ArgumentCaptor<HouseVO> cap = ArgumentCaptor.forClass(HouseVO.class);

		service.insert(new HouseInsertRequest("고택", null, null, null, null, "47", "47170"), partner(7));

		verify(mapper).insertHouse(cap.capture());
		assertEquals("47", cap.getValue().getSidoRegionCd());
		assertEquals("47170", cap.getValue().getSigunguRegionCd());
	}

	@Test
	@DisplayName("등록 — 지역코드 무효 조합은 400(HOUSE_REGION_INVALID), insertHouse 미진입")
	void insertInvalidRegion() {
		when(partnerService.findManageablePrtnrSn(7L)).thenReturn(MY_PRTNR);
		when(regionService.isValidHouseRegion("99", null)).thenReturn(false);
		BusinessException e = assertThrows(BusinessException.class,
				() -> service.insert(new HouseInsertRequest("고택", null, null, null, null, "99", null), partner(7)));
		assertEquals(HouseErrorCode.HOUSE_REGION_INVALID, e.getErrorCode());
		verify(mapper, never()).insertHouse(any());
	}

	@Test
	@DisplayName("상세 — 타 파트너 소유는 404(소유권 격리)")
	void detailForeignOwnerNotFound() {
		when(partnerService.findManageablePrtnrSn(7L)).thenReturn(MY_PRTNR);
		when(mapper.selectHouseDetail(5L)).thenReturn(vo(5L, 999L, "ACTIVE", "N"));
		BusinessException e = assertThrows(BusinessException.class, () -> service.detail(5L, partner(7)));
		assertEquals(HouseErrorCode.HOUSE_NOT_FOUND, e.getErrorCode());
	}

	@Test
	@DisplayName("수정 — 타 파트너 소유는 404, updateHouse 진입 안 함")
	void updateForeignOwnerNotFound() {
		when(partnerService.findManageablePrtnrSn(7L)).thenReturn(MY_PRTNR);
		when(mapper.selectHouseForUpdate(5L)).thenReturn(vo(5L, 999L, "ACTIVE", "N"));
		BusinessException e = assertThrows(BusinessException.class,
				() -> service.update(new HouseUpdateRequest(5L, "수정", null, null, null, null, null, null), partner(7)));
		assertEquals(HouseErrorCode.HOUSE_NOT_FOUND, e.getErrorCode());
		verify(mapper, never()).updateHouse(anyLong(), anyLong(), anyString(), any(), any(), any(), any(),
				any(), any(), anyLong());
	}

	@Test
	@DisplayName("수정 — 지역 미전송(null)이면 매퍼에 null 전달(SQL CASE 가 기존 지역값 보존)")
	void updatePreservesRegionWhenNull() {
		when(partnerService.findManageablePrtnrSn(7L)).thenReturn(MY_PRTNR);
		when(mapper.selectHouseForUpdate(5L)).thenReturn(vo(5L, MY_PRTNR, "ACTIVE", "N"));
		when(regionService.isValidHouseRegion(null, null)).thenReturn(true);
		when(mapper.updateHouse(5L, MY_PRTNR, "수정", null, null, null, null, null, null, 7L)).thenReturn(1);

		service.update(new HouseUpdateRequest(5L, "수정", null, null, null, null, null, null), partner(7));

		// 지역 null 그대로 매퍼 전달 → 매퍼 CASE(sido null이면 기존값 유지)가 백필값 보존
		verify(mapper).updateHouse(5L, MY_PRTNR, "수정", null, null, null, null, null, null, 7L);
	}

	@Test
	@DisplayName("수정 — 유효 지역 전송 시 매퍼에 교체값 전달")
	void updateReplacesRegionWhenProvided() {
		when(partnerService.findManageablePrtnrSn(7L)).thenReturn(MY_PRTNR);
		when(mapper.selectHouseForUpdate(5L)).thenReturn(vo(5L, MY_PRTNR, "ACTIVE", "N"));
		when(regionService.isValidHouseRegion("47", "47170")).thenReturn(true);
		when(mapper.updateHouse(5L, MY_PRTNR, "수정", null, null, null, null, "47", "47170", 7L)).thenReturn(1);
		ReadinessSnapshot snap = mock(ReadinessSnapshot.class);
		when(publishGuard.snapshotIfActive(5L, MY_PRTNR)).thenReturn(snap);

		service.update(new HouseUpdateRequest(5L, "수정", null, null, null, null, "47", "47170"), partner(7));

		// snapshotIfActive → mutation → enforceNoRegression(동일 스냅샷 객체) 순서
		InOrder ord = inOrder(publishGuard, mapper);
		ord.verify(publishGuard).snapshotIfActive(5L, MY_PRTNR);
		ord.verify(mapper).updateHouse(5L, MY_PRTNR, "수정", null, null, null, null, "47", "47170", 7L);
		ord.verify(publishGuard).enforceNoRegression(5L, MY_PRTNR, snap);
	}

	@Test
	@DisplayName("상태변경 — 운영자 임시중지(SUSPENDED)는 판매자 자가복구 불가(409)")
	void changeStatusBlockedWhenSuspended() {
		when(partnerService.findManageablePrtnrSn(7L)).thenReturn(MY_PRTNR);
		when(mapper.selectHouseForUpdate(5L)).thenReturn(vo(5L, MY_PRTNR, "SUSPENDED", "N"));
		BusinessException e = assertThrows(BusinessException.class,
				() -> service.changeStatus(new HouseStatusChangeRequest(5L, "ACTIVE"), partner(7)));
		assertEquals(HouseErrorCode.HOUSE_SUSPENDED, e.getErrorCode());
		verify(mapper, never()).updateHouseStatus(anyLong(), anyLong(), anyString(), anyLong());
	}

	@Test
	@DisplayName("상태변경 — 본인 소유 + 비SUSPENDED면 성공")
	void changeStatusSuccess() {
		when(partnerService.findManageablePrtnrSn(7L)).thenReturn(MY_PRTNR);
		when(mapper.selectHouseForUpdate(5L)).thenReturn(vo(5L, MY_PRTNR, "DRAFT", "N"));
		when(mapper.updateHouseStatus(5L, MY_PRTNR, "ACTIVE", 7L)).thenReturn(1);
		service.changeStatus(new HouseStatusChangeRequest(5L, "ACTIVE"), partner(7));
		verify(mapper).updateHouseStatus(5L, MY_PRTNR, "ACTIVE", 7L);
	}

	@Test
	@DisplayName("삭제 — 본인 활성 고택이면 soft delete + 하위 객실 동반 정리")
	void deleteSuccess() {
		when(partnerService.findManageablePrtnrSn(7L)).thenReturn(MY_PRTNR);
		when(mapper.selectHouseForUpdate(5L)).thenReturn(vo(5L, MY_PRTNR, "ACTIVE", "N"));
		when(mapper.softDeleteHouse(5L, MY_PRTNR, 7L)).thenReturn(1);
		service.delete(5L, partner(7));
		verify(roomMapper).softDeleteRoomsByHouse(5L, MY_PRTNR, 7L);
		verify(mapper).softDeleteHouse(5L, MY_PRTNR, 7L);
	}

	@Test
	@DisplayName("삭제 — 타 파트너 소유는 404, softDelete 진입 안 함")
	void deleteForeignOwnerNotFound() {
		when(partnerService.findManageablePrtnrSn(7L)).thenReturn(MY_PRTNR);
		when(mapper.selectHouseForUpdate(5L)).thenReturn(vo(5L, 999L, "ACTIVE", "N"));
		BusinessException e = assertThrows(BusinessException.class, () -> service.delete(5L, partner(7)));
		assertEquals(HouseErrorCode.HOUSE_NOT_FOUND, e.getErrorCode());
		verify(mapper, never()).softDeleteHouse(anyLong(), anyLong(), anyLong());
	}

	// ===== 위저드 Step2(정책/결제) + Step3(발행검증) — 판매자 경로 =====

	@Test
	@DisplayName("정책 — 본인 소유면 해석된 PRTNR_SN/행위자로 전체저장")
	void updatePolicySuccess() {
		when(partnerService.findManageablePrtnrSn(7L)).thenReturn(MY_PRTNR);
		when(mapper.selectHouseForUpdate(5L)).thenReturn(vo(5L, MY_PRTNR, "DRAFT", "N"));
		when(codeService.list("RSVT_CNFIRM")).thenReturn(List.of(code("RSVT_CNFIRM", "AUTO")));
		when(mapper.updateHousePolicy(anyLong(), anyLong(), any(), any(), any(), any(), any(), any(),
				any(), any(), any(), any(), anyLong())).thenReturn(1);
		ReadinessSnapshot snap = mock(ReadinessSnapshot.class);
		when(publishGuard.snapshotIfActive(5L, MY_PRTNR)).thenReturn(snap);

		service.updatePolicy(
				new HousePolicyUpdateRequest(5L, 0, 0, 0, "15:00", "11:00", "13:00", "AUTO", "환불규정", null, "예약공지"),
				partner(7));

		// snapshotIfActive → mutation → enforceNoRegression(동일 스냅샷) 순서 + 신규필드(noticeCn) 전달 확인
		InOrder ord = inOrder(publishGuard, mapper);
		ord.verify(publishGuard).snapshotIfActive(5L, MY_PRTNR);
		ord.verify(mapper).updateHousePolicy(eq(5L), eq(MY_PRTNR), any(), any(), any(), any(), any(), any(),
				eq("AUTO"), any(), any(), eq("예약공지"), eq(7L));
		ord.verify(publishGuard).enforceNoRegression(5L, MY_PRTNR, snap);
	}

	@Test
	@DisplayName("정책 — 예약확정방식 코드 무효면 400, 갱신 진입 안 함")
	void updatePolicyInvalidRsvtCnfirm() {
		when(partnerService.findManageablePrtnrSn(7L)).thenReturn(MY_PRTNR);
		when(mapper.selectHouseForUpdate(5L)).thenReturn(vo(5L, MY_PRTNR, "DRAFT", "N"));
		when(codeService.list("RSVT_CNFIRM")).thenReturn(List.of(code("RSVT_CNFIRM", "AUTO")));

		BusinessException e = assertThrows(BusinessException.class, () -> service.updatePolicy(
				new HousePolicyUpdateRequest(5L, null, null, null, null, null, null, "NOPE", null, null, null),
				partner(7)));
		assertEquals(HouseErrorCode.HOUSE_RSVT_CNFIRM_INVALID, e.getErrorCode());
		verify(mapper, never()).updateHousePolicy(anyLong(), anyLong(), any(), any(), any(), any(), any(), any(),
				any(), any(), any(), any(), anyLong());
	}

	@Test
	@DisplayName("결제수단 — 중복 입력은 1회만 삽입(전체교체)")
	void setPayMethodsDedupes() {
		when(partnerService.findManageablePrtnrSn(7L)).thenReturn(MY_PRTNR);
		when(mapper.selectHouseForUpdate(5L)).thenReturn(vo(5L, MY_PRTNR, "DRAFT", "N"));
		when(codeService.list("PAY_MTHD"))
				.thenReturn(List.of(code("PAY_MTHD", "CARD"), code("PAY_MTHD", "BANK_TRANSFER")));
		ReadinessSnapshot snap = mock(ReadinessSnapshot.class);
		when(publishGuard.snapshotIfActive(5L, MY_PRTNR)).thenReturn(snap);

		service.setPayMethods(new HousePayMethodSetRequest(5L, List.of("CARD", "CARD")), partner(7));

		verify(payMethodMapper).insertPayMethod(any(HousePayMethodVO.class)); // 기본 times(1) — 중복 제거됨
		// snapshotIfActive → mutation(softDelete) → enforceNoRegression(동일 스냅샷) 순서
		InOrder ord = inOrder(publishGuard, payMethodMapper);
		ord.verify(publishGuard).snapshotIfActive(5L, MY_PRTNR);
		ord.verify(payMethodMapper).softDeleteActiveByHouse(5L, 7L);
		ord.verify(publishGuard).enforceNoRegression(5L, MY_PRTNR, snap);
	}

	@Test
	@DisplayName("상태변경 — ACTIVE 전이 시 발행검증을 거친 뒤 갱신")
	void changeStatusActiveInvokesPublishValidator() {
		when(partnerService.findManageablePrtnrSn(7L)).thenReturn(MY_PRTNR);
		when(mapper.selectHouseForUpdate(5L)).thenReturn(vo(5L, MY_PRTNR, "DRAFT", "N"));
		when(mapper.updateHouseStatus(5L, MY_PRTNR, "ACTIVE", 7L)).thenReturn(1);

		service.changeStatus(new HouseStatusChangeRequest(5L, "ACTIVE"), partner(7));

		verify(publishValidator).validate(5L, MY_PRTNR);
		verify(mapper).updateHouseStatus(5L, MY_PRTNR, "ACTIVE", 7L);
	}
}
