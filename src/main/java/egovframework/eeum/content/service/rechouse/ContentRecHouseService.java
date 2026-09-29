package egovframework.eeum.content.service.rechouse;

import egovframework.eeum.auth.vo.AuthUser;
import egovframework.eeum.base.response.PageResponse;
import egovframework.eeum.content.web.dto.ContentRecHouseCreateRequest;
import egovframework.eeum.content.web.dto.ContentRecHouseOrderRequest;
import egovframework.eeum.content.web.dto.ContentRecHouseResponse;
import egovframework.eeum.content.web.dto.ContentRecHouseUpdateRequest;

/**
 * 콘텐츠 추천고택 서비스 — 조회(분류)/등록/수정/토글/순서저장/삭제(soft). status 는 조회 시 산출.
 */
public interface ContentRecHouseService {

	/** 목록(분류 필터, SORT_ORDER 순, status 산출, 고택명/주소 포함). */
	PageResponse<ContentRecHouseResponse> list(String recCategoryCd);

	/** 단건 조회(수정 프리필). 없으면 CONTENT_NOT_FOUND. */
	ContentRecHouseResponse get(long recId);

	/** 등록 — 분류/고택 검증 후 저장. exposeMode 로 EXPOSE_YN. */
	ContentRecHouseResponse create(ContentRecHouseCreateRequest request, AuthUser user);

	/** 전체 교체 수정. 없으면 CONTENT_NOT_FOUND. */
	ContentRecHouseResponse update(long recId, ContentRecHouseUpdateRequest request, AuthUser user);

	/** 노출 토글. 없으면 CONTENT_NOT_FOUND. */
	ContentRecHouseResponse toggle(long recId, boolean isActive, AuthUser user);

	/** 순서 저장(드래그앤드롭 배열 전체 반영). */
	void reorder(ContentRecHouseOrderRequest request, AuthUser user);

	/** soft delete(DEL_YN='Y'). 없으면 CONTENT_NOT_FOUND. */
	void delete(long recId, AuthUser user);
}
