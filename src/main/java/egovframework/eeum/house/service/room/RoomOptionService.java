package egovframework.eeum.house.service.room;

import java.util.List;

import egovframework.eeum.auth.vo.AuthUser;
import egovframework.eeum.house.web.dto.RoomOptionResponse;
import egovframework.eeum.house.web.dto.RoomOptionSetRequest;

/**
 * 객실 추가옵션 서비스 — 판매자(파트너 본인) + 운영자 대행(HOUSE_PROXY_MNG) 양쪽 경로.
 *
 * <p>소유권은 부모 객실→고택(PRTNR_SN)으로 판정. 설정은 교체(replace) 방식 — 기존 활성 옵션을 비우고 순서대로 재삽입.
 * 코어 로직은 (ownerPrtnrSn, actorUserSn) 으로 공유한다.
 */
public interface RoomOptionService {

	/** 객실 추가옵션 설정(교체, 본인 소유). */
	List<RoomOptionResponse> setOptions(RoomOptionSetRequest request, AuthUser partner);

	// ===== 운영자 대행(HOUSE_PROXY_MNG) — owner=대상 객실 서버해석, 행위자=운영자 =====

	/** 객실 추가옵션 설정(교체, 운영자 대행). */
	List<RoomOptionResponse> setOptionsAsOperator(RoomOptionSetRequest request, AuthUser operator);
}
