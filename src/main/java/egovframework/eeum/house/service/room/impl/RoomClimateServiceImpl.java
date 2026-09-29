package egovframework.eeum.house.service.room.impl;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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
import egovframework.eeum.house.service.house.HouseReviewGuard;
import egovframework.eeum.house.service.house.dao.HouseMapper;
import egovframework.eeum.house.service.room.RoomClimateService;
import egovframework.eeum.house.service.room.dao.RoomClimateFacilityMapper;
import egovframework.eeum.house.service.room.dao.RoomMapper;
import egovframework.eeum.house.vo.HouseVO;
import egovframework.eeum.house.vo.RoomClimateFacilityVO;
import egovframework.eeum.house.vo.RoomVO;
import egovframework.eeum.house.web.dto.RoomClimateSetRequest;
import lombok.RequiredArgsConstructor;

/**
 * 객실 냉난방시설 서비스 구현 — 판매자(파트너 본인) + 운영자 대행(HOUSE_PROXY_MNG).
 *
 * <p>부모 객실/고택 소유권 격리(락 순서 HOUSE→ROOM), 냉방·난방 그룹별 COMM_CD 검증, 교체(replace) 동기화.
 * 냉방코드는 COMM_CD COOLING_TYPE, 난방코드는 HEATING_TYPE 그룹으로만 존재 검증(섞이면 400) + 각 dedup.
 * 냉방→FCLTY_DIV_CD=COOLING / 난방→HEATING 으로 분리 저장. 변경계 매퍼는 PRTNR_SN 2중가드.
 */
@Service
@RequiredArgsConstructor
public class RoomClimateServiceImpl implements RoomClimateService {

	private static final String Y = "Y";
	private static final String COOLING_TYPE_GROUP = "COOLING_TYPE";
	private static final String HEATING_TYPE_GROUP = "HEATING_TYPE";
	private static final String DIV_COOLING = "COOLING";
	private static final String DIV_HEATING = "HEATING";

	private final RoomClimateFacilityMapper roomClimateFacilityMapper;
	private final RoomMapper roomMapper;
	private final HouseMapper houseMapper;
	private final PartnerService partnerService;
	private final CodeService codeService;
	private final HouseProxyOwnerResolver proxyResolver;
	private final HouseReviewGuard reviewGuard;

	// ===================== 판매자(파트너 본인) — owner=토큰 해석 =====================

	@Override
	@Transactional
	@Audit(actn = "ROOM_CLIMATE_SET", trgtType = "ROOM", trgtId = "#request.roomSn()")
	public void setClimate(RoomClimateSetRequest request, AuthUser partner) {
		setClimateCore(request, requirePrtnrSn(partner), partner.userSn());
	}

	// ============ 운영자 대행(HOUSE_PROXY_MNG) — owner=대상 객실 서버해석, 행위자=운영자 ============

	@Override
	@Transactional
	@Audit(actn = "ROOM_PROXY_CLIMATE_SET", trgtType = "ROOM", trgtId = "#request.roomSn()")
	public void setClimateAsOperator(RoomClimateSetRequest request, AuthUser operator) {
		setClimateCore(request, proxyResolver.resolveOwnerForRoom(request.roomSn(), operator), operator.userSn());
		// 검수요청 상태였으면 내용 변경으로 DRAFT 자동복귀(+REVERT 이력)
		Long houseSn = roomMapper.selectHouseSnByRoom(request.roomSn());
		if (houseSn != null) {
			reviewGuard.markDirtyIfUnderReview(houseSn, operator.userSn());
		}
	}

	// ============ 코어(소유자=ownerPrtnrSn, 행위자=actorUserSn 공유 — 파트너/운영자 동일 로직) ============

	/** 냉난방 교체 — 부모 고택/객실 소유·활성 잠금 → 그룹별 코드 검증 → 활성 비움 → 냉방/난방 재삽입. */
	private void setClimateCore(RoomClimateSetRequest request, long ownerPrtnrSn, long actorUserSn) {
		lockOwnedActiveRoom(request.roomSn(), ownerPrtnrSn);
		List<String> coolingCds = validateGroup(request.coolingCds(), COOLING_TYPE_GROUP);
		List<String> heatingCds = validateGroup(request.heatingCds(), HEATING_TYPE_GROUP);
		roomClimateFacilityMapper.softDeleteActiveByRoom(request.roomSn(), ownerPrtnrSn, actorUserSn);
		insertAll(request.roomSn(), DIV_COOLING, coolingCds, actorUserSn);
		insertAll(request.roomSn(), DIV_HEATING, heatingCds, actorUserSn);
	}

	private void insertAll(long roomSn, String divCd, List<String> fcltyCds, long actorUserSn) {
		for (String fcltyCd : fcltyCds) {
			RoomClimateFacilityVO vo = RoomClimateFacilityVO.builder()
					.roomSn(roomSn)
					.fcltyDivCd(divCd)
					.fcltyCd(fcltyCd)
					.rgtrSn(actorUserSn)
					.build();
			roomClimateFacilityMapper.insertClimateFacility(vo);
		}
	}

	/** 그룹의 COMM_CD 에 존재 + 중복 없음인지 — 아니면 400. 반환=dedup 유지순서 목록. */
	private List<String> validateGroup(List<String> cds, String groupCd) {
		Set<String> allowed = codeService.list(groupCd).stream()
				.map(CodeItemResponse::cd).collect(Collectors.toSet());
		Set<String> seen = new HashSet<>();
		for (String cd : cds) {
			if (!allowed.contains(cd) || !seen.add(cd)) {
				throw new BusinessException(RoomErrorCode.ROOM_CLIMATE_CD_INVALID);
			}
		}
		return List.copyOf(cds);
	}

	// ------------------------------------------------------------------

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

	/**
	 * 변경계 잠금 — 락 순서 HOUSE→ROOM 통일(데드락 방지).
	 * houseSn 선확보 → 부모 고택 FOR UPDATE(소유·활성 검증) → 객실 FOR UPDATE(활성 검증).
	 */
	private void lockOwnedActiveRoom(long roomSn, long prtnrSn) {
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
	}
}
