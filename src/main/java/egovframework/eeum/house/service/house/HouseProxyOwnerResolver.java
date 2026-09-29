package egovframework.eeum.house.service.house;

import org.springframework.stereotype.Component;

import egovframework.eeum.account.service.partner.PartnerService;
import egovframework.eeum.account.web.dto.PartnerDetailResponse;
import egovframework.eeum.auth.vo.AuthUser;
import egovframework.eeum.base.exception.BusinessException;
import egovframework.eeum.house.exception.HouseErrorCode;
import egovframework.eeum.house.exception.RoomErrorCode;
import egovframework.eeum.house.service.house.dao.HouseMapper;
import egovframework.eeum.house.service.room.dao.RoomMapper;
import egovframework.eeum.house.vo.HouseVO;
import lombok.RequiredArgsConstructor;

/**
 * 운영자 대행 관리(HOUSE_PROXY_MNG)의 소유자 해석기 — 대상 고택의 owner PRTNR_SN 을 <b>서버에서</b> 해석.
 *
 * <p>운영자는 토큰에 prtnrSn 이 없으므로 관리 대상(houseSn)의 기존 소유자를 읽어 그대로 쓴다
 * (prtnrSn 을 body/query 로 받지 않음 — IDOR 표면 제거, codex MUST). 그 뒤 로직·매퍼의 {@code WHERE PRTNR_SN=?}
 * 이중가드는 파트너 경로와 동일하게 탄다(운영자도 해석된 owner 를 넘김 → 항상 일치).
 *
 * <p>소유 파트너가 승인(APPROVED)+계정활성(ACTIVE)인 고택만 대행 관리 허용. 이미지/편의시설/고택수정 등 house 단위 대행에서 공용.
 */
@Component
@RequiredArgsConstructor
public class HouseProxyOwnerResolver {

	private static final String Y = "Y";
	private static final String APRV_APPROVED = "APPROVED";
	private static final String ACNT_ACTIVE = "ACTIVE";

	private final HouseMapper houseMapper;
	private final RoomMapper roomMapper;
	private final PartnerService partnerService;

	/**
	 * 대상 고택의 owner PRTNR_SN 해석 + 대행 가능 검증.
	 *
	 * @throws BusinessException 미인증(401), 고택 미존재/삭제(404 HOUSE_NOT_FOUND), 소유 파트너 비승인·비활성(409)
	 */
	public long resolveOwnerForHouse(long houseSn, AuthUser operator) {
		if (operator == null) {
			throw BusinessException.unauthorized();
		}
		HouseVO house = houseMapper.selectHouseDetail(houseSn);
		if (house == null || Y.equals(house.getDelYn())) {
			throw new BusinessException(HouseErrorCode.HOUSE_NOT_FOUND);
		}
		return requireApprovedActiveOwner(house.getPrtnrSn());
	}

	/**
	 * 운영개시 사전점검(read-only) 전용 owner 해석 — 고택 존재/삭제/소유자만 확인하고 owner PRTNR_SN 반환한다.
	 * <b>파트너 APPROVED+ACTIVE 게이트는 적용하지 않는다</b> — 미승인/정지 파트너 고택도 "왜 발행 불가인지" 점검할 수 있어야
	 * 하므로(파트너 승인/활성 미충족은 {@code HousePublishValidator.evaluate} 가 PARTNER_NOT_APPROVED 로 수집).
	 * write/상태변경 대행은 반드시 {@link #resolveOwnerForHouse}(승인 게이트 유지)를 쓸 것.
	 *
	 * @throws BusinessException 미인증(401), 고택 미존재/삭제/소유자 미상(404 HOUSE_NOT_FOUND)
	 */
	public long resolveOwnerForHouseForPublishCheck(long houseSn, AuthUser operator) {
		if (operator == null) {
			throw BusinessException.unauthorized();
		}
		HouseVO house = houseMapper.selectHouseDetail(houseSn);
		if (house == null || Y.equals(house.getDelYn()) || house.getPrtnrSn() == null) {
			throw new BusinessException(HouseErrorCode.HOUSE_NOT_FOUND);
		}
		return house.getPrtnrSn();
	}

	/**
	 * 대상 객실의 부모 고택 owner PRTNR_SN 해석 + 대행 가능 검증.
	 *
	 * <p>roomSn → 부모 houseSn({@link RoomMapper#selectHouseSnByRoom}) → {@link #resolveOwnerForHouse} 로직 재사용.
	 * RoomMapper 는 같은 house 도메인 내라 직접 접근 허용(ArchUnit).
	 *
	 * @throws BusinessException 미인증(401), 객실 미존재/삭제(404 ROOM_NOT_FOUND), 고택 미존재/삭제(404),
	 *                           소유 파트너 비승인·비활성(409)
	 */
	public long resolveOwnerForRoom(long roomSn, AuthUser operator) {
		if (operator == null) {
			throw BusinessException.unauthorized();
		}
		Long houseSn = roomMapper.selectHouseSnByRoom(roomSn);
		if (houseSn == null) {
			throw new BusinessException(RoomErrorCode.ROOM_NOT_FOUND);
		}
		return resolveOwnerForHouse(houseSn, operator);
	}

	/** 소유 파트너 승인(APPROVED)+활성(ACTIVE) 게이트 — 아니면 409. */
	private long requireApprovedActiveOwner(long ownerPrtnrSn) {
		PartnerDetailResponse partner = partnerService.detail(ownerPrtnrSn);
		if (!APRV_APPROVED.equals(partner.aprvStts()) || !ACNT_ACTIVE.equals(partner.acntStts())) {
			throw BusinessException.conflict("승인된 활성 파트너의 고택만 대행 관리할 수 있습니다.");
		}
		return ownerPrtnrSn;
	}
}
