package egovframework.eeum.house.service.room.impl;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import egovframework.eeum.account.service.partner.PartnerService;
import egovframework.eeum.auth.vo.AuthUser;
import egovframework.eeum.base.audit.Audit;
import egovframework.eeum.base.exception.BusinessException;
import egovframework.eeum.code.service.code.CodeService;
import egovframework.eeum.code.web.dto.CodeItemResponse;
import egovframework.eeum.house.exception.HouseErrorCode;
import egovframework.eeum.house.exception.RoomErrorCode;
import egovframework.eeum.house.service.house.HouseProxyOwnerResolver;
import egovframework.eeum.house.service.house.HousePublishGuard;
import egovframework.eeum.house.service.house.ReadinessSnapshot;
import egovframework.eeum.house.service.house.HouseReviewGuard;
import egovframework.eeum.house.service.house.dao.HouseMapper;
import egovframework.eeum.house.service.room.RoomService;
import egovframework.eeum.house.service.room.dao.RoomAmenityMapper;
import egovframework.eeum.house.service.room.dao.RoomBeddingMapper;
import egovframework.eeum.house.service.room.dao.RoomClimateFacilityMapper;
import egovframework.eeum.house.service.room.dao.RoomMapper;
import egovframework.eeum.house.service.room.dao.RoomOptionMapper;
import egovframework.eeum.house.vo.HouseVO;
import egovframework.eeum.house.vo.RoomBeddingVO;
import egovframework.eeum.house.vo.RoomClimateFacilityVO;
import egovframework.eeum.house.vo.RoomVO;
import egovframework.eeum.house.web.dto.AmenityResponse;
import egovframework.eeum.house.web.dto.RoomBeddingResponse;
import egovframework.eeum.house.web.dto.RoomBeddingSetRequest;
import egovframework.eeum.house.web.dto.RoomInsertRequest;
import egovframework.eeum.house.web.dto.RoomOptionResponse;
import egovframework.eeum.house.web.dto.RoomResponse;
import egovframework.eeum.house.web.dto.RoomStatusChangeRequest;
import egovframework.eeum.house.web.dto.RoomUpdateRequest;
import lombok.RequiredArgsConstructor;

/**
 * 객실 서비스 구현 — 판매자(파트너 본인) + 운영자 대행(HOUSE_PROXY_MNG).
 *
 * <p>코어 메서드는 (ownerPrtnrSn, actorUserSn) 으로 공유한다. 판매자 진입점은 토큰으로 owner 해석({@code requirePrtnrSn}),
 * 운영자 진입점은 대상 고택/객실에서 서버 해석({@link HouseProxyOwnerResolver}). 매퍼의 {@code WHERE PRTNR_SN=?} 이중가드는 양쪽 동일.
 * 같은 도메인의 {@link HouseMapper}/{@link RoomMapper}로 부모 고택 소유권을 검증한다(ArchUnit: 동일 도메인 내 접근 허용).
 * 운영자 임시조치(SUSPENDED)된 객실은 판매자가 스스로 상태를 되돌릴 수 없다.
 */
@Service
@RequiredArgsConstructor
public class RoomServiceImpl implements RoomService {

	private static final String Y = "Y";
	private static final String STTS_SUSPENDED = "SUSPENDED";
	private static final String BEDDING_TYPE_GROUP = "BEDDING_TYPE";
	private static final String CLIMATE_DIV_COOLING = "COOLING";

	private final RoomMapper roomMapper;
	private final RoomBeddingMapper roomBeddingMapper;
	private final RoomAmenityMapper roomAmenityMapper;
	private final RoomOptionMapper roomOptionMapper;
	private final RoomClimateFacilityMapper roomClimateFacilityMapper;
	private final HouseMapper houseMapper;
	private final PartnerService partnerService;
	private final CodeService codeService;
	private final HouseProxyOwnerResolver proxyResolver;
	private final HousePublishGuard publishGuard;
	private final HouseReviewGuard reviewGuard;

	// ===================== 판매자(파트너 본인) — owner=토큰 해석 =====================

	@Override
	@Transactional
	@Audit(actn = "ROOM_INSERT", trgtType = "ROOM", trgtId = "#result.roomSn()")
	public RoomResponse insert(RoomInsertRequest request, AuthUser partner) {
		return insertCore(request, requirePrtnrSn(partner), partner.userSn());
	}

	@Override
	@Transactional(readOnly = true)
	public List<RoomResponse> listByHouse(long houseSn, AuthUser partner) {
		return listCore(houseSn, requirePrtnrSn(partner));
	}

	@Override
	@Transactional(readOnly = true)
	public RoomResponse detail(long roomSn, AuthUser partner) {
		return detailCore(roomSn, requirePrtnrSn(partner));
	}

	@Override
	@Transactional
	@Audit(actn = "ROOM_UPDATE", trgtType = "ROOM", trgtId = "#request.roomSn()")
	public void update(RoomUpdateRequest request, AuthUser partner) {
		updateCore(request, requirePrtnrSn(partner), partner.userSn());
	}

	@Override
	@Transactional
	@Audit(actn = "ROOM_STATUS_CHANGE", trgtType = "ROOM", trgtId = "#request.roomSn()")
	public void changeStatus(RoomStatusChangeRequest request, AuthUser partner) {
		changeStatusCore(request, requirePrtnrSn(partner), partner.userSn());
	}

	@Override
	@Transactional
	@Audit(actn = "ROOM_DELETE", trgtType = "ROOM", trgtId = "#roomSn")
	public void delete(long roomSn, AuthUser partner) {
		deleteCore(roomSn, requirePrtnrSn(partner), partner.userSn());
	}

	@Override
	@Transactional
	@Audit(actn = "ROOM_BEDDING_SET", trgtType = "ROOM", trgtId = "#request.roomSn()")
	public List<RoomBeddingResponse> setBeddings(RoomBeddingSetRequest request, AuthUser partner) {
		return setBeddingsCore(request, requirePrtnrSn(partner), partner.userSn());
	}

	// ============ 운영자 대행(HOUSE_PROXY_MNG) — owner=대상 고택/객실 서버해석, 행위자=운영자 ============

	@Override
	@Transactional
	@Audit(actn = "ROOM_PROXY_INSERT", trgtType = "ROOM", trgtId = "#result.roomSn()")
	public RoomResponse insertAsOperator(RoomInsertRequest request, AuthUser operator) {
		RoomResponse result = insertCore(request, proxyResolver.resolveOwnerForHouse(request.houseSn(), operator),
				operator.userSn());
		// 검수요청 상태였으면 내용 변경으로 DRAFT 자동복귀(+REVERT 이력)
		reviewGuard.markDirtyIfUnderReview(request.houseSn(), operator.userSn());
		return result;
	}

	@Override
	@Transactional(readOnly = true)
	public List<RoomResponse> listByHouseAsOperator(long houseSn, AuthUser operator) {
		return listCore(houseSn, proxyResolver.resolveOwnerForHouse(houseSn, operator));
	}

	@Override
	@Transactional(readOnly = true)
	public RoomResponse detailAsOperator(long roomSn, AuthUser operator) {
		return detailCore(roomSn, proxyResolver.resolveOwnerForRoom(roomSn, operator));
	}

	@Override
	@Transactional
	@Audit(actn = "ROOM_PROXY_UPDATE", trgtType = "ROOM", trgtId = "#request.roomSn()")
	public void updateAsOperator(RoomUpdateRequest request, AuthUser operator) {
		updateCore(request, proxyResolver.resolveOwnerForRoom(request.roomSn(), operator), operator.userSn());
		// 검수요청 상태였으면 내용 변경으로 DRAFT 자동복귀(+REVERT 이력)
		Long houseSn = roomMapper.selectHouseSnByRoom(request.roomSn());
		if (houseSn != null) {
			reviewGuard.markDirtyIfUnderReview(houseSn, operator.userSn());
		}
	}

	@Override
	@Transactional
	@Audit(actn = "ROOM_PROXY_STATUS_CHANGE", trgtType = "ROOM", trgtId = "#request.roomSn()")
	public void changeStatusAsOperator(RoomStatusChangeRequest request, AuthUser operator) {
		changeStatusCore(request, proxyResolver.resolveOwnerForRoom(request.roomSn(), operator), operator.userSn());
		// 검수요청 상태였으면 내용 변경으로 DRAFT 자동복귀(+REVERT 이력)
		Long houseSn = roomMapper.selectHouseSnByRoom(request.roomSn());
		if (houseSn != null) {
			reviewGuard.markDirtyIfUnderReview(houseSn, operator.userSn());
		}
	}

	@Override
	@Transactional
	@Audit(actn = "ROOM_PROXY_DELETE", trgtType = "ROOM", trgtId = "#roomSn")
	public void deleteAsOperator(long roomSn, AuthUser operator) {
		// 소프트삭제 후엔 selectHouseSnByRoom 이 DEL_YN='N' 필터로 조회 불가 → 삭제 전 houseSn 선확보
		Long houseSn = roomMapper.selectHouseSnByRoom(roomSn);
		deleteCore(roomSn, proxyResolver.resolveOwnerForRoom(roomSn, operator), operator.userSn());
		// 검수요청 상태였으면 내용 변경으로 DRAFT 자동복귀(+REVERT 이력)
		if (houseSn != null) {
			reviewGuard.markDirtyIfUnderReview(houseSn, operator.userSn());
		}
	}

	@Override
	@Transactional
	@Audit(actn = "ROOM_PROXY_BEDDING_SET", trgtType = "ROOM", trgtId = "#request.roomSn()")
	public List<RoomBeddingResponse> setBeddingsAsOperator(RoomBeddingSetRequest request, AuthUser operator) {
		List<RoomBeddingResponse> result = setBeddingsCore(request,
				proxyResolver.resolveOwnerForRoom(request.roomSn(), operator), operator.userSn());
		// 검수요청 상태였으면 내용 변경으로 DRAFT 자동복귀(+REVERT 이력)
		Long houseSn = roomMapper.selectHouseSnByRoom(request.roomSn());
		if (houseSn != null) {
			reviewGuard.markDirtyIfUnderReview(houseSn, operator.userSn());
		}
		return result;
	}

	// ============ 코어(소유자=ownerPrtnrSn, 행위자=actorUserSn 공유 — 파트너/운영자 동일 로직) ============

	private RoomResponse insertCore(RoomInsertRequest request, long ownerPrtnrSn, long actorUserSn) {
		requireOwnedActiveHouse(request.houseSn(), ownerPrtnrSn);
		validatePersons(request.bscPrsnCnt(), request.maxPrsnCnt());
		RoomVO vo = RoomVO.builder()
				.houseSn(request.houseSn())
				.roomNm(request.roomNm())
				.bscPrsnCnt(request.bscPrsnCnt())
				.maxPrsnCnt(request.maxPrsnCnt())
				.dfltStockQty(request.dfltStockQtyOrDefault())
				.minStayNgtCnt(request.minStayNgtCntOrDefault())
				.roomDc(request.roomDc())
				.areaSqm(request.areaSqm())
				.roomCnt(request.roomCnt())
				.bathrmCnt(request.bathrmCnt())
				.rgtrSn(actorUserSn)
				.build();
		try {
			roomMapper.insertRoom(vo); // ROOM_STTS_CD='DRAFT', DEL_YN='N' DB 기본값
		} catch (DuplicateKeyException e) {
			throw new BusinessException(RoomErrorCode.ROOM_NAME_DUPLICATE,
					RoomErrorCode.ROOM_NAME_DUPLICATE.defaultMessage(), e);
		}
		return detailCore(vo.getRoomSn(), ownerPrtnrSn);
	}

	private List<RoomResponse> listCore(long houseSn, long ownerPrtnrSn) {
		HouseVO house = houseMapper.selectHouseDetail(houseSn);
		if (house == null || !house.getPrtnrSn().equals(ownerPrtnrSn)) {
			throw new BusinessException(HouseErrorCode.HOUSE_NOT_FOUND);
		}
		return roomMapper.selectRoomsByHouse(houseSn).stream()
				.map(room -> RoomResponse.from(room, beddingsOf(room.getRoomSn())))
				.toList();
	}

	private RoomResponse detailCore(long roomSn, long ownerPrtnrSn) {
		RoomVO room = roomMapper.selectRoomDetail(roomSn);
		if (room == null || !prtnrSnEquals(room, ownerPrtnrSn)) {
			throw new BusinessException(RoomErrorCode.ROOM_NOT_FOUND);
		}
		// 상세에서만 편의시설·추가옵션 채움(목록은 N+1 회피 위해 비움)
		List<AmenityResponse> amenities = roomAmenityMapper.selectActiveAmenitiesByRoom(roomSn).stream()
				.map(AmenityResponse::from).toList();
		List<RoomOptionResponse> options = roomOptionMapper.selectActiveByRoom(roomSn).stream()
				.map(RoomOptionResponse::from).toList();
		List<RoomClimateFacilityVO> climate = roomClimateFacilityMapper.selectActiveByRoom(roomSn);
		List<String> coolingCds = climate.stream()
				.filter(c -> CLIMATE_DIV_COOLING.equals(c.getFcltyDivCd()))
				.map(RoomClimateFacilityVO::getFcltyCd).toList();
		List<String> heatingCds = climate.stream()
				.filter(c -> !CLIMATE_DIV_COOLING.equals(c.getFcltyDivCd()))
				.map(RoomClimateFacilityVO::getFcltyCd).toList();
		return RoomResponse.from(room, beddingsOf(roomSn), amenities, options, coolingCds, heatingCds);
	}

	private void updateCore(RoomUpdateRequest request, long ownerPrtnrSn, long actorUserSn) {
		lockOwnedActiveRoom(request.roomSn(), ownerPrtnrSn);
		validatePersons(request.bscPrsnCnt(), request.maxPrsnCnt());
		// 발행 후 표류 방지(비퇴행) — 재고/욕실(roomCnt/bathrmCnt) 변경 전 부모 고택 readiness 캡처
		ParentGuardState guard = captureParent(request.roomSn(), ownerPrtnrSn);
		try {
			if (roomMapper.updateRoom(request.roomSn(), ownerPrtnrSn, request.roomNm(), request.bscPrsnCnt(),
					request.maxPrsnCnt(), request.dfltStockQtyOrDefault(), request.minStayNgtCntOrDefault(),
					request.roomDc(), request.areaSqm(), request.roomCnt(), request.bathrmCnt(), actorUserSn) == 0) {
				throw new BusinessException(RoomErrorCode.ROOM_NOT_FOUND);
			}
		} catch (DuplicateKeyException e) {
			throw new BusinessException(RoomErrorCode.ROOM_NAME_DUPLICATE,
					RoomErrorCode.ROOM_NAME_DUPLICATE.defaultMessage(), e);
		}
		// roomCnt/bathrmCnt 를 미충족으로 되돌려 게시가능 객실이 소멸하면 거부(롤백)
		enforceParent(guard, ownerPrtnrSn);
	}

	private void changeStatusCore(RoomStatusChangeRequest request, long ownerPrtnrSn, long actorUserSn) {
		RoomVO room = lockOwnedActiveRoom(request.roomSn(), ownerPrtnrSn);
		if (STTS_SUSPENDED.equals(room.getRoomSttsCd())) {
			throw new BusinessException(RoomErrorCode.ROOM_SUSPENDED);
		}
		// 발행 후 표류 방지(비퇴행) — 변경 전 부모 고택 readiness 캡처
		ParentGuardState guard = captureParent(request.roomSn(), ownerPrtnrSn);
		if (roomMapper.updateRoomStatus(request.roomSn(), ownerPrtnrSn, request.roomSttsCd(), actorUserSn) == 0) {
			throw new BusinessException(RoomErrorCode.ROOM_NOT_FOUND);
		}
		// 변경 후 게시가능 객실 소멸 등 충족→미충족 전환이면 거부(롤백). 활성화/개선은 허용.
		enforceParent(guard, ownerPrtnrSn);
	}

	private void deleteCore(long roomSn, long ownerPrtnrSn, long actorUserSn) {
		lockOwnedActiveRoom(roomSn, ownerPrtnrSn);
		ParentGuardState guard = captureParent(roomSn, ownerPrtnrSn);
		if (roomMapper.softDeleteRoom(roomSn, ownerPrtnrSn, actorUserSn) == 0) {
			throw new BusinessException(RoomErrorCode.ROOM_NOT_FOUND);
		}
		// 게시가능 객실을 삭제해 부모 ACTIVE 고택의 판매가능이 소멸(true→false)했으면 거부(롤백)
		enforceParent(guard, ownerPrtnrSn);
	}

	/** 침구 교체 — 부모 고택/객실 소유·활성 잠금 → 코드 검증 → 활성 비움 → 재삽입. 반환=설정 결과. */
	private List<RoomBeddingResponse> setBeddingsCore(RoomBeddingSetRequest request, long ownerPrtnrSn,
			long actorUserSn) {
		lockOwnedActiveRoom(request.roomSn(), ownerPrtnrSn);
		validateBeddingTypes(request.items());
		ParentGuardState guard = captureParent(request.roomSn(), ownerPrtnrSn);
		roomBeddingMapper.softDeleteActiveByRoom(request.roomSn(), actorUserSn);
		for (RoomBeddingSetRequest.Item item : request.items()) {
			RoomBeddingVO vo = RoomBeddingVO.builder()
					.roomSn(request.roomSn())
					.beddingTypeCd(item.beddingTypeCd())
					.bedCnt(item.bedCnt())
					.rgtrSn(actorUserSn)
					.build();
			roomBeddingMapper.insertBedding(vo);
		}
		// 침구를 비워 게시가능이 소멸(true→false)하면 거부, 침구 추가(개선)는 허용 — 교착 해소
		enforceParent(guard, ownerPrtnrSn);
		return beddingsOf(request.roomSn());
	}

	/** 객실의 부모 고택 readiness 를 편집 전 캡처(ACTIVE 만) — 비퇴행 비교용 홀더. 부모 미해석 시 null. */
	private ParentGuardState captureParent(long roomSn, long ownerPrtnrSn) {
		Long houseSn = roomMapper.selectHouseSnByRoom(roomSn);
		if (houseSn == null) {
			return null;
		}
		return new ParentGuardState(houseSn, publishGuard.snapshotIfActive(houseSn, ownerPrtnrSn));
	}

	/** 편집 후 부모 고택 비퇴행 강제 — 충족→미충족 전환 시 409(롤백). 캡처가 없으면 no-op. */
	private void enforceParent(ParentGuardState guard, long ownerPrtnrSn) {
		if (guard != null) {
			publishGuard.enforceNoRegression(guard.houseSn(), ownerPrtnrSn, guard.before());
		}
	}

	/** 편집 전 부모 고택 식별자 + readiness 스냅샷(ACTIVE 아니면 before=null). */
	private record ParentGuardState(long houseSn, ReadinessSnapshot before) {
	}

	private List<RoomBeddingResponse> beddingsOf(long roomSn) {
		return roomBeddingMapper.selectActiveByRoom(roomSn).stream().map(RoomBeddingResponse::from).toList();
	}

	// ------------------------------------------------------------------

	/** 로그인 사용자의 활성 파트너 일련번호 — 아니면 403. */
	private long requirePrtnrSn(AuthUser partner) {
		if (partner == null) {
			throw BusinessException.unauthorized();
		}
		Long prtnrSn = partnerService.findManageablePrtnrSn(partner.userSn());
		if (prtnrSn == null) {
			throw new BusinessException(HouseErrorCode.HOUSE_NOT_PARTNER);
		}
		return prtnrSn;
	}

	/** 부모 고택이 본인 소유 + 활성인지(FOR UPDATE) — 아니면 404. */
	private void requireOwnedActiveHouse(long houseSn, long prtnrSn) {
		HouseVO house = houseMapper.selectHouseForUpdate(houseSn);
		if (house == null || Y.equals(house.getDelYn()) || !house.getPrtnrSn().equals(prtnrSn)) {
			throw new BusinessException(HouseErrorCode.HOUSE_NOT_FOUND);
		}
	}

	/**
	 * 변경계 잠금 — 락 순서 HOUSE→ROOM 통일(고택삭제와 동일, 데드락 방지).
	 * houseSn 선확보 → 부모 고택 FOR UPDATE(소유·활성 검증) → 객실 FOR UPDATE(활성 검증). 반환=잠긴 객실.
	 */
	private RoomVO lockOwnedActiveRoom(long roomSn, long prtnrSn) {
		Long houseSn = roomMapper.selectHouseSnByRoom(roomSn);
		if (houseSn == null) {
			throw new BusinessException(RoomErrorCode.ROOM_NOT_FOUND);
		}
		HouseVO house = houseMapper.selectHouseForUpdate(houseSn);
		if (house == null || Y.equals(house.getDelYn()) || !house.getPrtnrSn().equals(prtnrSn)) {
			throw new BusinessException(RoomErrorCode.ROOM_NOT_FOUND);
		}
		RoomVO room = roomMapper.selectRoomForUpdate(roomSn);
		if (room == null || Y.equals(room.getDelYn())) {
			throw new BusinessException(RoomErrorCode.ROOM_NOT_FOUND);
		}
		return room;
	}

	private static boolean prtnrSnEquals(RoomVO room, long prtnrSn) {
		return room.getOwnerPrtnrSn() != null && room.getOwnerPrtnrSn() == prtnrSn;
	}

	/** 최대인원 >= 기준인원 정합(서비스 — 클린 에러. DB CHECK 와 짝). */
	private static void validatePersons(int bscPrsnCnt, int maxPrsnCnt) {
		if (maxPrsnCnt < bscPrsnCnt) {
			throw new BusinessException(RoomErrorCode.ROOM_PERSON_INVALID);
		}
	}

	/** 침구타입코드가 COMM_CD BEDDING_TYPE 에 존재 + 중복 없음인지 — 아니면 400. */
	private void validateBeddingTypes(List<RoomBeddingSetRequest.Item> items) {
		Set<String> allowed = codeService.list(BEDDING_TYPE_GROUP).stream()
				.map(CodeItemResponse::cd).collect(Collectors.toSet());
		Set<String> seen = new java.util.HashSet<>();
		for (RoomBeddingSetRequest.Item item : items) {
			if (!allowed.contains(item.beddingTypeCd()) || !seen.add(item.beddingTypeCd())) {
				throw new BusinessException(RoomErrorCode.ROOM_BEDDING_TYPE_INVALID);
			}
		}
	}
}
