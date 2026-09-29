package egovframework.eeum.house.service.room;

import egovframework.eeum.auth.vo.AuthUser;
import egovframework.eeum.house.web.dto.RoomClimateSetRequest;

/**
 * 객실 냉난방시설 서비스 — 판매자(파트너 본인) + 운영자 대행(HOUSE_PROXY_MNG) 양쪽 경로.
 *
 * <p>소유권은 부모 객실→고택(PRTNR_SN)으로 판정. 설정은 교체(replace) 방식 — 기존 활성 냉난방시설을 비우고 재삽입.
 * 냉방/난방 각각 다중선택이며 그룹별 COMM_CD(COOLING_TYPE/HEATING_TYPE) 검증. 코어 로직은 (ownerPrtnrSn, actorUserSn) 으로 공유한다.
 */
public interface RoomClimateService {

	/** 객실 냉난방시설 설정(교체, 본인 소유). */
	void setClimate(RoomClimateSetRequest request, AuthUser partner);

	// ===== 운영자 대행(HOUSE_PROXY_MNG) — owner=대상 객실 서버해석, 행위자=운영자 =====

	/** 객실 냉난방시설 설정(교체, 운영자 대행). */
	void setClimateAsOperator(RoomClimateSetRequest request, AuthUser operator);
}
