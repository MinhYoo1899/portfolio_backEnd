package egovframework.eeum.content.service.popup;

import egovframework.eeum.auth.vo.AuthUser;
import egovframework.eeum.base.response.PageResponse;
import egovframework.eeum.content.web.dto.ContentPopupCreateRequest;
import egovframework.eeum.content.web.dto.ContentPopupResponse;
import egovframework.eeum.content.web.dto.ContentPopupSearchRequest;
import egovframework.eeum.content.web.dto.ContentPopupUpdateRequest;

/**
 * 콘텐츠 팝업 서비스 — 조회(필터)/등록/수정/토글/삭제(soft). status(EXPOSED/HIDDEN/ENDED)는 조회 시 산출.
 */
public interface ContentPopupService {

	/** 목록(노출위치/제목/상태 필터, status 산출 포함). */
	PageResponse<ContentPopupResponse> list(ContentPopupSearchRequest request);

	/** 단건 조회(수정 프리필). 없으면 CONTENT_NOT_FOUND. */
	ContentPopupResponse get(long popupId);

	/** 등록 — 이미지/위치/대상/linkTarget/노출기간 검증 후 저장. exposeMode 로 EXPOSE_YN. */
	ContentPopupResponse create(ContentPopupCreateRequest request, AuthUser user);

	/** 전체 교체 수정. 없으면 CONTENT_NOT_FOUND. */
	ContentPopupResponse update(long popupId, ContentPopupUpdateRequest request, AuthUser user);

	/** 노출 토글. 없으면 CONTENT_NOT_FOUND. */
	ContentPopupResponse toggle(long popupId, boolean isActive, AuthUser user);

	/** soft delete(DEL_YN='Y'). 없으면 CONTENT_NOT_FOUND. */
	void delete(long popupId, AuthUser user);
}
