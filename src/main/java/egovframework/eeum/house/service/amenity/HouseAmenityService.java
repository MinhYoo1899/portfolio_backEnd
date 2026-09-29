package egovframework.eeum.house.service.amenity;

import java.util.List;

import egovframework.eeum.auth.vo.AuthUser;
import egovframework.eeum.house.web.dto.AmenityResponse;
import egovframework.eeum.house.web.dto.HouseAmenitySetRequest;

/**
 * 고택-편의시설 매핑 서비스(판매자) — 본인 소유 고택의 편의시설 선택 집합 관리/조회.
 */
public interface HouseAmenityService {

	/** 고택 편의시설 설정(전체 교체, 본인 소유). */
	void setAmenities(HouseAmenitySetRequest request, AuthUser partner);

	/** 고택 편의시설 목록(본인 소유, 사용중 항목). */
	List<AmenityResponse> listByHouse(long houseSn, AuthUser partner);

	// ===== 운영자 대행(HOUSE_PROXY_MNG) — owner=대상고택 서버해석, 행위자=운영자 =====

	/** 고택 편의시설 설정(전체 교체, 운영자 대행). */
	void setAmenitiesAsOperator(HouseAmenitySetRequest request, AuthUser operator);

	/** 고택 편의시설 목록(운영자 대행, 사용중 항목). */
	List<AmenityResponse> listByHouseAsOperator(long houseSn, AuthUser operator);
}
