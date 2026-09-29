package egovframework.eeum.house.service.room.impl;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import egovframework.eeum.account.service.partner.PartnerService;
import egovframework.eeum.auth.vo.AuthUser;
import egovframework.eeum.base.audit.Audit;
import egovframework.eeum.base.exception.BusinessException;
import egovframework.eeum.house.exception.HouseErrorCode;
import egovframework.eeum.house.exception.RoomErrorCode;
import egovframework.eeum.house.service.house.HouseProxyOwnerResolver;
import egovframework.eeum.house.service.house.HouseReviewGuard;
import egovframework.eeum.house.service.house.dao.HouseMapper;
import egovframework.eeum.house.service.room.RoomOptionService;
import egovframework.eeum.house.service.room.dao.RoomMapper;
import egovframework.eeum.house.service.room.dao.RoomOptionMapper;
import egovframework.eeum.house.vo.HouseVO;
import egovframework.eeum.house.vo.RoomOptionVO;
import egovframework.eeum.house.vo.RoomVO;
import egovframework.eeum.house.web.dto.RoomOptionResponse;
import egovframework.eeum.house.web.dto.RoomOptionSetRequest;
import lombok.RequiredArgsConstructor;

/**
 * 객실 추가옵션 서비스 구현 — 판매자(파트너 본인) + 운영자 대행(HOUSE_PROXY_MNG).
 *
 * <p>부모 객실/고택 소유권 격리(락 순서 HOUSE→ROOM), 옵션명 정합(trim·중복 금지) 검증, 교체(replace) 동기화.
 * 옵션명은 trim 후 비교하며 같은 객실 내 대소문자 무시 중복은 400. 변경계 매퍼는 PRTNR_SN 2중가드.
 */
@Service
@RequiredArgsConstructor
public class RoomOptionServiceImpl implements RoomOptionService {

	private static final String Y = "Y";

	private final RoomOptionMapper roomOptionMapper;
	private final RoomMapper roomMapper;
	private final HouseMapper houseMapper;
	private final PartnerService partnerService;
	private final HouseProxyOwnerResolver proxyResolver;
	private final HouseReviewGuard reviewGuard;

	// ===================== 판매자(파트너 본인) — owner=토큰 해석 =====================

	@Override
	@Transactional
	@Audit(actn = "ROOM_OPTION_SET", trgtType = "ROOM", trgtId = "#request.roomSn()")
	public List<RoomOptionResponse> setOptions(RoomOptionSetRequest request, AuthUser partner) {
		return setOptionsCore(request, requirePrtnrSn(partner), partner.userSn());
	}

	// ============ 운영자 대행(HOUSE_PROXY_MNG) — owner=대상 객실 서버해석, 행위자=운영자 ============

	@Override
	@Transactional
	@Audit(actn = "ROOM_PROXY_OPTION_SET", trgtType = "ROOM", trgtId = "#request.roomSn()")
	public List<RoomOptionResponse> setOptionsAsOperator(RoomOptionSetRequest request, AuthUser operator) {
		List<RoomOptionResponse> result = setOptionsCore(request,
				proxyResolver.resolveOwnerForRoom(request.roomSn(), operator), operator.userSn());
		// 검수요청 상태였으면 내용 변경으로 DRAFT 자동복귀(+REVERT 이력)
		Long houseSn = roomMapper.selectHouseSnByRoom(request.roomSn());
		if (houseSn != null) {
			reviewGuard.markDirtyIfUnderReview(houseSn, operator.userSn());
		}
		return result;
	}

	// ============ 코어(소유자=ownerPrtnrSn, 행위자=actorUserSn 공유 — 파트너/운영자 동일 로직) ============

	/** 옵션 교체 — 부모 고택/객실 소유·활성 잠금 → 옵션명 정합 검증 → 활성 비움 → 순서대로 재삽입. 반환=설정 결과. */
	private List<RoomOptionResponse> setOptionsCore(RoomOptionSetRequest request, long ownerPrtnrSn,
			long actorUserSn) {
		lockOwnedActiveRoom(request.roomSn(), ownerPrtnrSn);
		List<RoomOptionSetRequest.Item> items = request.items();
		validateOptionNames(items);
		roomOptionMapper.softDeleteActiveByRoom(request.roomSn(), ownerPrtnrSn, actorUserSn);
		int seq = 0;
		try {
			for (RoomOptionSetRequest.Item item : items) {
				RoomOptionVO vo = RoomOptionVO.builder()
						.roomSn(request.roomSn())
						.optionNm(item.optionNm().trim())
						.addAmt(item.addAmt())
						.sortSeq(seq++)
						.rgtrSn(actorUserSn)
						.build();
				roomOptionMapper.insertOption(vo);
			}
		} catch (org.springframework.dao.DuplicateKeyException e) {
			// 앱 중복검사(대소문자)보다 DB 콜레이션(ai_ci)이 강해 accent/호환등가 변형이 활성유니크 충돌 → 약속된 400으로 변환
			throw new BusinessException(RoomErrorCode.ROOM_OPTION_DUPLICATE,
					RoomErrorCode.ROOM_OPTION_DUPLICATE.defaultMessage(), e);
		}
		return roomOptionMapper.selectActiveByRoom(request.roomSn()).stream().map(RoomOptionResponse::from).toList();
	}

	/** 옵션명 trim 후 공백 금지 + 같은 객실 내 대소문자 무시 중복 금지 — 아니면 400. */
	private void validateOptionNames(List<RoomOptionSetRequest.Item> items) {
		Set<String> seen = new HashSet<>();
		for (RoomOptionSetRequest.Item item : items) {
			String nm = item.optionNm() == null ? "" : item.optionNm().trim();
			if (nm.isEmpty()) {
				throw new BusinessException(RoomErrorCode.ROOM_OPTION_INVALID);
			}
			if (!seen.add(nm.toLowerCase(java.util.Locale.ROOT))) {
				throw new BusinessException(RoomErrorCode.ROOM_OPTION_DUPLICATE);
			}
		}
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
