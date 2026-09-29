package egovframework.eeum.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import egovframework.eeum.base.exception.BusinessException;
import egovframework.eeum.notification.service.notification.dao.OperatorNotificationMapper;
import egovframework.eeum.notification.service.notification.impl.OperatorNotificationServiceImpl;
import egovframework.eeum.notification.vo.OperatorNotificationVO;

/**
 * 운영자 알림함 서비스 검증 — 운영자 경계(DB), 페이징 위임(offset 계산), 미읽음수, 읽음 위임, 비운영자 차단.
 */
class OperatorNotificationServiceVerification {

	private static final long OPERATOR = 55L;
	private static final long NON_OPERATOR = 66L;

	private final OperatorNotificationMapper mapper = mock(OperatorNotificationMapper.class);
	private final OperatorNotificationServiceImpl service = new OperatorNotificationServiceImpl(mapper);

	@Test
	@DisplayName("목록 — 운영자 + 페이징 위임(page2 size10 → offset10)")
	void listDelegatesWithPaging() {
		when(mapper.existsOperator(OPERATOR)).thenReturn(true);
		when(mapper.countByReceiver(OPERATOR)).thenReturn(3L);
		OperatorNotificationVO vo = new OperatorNotificationVO();
		vo.setOperatorNotiSn(1L);
		vo.setNotiTypeCd("PARTNER_AUTH");
		when(mapper.selectByReceiver(OPERATOR, 10, 10)).thenReturn(List.of(vo));

		service.list(OPERATOR, 2, 10);

		verify(mapper).selectByReceiver(OPERATOR, 10, 10);
	}

	@Test
	@DisplayName("미읽음수 — 운영자 + 실제 수치 반환")
	void unreadCountReturnsActual() {
		when(mapper.existsOperator(OPERATOR)).thenReturn(true);
		when(mapper.countUnread(OPERATOR)).thenReturn(7L);

		assertEquals(7L, service.unreadCount(OPERATOR));
	}

	@Test
	@DisplayName("개별/전체 읽음 — 운영자 + 매퍼 위임")
	void markReadDelegates() {
		when(mapper.existsOperator(OPERATOR)).thenReturn(true);

		service.markRead(OPERATOR, 9L);
		service.markAllRead(OPERATOR);

		verify(mapper).markRead(OPERATOR, 9L);
		verify(mapper).markAllRead(OPERATOR);
	}

	@Test
	@DisplayName("차단 — 비운영자는 목록/미읽음/읽음 전부 403, 매퍼 조회·갱신 미호출")
	void nonOperatorBlocked() {
		when(mapper.existsOperator(NON_OPERATOR)).thenReturn(false);

		assertThrows(BusinessException.class, () -> service.list(NON_OPERATOR, 1, 20));
		assertThrows(BusinessException.class, () -> service.unreadCount(NON_OPERATOR));
		assertThrows(BusinessException.class, () -> service.markRead(NON_OPERATOR, 1L));
		assertThrows(BusinessException.class, () -> service.markAllRead(NON_OPERATOR));

		verify(mapper, never()).selectByReceiver(anyLong(), anyInt(), anyInt());
		verify(mapper, never()).countUnread(anyLong());
		verify(mapper, never()).markRead(anyLong(), anyLong());
		verify(mapper, never()).markAllRead(anyLong());
	}
}
