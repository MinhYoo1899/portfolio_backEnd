package egovframework.eeum.content.service.banner;

import egovframework.eeum.auth.vo.AuthUser;
import egovframework.eeum.base.response.PageResponse;
import egovframework.eeum.content.web.dto.ContentBannerCreateRequest;
import egovframework.eeum.content.web.dto.ContentBannerResponse;
import egovframework.eeum.content.web.dto.ContentBannerUpdateRequest;

/**
 * 콘텐츠 메인배너 서비스 — 조회/등록/수정/토글. status(EXPOSED/HIDDEN/ENDED)는 조회 시 산출.
 */
public interface ContentBannerService {

	/** 목록(정렬순, status 산출 포함). */
	PageResponse<ContentBannerResponse> list();

	/** 단건 조회(수정 프리필). 없으면 CONTENT_NOT_FOUND. */
	ContentBannerResponse get(long bannerId);

	/** 등록 — 이미지/linkTarget/노출기간 검증 후 저장. */
	ContentBannerResponse create(ContentBannerCreateRequest request, AuthUser user);

	/** 전체 교체 수정. 없으면 CONTENT_NOT_FOUND. */
	ContentBannerResponse update(long bannerId, ContentBannerUpdateRequest request, AuthUser user);

	/** 노출 토글. 없으면 CONTENT_NOT_FOUND. */
	ContentBannerResponse toggle(long bannerId, boolean isActive, AuthUser user);
}
