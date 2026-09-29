package egovframework.eeum.notification.service.notification.impl;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import egovframework.eeum.base.exception.BusinessException;
import egovframework.eeum.base.response.PageResponse;
import egovframework.eeum.notification.service.notification.OperatorNotificationService;
import egovframework.eeum.notification.service.notification.dao.OperatorNotificationMapper;
import egovframework.eeum.notification.web.dto.OperatorNotificationListItemResponse;
import lombok.RequiredArgsConstructor;

/**
 * 운영자 알림함 조회/읽음 서비스 구현 — 대상=토큰 본인(운영자 DB 경계검증). 조회·미읽음수는 최근 90일·미삭제만.
 */
@Service
@RequiredArgsConstructor
public class OperatorNotificationServiceImpl implements OperatorNotificationService {

	private static final int DEFAULT_SIZE = 20;
	private static final int MAX_SIZE = 100;

	private final OperatorNotificationMapper mapper;

	@Override
	@Transactional(readOnly = true)
	public PageResponse<OperatorNotificationListItemResponse> list(long userSn, int page, int size) {
		requireOperator(userSn);
		int p = page < 1 ? 1 : page;
		int s = size < 1 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
		int offset = (p - 1) * s;
		long total = mapper.countByReceiver(userSn);
		List<OperatorNotificationListItemResponse> items = mapper.selectByReceiver(userSn, s, offset).stream()
				.map(OperatorNotificationListItemResponse::from).toList();
		return PageResponse.of(items, total, p, s);
	}

	@Override
	@Transactional(readOnly = true)
	public long unreadCount(long userSn) {
		requireOperator(userSn);
		return mapper.countUnread(userSn);
	}

	@Override
	@Transactional
	public void markRead(long userSn, long notiSn) {
		requireOperator(userSn);
		mapper.markRead(userSn, notiSn); // 본인·미읽음일 때만 1건, 그 외 0 — 멱등(오류 아님)
	}

	@Override
	@Transactional
	public void markAllRead(long userSn) {
		requireOperator(userSn);
		mapper.markAllRead(userSn);
	}

	private void requireOperator(long userSn) {
		if (!mapper.existsOperator(userSn)) {
			throw BusinessException.forbidden();
		}
	}
}
