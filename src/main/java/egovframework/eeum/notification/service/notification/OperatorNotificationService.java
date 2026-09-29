package egovframework.eeum.notification.service.notification;

import egovframework.eeum.base.response.PageResponse;
import egovframework.eeum.notification.web.dto.OperatorNotificationListItemResponse;

/**
 * 운영자 <b>본인</b> 알림함 조회/읽음 — 대상은 항상 토큰 본인(운영자 DB 검증). 최근 90일·미삭제만.
 */
public interface OperatorNotificationService {

	/** 본인 알림 목록(최근 90일, 최신순, 페이징). */
	PageResponse<OperatorNotificationListItemResponse> list(long userSn, int page, int size);

	/** 본인 미읽음 수(실제값 — 표시 캡은 프론트). */
	long unreadCount(long userSn);

	/** 본인 알림 1건 읽음(본인 것만, 멱등). */
	void markRead(long userSn, long notiSn);

	/** 본인 미읽음 전체 읽음. */
	void markAllRead(long userSn);
}
