package egovframework.eeum.content.service.adbanner;

import egovframework.eeum.auth.vo.AuthUser;
import egovframework.eeum.base.response.PageResponse;
import egovframework.eeum.content.web.dto.ContentAdBannerCreateRequest;
import egovframework.eeum.content.web.dto.ContentAdBannerResponse;
import egovframework.eeum.content.web.dto.ContentAdBannerUpdateRequest;

/**
 * 콘텐츠 광고배너 서비스 — 조회/등록/수정/토글/삭제(soft).
 * status(EXPOSED/HIDDEN/ENDED)는 조회 시 산출(메인배너 {@code ContentBannerStatus} 재사용).
 */
public interface ContentAdBannerService {

	/** 목록(노출위치 정렬순, status 산출 포함). */
	PageResponse<ContentAdBannerResponse> list();

	/** 단건 조회(수정 프리필). 없으면 CONTENT_NOT_FOUND. */
	ContentAdBannerResponse get(long adBannerId);

	/** 등록 — position/이미지/linkTarget/노출기간 검증 후 저장. 생성 시 EXPOSE_YN 기본 Y. */
	ContentAdBannerResponse create(ContentAdBannerCreateRequest request, AuthUser user);

	/** 전체 교체 수정(EXPOSE_YN 보존). 없으면 CONTENT_NOT_FOUND. */
	ContentAdBannerResponse update(long adBannerId, ContentAdBannerUpdateRequest request, AuthUser user);

	/** 노출 토글. 없으면 CONTENT_NOT_FOUND. */
	ContentAdBannerResponse toggle(long adBannerId, boolean isActive, AuthUser user);

	/** soft delete(DEL_YN='Y'). 없으면 CONTENT_NOT_FOUND. */
	void delete(long adBannerId, AuthUser user);
}
