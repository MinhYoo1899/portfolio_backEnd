package egovframework.eeum.notice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import egovframework.eeum.auth.vo.AuthUser;
import egovframework.eeum.base.exception.BusinessException;
import egovframework.eeum.notice.exception.NoticeErrorCode;
import egovframework.eeum.notice.service.notice.dao.NoticeMapper;
import egovframework.eeum.notice.service.notice.impl.NoticeServiceImpl;
import egovframework.eeum.notice.vo.NoticeVO;
import egovframework.eeum.notice.web.dto.NoticeCreateRequest;

/**
 * 공지 서비스 검증 — 대상/내용/예약일시 validation + 상태 산정(DRAFT/SCHEDULED/PUBLISHED_ONGOING) + 종료전이 + 404/409.
 */
class NoticeServiceVerification {

	private final NoticeMapper noticeMapper = mock(NoticeMapper.class);
	private final NoticeServiceImpl service = new NoticeServiceImpl(noticeMapper,
			mock(egovframework.eeum.notification.service.notification.NotificationEmitter.class));

	private static AuthUser user() {
		return new AuthUser(9L, "op@eeum.dev", "운영자", "OPERATOR", "ACTIVE", List.of("NOTICE_MNG"));
	}

	private NoticeCreateRequest req(String content, String target, String timing, Instant scheduledAt, Boolean draft) {
		return new NoticeCreateRequest("점검 공지", content, target, timing, scheduledAt, draft);
	}

	private void stubSaved(long sn) {
		NoticeVO vo = new NoticeVO();
		vo.setNoticeSn(sn);
		vo.setTitle("점검 공지");
		vo.setNoticeSttsCd("PUBLISHED_ONGOING");
		when(noticeMapper.selectNoticeById(sn)).thenReturn(vo);
	}

	private ArgumentCaptor<NoticeVO> stubInsert(long sn) {
		ArgumentCaptor<NoticeVO> cap = ArgumentCaptor.forClass(NoticeVO.class);
		doAnswer(inv -> {
			inv.getArgument(0, NoticeVO.class).setNoticeSn(sn);
			return 1;
		}).when(noticeMapper).insertNotice(any());
		stubSaved(sn);
		return cap;
	}

	@Test
	@DisplayName("없는 대상 → NOTICE_TARGET_INVALID")
	void target_invalid() {
		BusinessException e = assertThrows(() -> service.create(
				req("내용", "EVERYONE", "IMMEDIATE", null, false), user()));
		assertEquals(NoticeErrorCode.NOTICE_TARGET_INVALID, e.getErrorCode());
	}

	@Test
	@DisplayName("즉시발행인데 내용 없음 → NOTICE_CONTENT_REQUIRED")
	void immediate_noContent() {
		BusinessException e = assertThrows(() -> service.create(
				req(null, "ALL", "IMMEDIATE", null, false), user()));
		assertEquals(NoticeErrorCode.NOTICE_CONTENT_REQUIRED, e.getErrorCode());
	}

	@Test
	@DisplayName("예약발송인데 과거 시각 → NOTICE_SCHEDULED_AT_INVALID")
	void scheduled_pastTime() {
		BusinessException e = assertThrows(() -> service.create(
				req("내용", "ALL", "SCHEDULED", Instant.now().minusSeconds(3600), false), user()));
		assertEquals(NoticeErrorCode.NOTICE_SCHEDULED_AT_INVALID, e.getErrorCode());
	}

	@Test
	@DisplayName("임시저장 → DRAFT, 알림 N (내용 없어도 허용)")
	void draft_status() {
		ArgumentCaptor<NoticeVO> cap = stubInsert(10L);
		service.create(req(null, "ALL", "IMMEDIATE", null, true), user());
		verify(noticeMapper).insertNotice(cap.capture());
		assertEquals("DRAFT", cap.getValue().getNoticeSttsCd());
		assertEquals("N", cap.getValue().getNotifySendYn());
	}

	@Test
	@DisplayName("예약발송(미래) → SCHEDULED, 알림 N, 예약일시 저장")
	void scheduled_future() {
		ArgumentCaptor<NoticeVO> cap = stubInsert(11L);
		service.create(req("내용", "ALL_PARTNER", "SCHEDULED", Instant.now().plusSeconds(3600), false), user());
		verify(noticeMapper).insertNotice(cap.capture());
		assertEquals("SCHEDULED", cap.getValue().getNoticeSttsCd());
		assertEquals("N", cap.getValue().getNotifySendYn());
		assertNotNull(cap.getValue().getScheduledAt());
	}

	@Test
	@DisplayName("즉시발행 → PUBLISHED_ONGOING, 알림 Y(스텁), 발행일 기록")
	void immediate_publish() {
		ArgumentCaptor<NoticeVO> cap = stubInsert(12L);
		service.create(req("내용", "ALL", "IMMEDIATE", null, false), user());
		verify(noticeMapper).insertNotice(cap.capture());
		assertEquals("PUBLISHED_ONGOING", cap.getValue().getNoticeSttsCd());
		assertEquals("Y", cap.getValue().getNotifySendYn());
		assertNotNull(cap.getValue().getPublishedAt());
	}

	@Test
	@DisplayName("종료 — 없으면 404")
	void close_notFound() {
		when(noticeMapper.selectNoticeById(404L)).thenReturn(null);
		assertEquals(NoticeErrorCode.NOTICE_NOT_FOUND,
				assertThrows(() -> service.close(404L, user())).getErrorCode());
	}

	@Test
	@DisplayName("종료 — 게시중 아니면 409(NOTICE_NOT_PUBLISHED), close 미호출")
	void close_notPublished() {
		NoticeVO vo = new NoticeVO();
		vo.setNoticeSn(5L);
		vo.setNoticeSttsCd("DRAFT");
		when(noticeMapper.selectNoticeById(5L)).thenReturn(vo);
		assertEquals(NoticeErrorCode.NOTICE_NOT_PUBLISHED,
				assertThrows(() -> service.close(5L, user())).getErrorCode());
		verify(noticeMapper, org.mockito.Mockito.never()).closeNotice(anyLong(), anyLong());
	}

	@Test
	@DisplayName("종료 성공 — 게시중 → closeNotice 호출")
	void close_success() {
		stubSaved(6L); // PUBLISHED_ONGOING
		when(noticeMapper.closeNotice(6L, 9L)).thenReturn(1);
		service.close(6L, user());
		verify(noticeMapper).closeNotice(6L, 9L);
	}

	@Test
	@DisplayName("삭제 성공/없음")
	void delete_paths() {
		when(noticeMapper.softDeleteNotice(7L, 9L)).thenReturn(1);
		service.delete(7L, user());
		verify(noticeMapper).softDeleteNotice(7L, 9L);
		when(noticeMapper.softDeleteNotice(404L, 9L)).thenReturn(0);
		assertEquals(NoticeErrorCode.NOTICE_NOT_FOUND,
				assertThrows(() -> service.delete(404L, user())).getErrorCode());
	}

	@Test
	@DisplayName("cron 승격 위임 — promoteScheduled 반환")
	void promote_delegates() {
		when(noticeMapper.promoteScheduled()).thenReturn(3);
		assertEquals(3, service.promoteScheduled());
	}

	// ------------------------------------------------------------------

	private static BusinessException assertThrows(org.junit.jupiter.api.function.Executable exec) {
		return org.junit.jupiter.api.Assertions.assertThrows(BusinessException.class, exec);
	}
}
