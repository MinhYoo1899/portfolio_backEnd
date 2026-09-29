package egovframework.eeum.house.service.publichouse;

import egovframework.eeum.base.response.PageResponse;
import egovframework.eeum.house.web.dto.AvailabilitySearchRequest;
import egovframework.eeum.house.web.dto.PublicAvailabilityResponse;
import egovframework.eeum.house.web.dto.PublicHouseDetailResponse;
import egovframework.eeum.house.web.dto.PublicHouseListItem;
import egovframework.eeum.house.web.dto.PublicHouseSearchRequest;

/**
 * 손님 공개 고택 브라우즈 서비스 — 비로그인 공개. ACTIVE 고택 + 승인·활성 파트너만 노출(소유권 무관).
 *
 * <p>비공개(DRAFT/HIDDEN/SUSPENDED/삭제/미승인·정지 파트너)는 전부 404 로 은닉한다(존재 여부 비노출).
 */
public interface PublicHouseService {

	PageResponse<PublicHouseListItem> list(PublicHouseSearchRequest request);

	/** 공개 고택 상세 — 이미지/편의시설/결제수단/객실요약/판매자 표시정보 조립. 비공개는 404. */
	PublicHouseDetailResponse detail(long houseSn);

	/** 공개 가용검색 — KST 과거 체크인 400, 비공개 고택 404, 이후 가용성 판정 위임 + 최저가 계산. */
	PublicAvailabilityResponse availability(AvailabilitySearchRequest request);
}
