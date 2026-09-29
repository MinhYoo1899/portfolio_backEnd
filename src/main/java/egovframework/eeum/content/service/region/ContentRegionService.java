package egovframework.eeum.content.service.region;

import egovframework.eeum.auth.vo.AuthUser;
import egovframework.eeum.base.response.PageResponse;
import egovframework.eeum.content.web.dto.ContentRegionCreateRequest;
import egovframework.eeum.content.web.dto.ContentRegionResponse;
import egovframework.eeum.content.web.dto.ContentRegionSearchRequest;
import egovframework.eeum.content.web.dto.ContentRegionUpdateRequest;

/**
 * 콘텐츠 지역별 즐길거리 서비스 — 목록(필터·페이지네이션)/상세/등록/수정/토글/삭제(soft). status 는 조회 시 산출.
 */
public interface ContentRegionService {

	/** 목록(카테고리/유형/지역/제목 필터 + 페이지네이션, status 산출). */
	PageResponse<ContentRegionResponse> list(ContentRegionSearchRequest request);

	/** 단건 조회(수정 프리필/상세). 없으면 CONTENT_NOT_FOUND. */
	ContentRegionResponse get(long contentId);

	/** 등록 — 카테고리/유형/지역/이미지/노출기간 검증 후 저장. exposeMode 로 EXPOSE_YN. 조회수/댓글수 0. */
	ContentRegionResponse create(ContentRegionCreateRequest request, AuthUser user);

	/** 전체 교체 수정(조회수/댓글수 보존). 없으면 CONTENT_NOT_FOUND. */
	ContentRegionResponse update(long contentId, ContentRegionUpdateRequest request, AuthUser user);

	/** 활성 토글. 없으면 CONTENT_NOT_FOUND. */
	ContentRegionResponse toggle(long contentId, boolean isActive, AuthUser user);

	/** soft delete(DEL_YN='Y'). 없으면 CONTENT_NOT_FOUND. */
	void delete(long contentId, AuthUser user);
}
