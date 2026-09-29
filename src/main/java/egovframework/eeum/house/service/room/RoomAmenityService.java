package egovframework.eeum.house.service.room;

import java.util.List;

import egovframework.eeum.auth.vo.AuthUser;
import egovframework.eeum.house.web.dto.AmenityResponse;
import egovframework.eeum.house.web.dto.RoomAmenitySetRequest;

/**
 * 객실-편의시설 매핑 서비스 — 판매자(파트너 본인) + 운영자 대행(HOUSE_PROXY_MNG) 양쪽 경로.
 *
 * <p>소유권은 부모 객실→고택(PRTNR_SN)으로 판정. 판매자는 토큰에서, 운영자는 대상 객실에서 owner 서버 해석.
 * 코어 로직은 (ownerPrtnrSn, actorUserSn) 으로 공유한다.
 */
public interface RoomAmenityService {

	/** 객실 편의시설 설정(전체 교체, 본인 소유). */
	void setAmenities(RoomAmenitySetRequest request, AuthUser partner);

	/** 객실 편의시설 목록(본인 소유, 사용중 항목). */
	List<AmenityResponse> listByRoom(long roomSn, AuthUser partner);

	// ===== 운영자 대행(HOUSE_PROXY_MNG) — owner=대상 객실 서버해석, 행위자=운영자 =====

	/** 객실 편의시설 설정(전체 교체, 운영자 대행). */
	void setAmenitiesAsOperator(RoomAmenitySetRequest request, AuthUser operator);

	/** 객실 편의시설 목록(운영자 대행, 사용중 항목). */
	List<AmenityResponse> listByRoomAsOperator(long roomSn, AuthUser operator);
}
