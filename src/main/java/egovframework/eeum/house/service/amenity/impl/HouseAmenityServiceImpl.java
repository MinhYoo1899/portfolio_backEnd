package egovframework.eeum.house.service.amenity.impl;

import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import egovframework.eeum.account.service.partner.PartnerService;
import egovframework.eeum.auth.vo.AuthUser;
import egovframework.eeum.base.audit.Audit;
import egovframework.eeum.base.exception.BusinessException;
import egovframework.eeum.house.exception.AmenityErrorCode;
import egovframework.eeum.house.exception.HouseErrorCode;
import egovframework.eeum.house.service.amenity.HouseAmenityService;
import egovframework.eeum.house.service.amenity.dao.AmenityMapper;
import egovframework.eeum.house.service.amenity.dao.HouseAmenityMapper;
import egovframework.eeum.house.service.house.HouseProxyOwnerResolver;
import egovframework.eeum.house.service.house.HouseReviewGuard;
import egovframework.eeum.house.service.house.dao.HouseMapper;
import egovframework.eeum.house.vo.HouseVO;
import egovframework.eeum.house.web.dto.AmenityResponse;
import egovframework.eeum.house.web.dto.HouseAmenitySetRequest;
import lombok.RequiredArgsConstructor;

/**
 * 고택-편의시설 매핑 서비스 구현(판매자) — 부모 고택 소유권 격리, 편의시설 유효성 검증, 집합 교체 동기화.
 */
@Service
@RequiredArgsConstructor
public class HouseAmenityServiceImpl implements HouseAmenityService {

	private static final String Y = "Y";
	/** 고택 매핑 허용 적용범위(고택=HOUSE 또는 BOTH). */
	private static final List<String> HOUSE_SCOPES = List.of("HOUSE", "BOTH");

	private final HouseAmenityMapper houseAmenityMapper;
	private final AmenityMapper amenityMapper;
	private final HouseMapper houseMapper;
	private final PartnerService partnerService;
	private final HouseProxyOwnerResolver proxyResolver;
	private final HouseReviewGuard reviewGuard;

	// ===================== 판매자(파트너 본인) — owner=토큰 해석 =====================

	@Override
	@Transactional
	@Audit(actn = "HOUSE_AMENITY_SET", trgtType = "HOUSE", trgtId = "#request.houseSn()")
	public void setAmenities(HouseAmenitySetRequest request, AuthUser partner) {
		setAmenitiesCore(request, requirePrtnrSn(partner), partner.userSn());
	}

	@Override
	@Transactional(readOnly = true)
	public List<AmenityResponse> listByHouse(long houseSn, AuthUser partner) {
		return listCore(houseSn, requirePrtnrSn(partner));
	}

	// ============ 운영자 대행(HOUSE_PROXY_MNG) — owner=대상고택 서버해석, 행위자=운영자 ============

	@Override
	@Transactional
	@Audit(actn = "HOUSE_PROXY_AMENITY_SET", trgtType = "HOUSE", trgtId = "#request.houseSn()")
	public void setAmenitiesAsOperator(HouseAmenitySetRequest request, AuthUser operator) {
		setAmenitiesCore(request, proxyResolver.resolveOwnerForHouse(request.houseSn(), operator), operator.userSn());
		// 검수요청 상태였으면 내용 변경으로 DRAFT 자동복귀(+REVERT 이력)
		reviewGuard.markDirtyIfUnderReview(request.houseSn(), operator.userSn());
	}

	@Override
	@Transactional(readOnly = true)
	public List<AmenityResponse> listByHouseAsOperator(long houseSn, AuthUser operator) {
		return listCore(houseSn, proxyResolver.resolveOwnerForHouse(houseSn, operator));
	}

	// ============ 코어(소유자=ownerPrtnrSn, 행위자=actorUserSn 공유 — 파트너/운영자 동일 로직) ============

	private void setAmenitiesCore(HouseAmenitySetRequest request, long ownerPrtnrSn, long actorUserSn) {
		requireOwnedActiveHouse(request.houseSn(), ownerPrtnrSn); // 고택 FOR UPDATE — 동시 설정 직렬화
		List<Long> requested = request.amenitySns().stream().distinct().toList();
		// 요청 편의시설이 전부 활성·고택범위(HOUSE/BOTH)인지 검증(비활성/객실전용/임의 ID 차단). 승인 게이트 없음.
		if (!requested.isEmpty()
				&& amenityMapper.countActiveScopedByIds(requested, HOUSE_SCOPES) != requested.size()) {
			throw new BusinessException(AmenityErrorCode.AMENITY_NOT_FOUND);
		}
		Set<Long> current = Set.copyOf(houseAmenityMapper.selectActiveAmenitySnsByHouse(request.houseSn()));
		// 보존 집합에 없는 기존 매핑 해제
		houseAmenityMapper.softDeleteHouseAmenitiesNotIn(request.houseSn(), ownerPrtnrSn, requested, actorUserSn);
		// 새로 추가된 것만 삽입(이미 활성인 건 유지)
		for (Long amenitySn : requested) {
			if (!current.contains(amenitySn)) {
				houseAmenityMapper.insertHouseAmenity(request.houseSn(), amenitySn, actorUserSn);
			}
		}
	}

	private List<AmenityResponse> listCore(long houseSn, long ownerPrtnrSn) {
		HouseVO house = houseMapper.selectHouseDetail(houseSn);
		if (house == null || !house.getPrtnrSn().equals(ownerPrtnrSn)) {
			throw new BusinessException(HouseErrorCode.HOUSE_NOT_FOUND);
		}
		return houseAmenityMapper.selectActiveAmenitiesByHouse(houseSn).stream()
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

	private void requireOwnedActiveHouse(long houseSn, long prtnrSn) {
		HouseVO house = houseMapper.selectHouseForUpdate(houseSn);
		if (house == null || Y.equals(house.getDelYn()) || !house.getPrtnrSn().equals(prtnrSn)) {
			throw new BusinessException(HouseErrorCode.HOUSE_NOT_FOUND);
		}
	}
}
