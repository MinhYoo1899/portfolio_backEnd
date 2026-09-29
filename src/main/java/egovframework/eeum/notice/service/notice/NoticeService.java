package egovframework.eeum.notice.service.notice;

import egovframework.eeum.auth.vo.AuthUser;
import egovframework.eeum.base.response.PageResponse;
import egovframework.eeum.notice.web.dto.NoticeCreateRequest;
import egovframework.eeum.notice.web.dto.NoticeResponse;
import egovframework.eeum.notice.web.dto.NoticeSearchRequest;
import egovframework.eeum.notice.web.dto.NoticeUpdateRequest;

/**
 * 공지사항 서비스 — 목록/상세/작성/수정/종료/삭제 + 예약공지 자동승격(cron).
 * 상태(DRAFT/SCHEDULED/PUBLISHED_ONGOING/CLOSED)는 저장 라이프사이클. 알림=앱푸시 스텁(FCM 미설정 시 기록만).
 */
public interface NoticeService {

	/** 목록(상태/대상/알림/제목 필터 + 페이지네이션). */
	PageResponse<NoticeResponse> list(NoticeSearchRequest request);

	/** 단건 조회. 없으면 NOTICE_NOT_FOUND. */
	NoticeResponse get(long noticeId);

	/** 작성 — saveAsDraft/sendTiming 조합으로 상태 산정. 발행 시 알림 발송(스텁). */
	NoticeResponse create(NoticeCreateRequest request, AuthUser user);

	/** 편집(상태 보존). 없으면 NOTICE_NOT_FOUND. */
	NoticeResponse update(long noticeId, NoticeUpdateRequest request, AuthUser user);

	/** 수동 종료 — 게시중만 CLOSED. 없으면 404, 게시중 아니면 409. */
	NoticeResponse close(long noticeId, AuthUser user);

	/** soft delete. 없으면 NOTICE_NOT_FOUND. */
	void delete(long noticeId, AuthUser user);

	/** 예약공지 자동승격(cron) — 예약시각 도래분 게시중 전이 + 알림(스텁). 승격 건수 반환. */
	int promoteScheduled();
}
