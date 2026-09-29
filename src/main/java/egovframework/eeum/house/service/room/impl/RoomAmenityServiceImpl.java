package egovframework.eeum.house.service.room.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import egovframework.eeum.account.service.partner.PartnerService;
import egovframework.eeum.auth.vo.AuthUser;
import egovframework.eeum.base.audit.Audit;
import egovframework.eeum.base.exception.BusinessException;
import egovframework.eeum.house.exception.AmenityErrorCode;
import egovframework.eeum.house.exception.HouseErrorCode;
import egovframework.eeum.house.exception.RoomErrorCode;
import egovframework.eeum.house.service.amenity.dao.AmenityMapper;
import egovframework.eeum.house.service.house.HouseProxyOwnerResolver;
import egovframework.eeum.house.service.house.HouseReviewGuard;
import egovframework.eeum.house.service.house.dao.HouseMapper;
import egovframework.eeum.house.service.room.RoomAmenityService;
import egovframework.eeum.house.service.room.dao.RoomAmenityMapper;
import egovframework.eeum.house.service.room.dao.RoomMapper;
import egovframework.eeum.house.vo.AmenityVO;
import egovframework.eeum.house.vo.HouseVO;
import egovframework.eeum.house.vo.RoomVO;
import egovframework.eeum.house.web.dto.AmenityResponse;
import egovframework.eeum.house.web.dto.RoomAmenitySetRequest;
import lombok.RequiredArgsConstructor;

/**
 * 객실-편의시설 매핑 서비스 구현 — 판매자(파트너 본인) + 운영자 대행(HOUSE_PROXY_MNG).
 *
 * <p>부모 객실/고택 소유권 격리(락 순서 HOUSE→ROOM), 편의시설 카탈로그 유효성 검증(활성·객실범위), 집합 교체 동기화.
 * 카탈로그 검증은 {@link AmenityMapper#countActiveScopedByIds}. 변경계 매퍼는 PRTNR_SN 2중가드. 승인 게이트 없음.
 */
@Service
@RequiredArgsConstructor
public class RoomAmenityServiceImpl implements RoomAmenityService {

	private static final String Y = "Y";
	/** 객실 매핑 허용 적용범위(객실=ROOM 또는 BOTH). */
	private static final List<String> ROOM_SCOPES = List.of("ROOM", "BOTH");
	/** 커스텀 직접입력 기본 분류/범위(운영자가 카탈로그 관리에서 재조정 가능). */
	private static final String CUSTOM_DEFAULT_CTGRY = "ROOM_FEATURE";
	private static final String SCOPE_ROOM = "ROOM";

	private final RoomAmenityMapper roomAmenityMapper;
	private final AmenityMapper amenityMapper;
	private final RoomMapper roomMapper;
	private final HouseMapper houseMapper;
	private final PartnerService partnerService;
	private final HouseProxyOwnerResolver proxyResolver;
	private final HouseReviewGuard reviewGuard;

	// ===================== 판매자(파트너 본인) — owner=토큰 해석 =====================

	@Override
	@Transactional
	@Audit(actn = "ROOM_AMENITY_SET", trgtType = "ROOM", trgtId = "#request.roomSn()")
	public void setAmenities(RoomAmenitySetRequest request, AuthUser partner) {
		setAmenitiesCore(request, requirePrtnrSn(partner), partner.userSn());
	}

	@Override
	@Transactional(readOnly = true)
	public List<AmenityResponse> listByRoom(long roomSn, AuthUser partner) {
		return listCore(roomSn, requirePrtnrSn(partner));
	}

	// ============ 운영자 대행(HOUSE_PROXY_MNG) — owner=대상 객실 서버해석, 행위자=운영자 ============

	@Override
	@Transactional
	@Audit(actn = "ROOM_PROXY_AMENITY_SET", trgtType = "ROOM", trgtId = "#request.roomSn()")
	public void setAmenitiesAsOperator(RoomAmenitySetRequest request, AuthUser operator) {
		setAmenitiesCore(request, proxyResolver.resolveOwnerForRoom(request.roomSn(), operator), operator.userSn());
		// 검수요청 상태였으면 내용 변경으로 DRAFT 자동복귀(+REVERT 이력)
		Long houseSn = roomMapper.selectHouseSnByRoom(request.roomSn());
		if (houseSn != null) {
			reviewGuard.markDirtyIfUnderReview(houseSn, operator.userSn());
		}
	}

	@Override
	@Transactional(readOnly = true)
	public List<AmenityResponse> listByRoomAsOperator(long roomSn, AuthUser operator) {
		return listCore(roomSn, proxyResolver.resolveOwnerForRoom(roomSn, operator));
	}

	// ============ 코어(소유자=ownerPrtnrSn, 행위자=actorUserSn 공유 — 파트너/운영자 동일 로직) ============

	private void setAmenitiesCore(RoomAmenitySetRequest request, long ownerPrtnrSn, long actorUserSn) {
		lockOwnedActiveRoom(request.roomSn(), ownerPrtnrSn); // HOUSE→ROOM FOR UPDATE — 동시 설정 직렬화
		List<Long> selected = request.amenitySns().stream().distinct().toList();

		// 1) 선택 전체 검증 — "활성·객실범위(ROOM/BOTH)" 카탈로그 항목만 허용(비활성/타범위/임의ID 차단).
		//    이미 매핑된 항목도 재검증한다(비활성·타범위로 바뀐 항목을 ID 재전송만으로 유지하는 우회 차단 — codex 치명).
		if (!selected.isEmpty()
				&& amenityMapper.countActiveScopedByIds(selected, ROOM_SCOPES) != selected.size()) {
			throw new BusinessException(AmenityErrorCode.AMENITY_NOT_FOUND);
		}

		// 2) 커스텀(직접입력) → 즉시 활성 마스터행 생성/재사용(승인 게이트 없음) 후 매핑 대상에 합류
		List<Long> customSns = new ArrayList<>();
		for (String nm : request.customAmenityNms()) {
			customSns.add(createOrReuseCustom(nm, actorUserSn));
		}

		// 3) 최종 보존 집합 = 선택 + 커스텀(중복 제거)
		List<Long> keep = Stream.concat(selected.stream(), customSns.stream()).distinct().toList();

		// 4) 교체 동기화 — 보존 집합에 없는 기존 매핑 해제 + 신규만 삽입(current 는 중복삽입 방지용)
		Set<Long> current = Set.copyOf(roomAmenityMapper.selectActiveAmenitySnsByRoom(request.roomSn()));
		roomAmenityMapper.softDeleteRoomAmenitiesNotIn(request.roomSn(), ownerPrtnrSn, keep, actorUserSn);
		for (Long amenitySn : keep) {
			if (!current.contains(amenitySn)) {
				roomAmenityMapper.insertRoomAmenity(request.roomSn(), amenitySn, actorUserSn);
			}
		}
	}

	/**
	 * 커스텀 편의시설명 → 즉시 활성 마스터행 insert-or-get(원자적). 동일 활성명이 이미 있으면(동시요청/기등록)
	 * ON DUPLICATE KEY 로 그 행의 amenitySn 을 받는다(경합 안전). 단 재사용 항목이 객실 매핑 가능(활성·범위 ROOM/BOTH)인지
	 * 검증해 HOUSE 전용/비활성 항목 우회를 차단. 반환=매핑할 amenitySn.
	 */
	private long createOrReuseCustom(String amenityNm, long actorUserSn) {
		AmenityVO vo = AmenityVO.builder()
				.amenityCtgryCd(CUSTOM_DEFAULT_CTGRY)
				.applyScopeCd(SCOPE_ROOM)
				.amenityNm(amenityNm)
				.requestUserSn(actorUserSn)
				.build();
		amenityMapper.insertCustomAmenity(vo); // 신규 활성 커스텀 생성 또는 동일활성명 기존행 SN 수신
		long sn = vo.getAmenitySn();
		if (amenityMapper.countActiveScopedByIds(List.of(sn), ROOM_SCOPES) != 1) {
			throw new BusinessException(AmenityErrorCode.AMENITY_NOT_FOUND,
					"직접입력 편의시설명이 매핑 불가 항목과 충돌합니다.");
		}
		return sn;
	}

	private List<AmenityResponse> listCore(long roomSn, long ownerPrtnrSn) {
		RoomVO room = roomMapper.selectRoomDetail(roomSn);
		if (room == null || room.getOwnerPrtnrSn() == null || room.getOwnerPrtnrSn() != ownerPrtnrSn) {
			throw new BusinessException(RoomErrorCode.ROOM_NOT_FOUND);
		}
		return roomAmenityMapper.selectActiveAmenitiesByRoom(roomSn).stream()
				.map(AmenityResponse::from).toList();
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
