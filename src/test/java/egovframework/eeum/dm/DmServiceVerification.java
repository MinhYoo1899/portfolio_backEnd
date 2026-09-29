package egovframework.eeum.dm;

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
import egovframework.eeum.dm.exception.DmErrorCode;
import egovframework.eeum.dm.service.dm.dao.DmMapper;
import egovframework.eeum.dm.service.dm.impl.DmServiceImpl;
import egovframework.eeum.dm.vo.DmMessageVO;
import egovframework.eeum.dm.web.dto.DmCreateRequest;

/**
 * DM 서비스 검증 — 채널/대상/내용/예약일시/이미지 validation + 상태 산정(DRAFT/SCHEDULED/SENT) + 404.
 */
class DmServiceVerification {

	private final DmMapper dmMapper = mock(DmMapper.class);
	private final DmServiceImpl service = new DmServiceImpl(dmMapper,
			mock(egovframework.eeum.notification.service.notification.NotificationEmitter.class));

	private static final String VALID_IMG = "/files/download?atchFileSn=7";

	private static AuthUser user() {
		return new AuthUser(9L, "op@eeum.dev", "운영자", "OPERATOR", "ACTIVE", List.of("DM_MNG"));
	}

	private DmCreateRequest req(String content, String channel, String img, String target, String timing,
			Instant scheduledAt, Boolean draft) {
		return new DmCreateRequest("여름 프로모션", content, channel, img, target, 100, timing, scheduledAt, draft);
	}

	private ArgumentCaptor<DmMessageVO> stubInsert(long sn) {
		ArgumentCaptor<DmMessageVO> cap = ArgumentCaptor.forClass(DmMessageVO.class);
		doAnswer(inv -> {
			inv.getArgument(0, DmMessageVO.class).setDmMessageSn(sn);
			return 1;
		}).when(dmMapper).insertDm(any());
		DmMessageVO saved = new DmMessageVO();
		saved.setDmMessageSn(sn);
		saved.setTitle("여름 프로모션");
		saved.setDmSttsCd("SENT");
		when(dmMapper.selectDmById(sn)).thenReturn(saved);
		return cap;
	}

	@Test
	@DisplayName("없는 채널 → DM_CHANNEL_INVALID")
	void channel_invalid() {
		BusinessException e = assertThrows(() -> service.create(
				req("내용", "EMAIL", null, "ALL", "NOW", null, false), user()));
		assertEquals(DmErrorCode.DM_CHANNEL_INVALID, e.getErrorCode());
	}

	@Test
	@DisplayName("없는 대상 → DM_TARGET_INVALID")
	void target_invalid() {
		BusinessException e = assertThrows(() -> service.create(
				req("내용", "SMS", null, "GROUP", "NOW", null, false), user()));
		assertEquals(DmErrorCode.DM_TARGET_INVALID, e.getErrorCode());
	}

	@Test
	@DisplayName("즉시발송 내용 없음 → DM_CONTENT_REQUIRED")
	void now_noContent() {
		BusinessException e = assertThrows(() -> service.create(
				req(null, "ALIMTALK", null, "ALL", "NOW", null, false), user()));
		assertEquals(DmErrorCode.DM_CONTENT_REQUIRED, e.getErrorCode());
	}

	@Test
	@DisplayName("예약발송 과거시각 → DM_SCHEDULED_AT_INVALID")
	void scheduled_past() {
		BusinessException e = assertThrows(() -> service.create(
				req("내용", "SMS", null, "ALL", "SCHEDULED", Instant.now().minusSeconds(3600), false), user()));
		assertEquals(DmErrorCode.DM_SCHEDULED_AT_INVALID, e.getErrorCode());
	}

	@Test
	@DisplayName("외부 임의 이미지 → DM_IMAGE_INVALID")
	void image_external() {
		BusinessException e = assertThrows(() -> service.create(
				req("내용", "SMS", "https://evil.example.com/x.jpg", "ALL", "NOW", null, false), user()));
		assertEquals(DmErrorCode.DM_IMAGE_INVALID, e.getErrorCode());
	}

	@Test
	@DisplayName("임시저장 → DRAFT (내용 없어도 허용)")
	void draft_status() {
		ArgumentCaptor<DmMessageVO> cap = stubInsert(10L);
		service.create(req(null, "ALIMTALK", null, "ALL", "NOW", null, true), user());
		verify(dmMapper).insertDm(cap.capture());
		assertEquals("DRAFT", cap.getValue().getDmSttsCd());
	}

	@Test
	@DisplayName("예약발송(미래) → SCHEDULED, 예약일시 저장")
	void scheduled_future() {
		ArgumentCaptor<DmMessageVO> cap = stubInsert(11L);
		service.create(req("내용", "SMS", VALID_IMG, "SELECTED", "SCHEDULED", Instant.now().plusSeconds(3600), false),
				user());
		verify(dmMapper).insertDm(cap.capture());
		assertEquals("SCHEDULED", cap.getValue().getDmSttsCd());
		assertNotNull(cap.getValue().getScheduledAt());
	}

	@Test
	@DisplayName("즉시발송 → SENT, 발송일 기록(스텁)")
	void now_sent() {
		ArgumentCaptor<DmMessageVO> cap = stubInsert(12L);
		service.create(req("내용", "ALIMTALK", VALID_IMG, "ALL", "NOW", null, false), user());
		verify(dmMapper).insertDm(cap.capture());
		assertEquals("SENT", cap.getValue().getDmSttsCd());
		assertNotNull(cap.getValue().getSentAt());
	}

	@Test
	@DisplayName("없는 DM 조회/삭제 → DM_NOT_FOUND")
	void notFound_paths() {
		when(dmMapper.selectDmById(404L)).thenReturn(null);
		assertEquals(DmErrorCode.DM_NOT_FOUND, assertThrows(() -> service.get(404L)).getErrorCode());
		when(dmMapper.softDeleteDm(anyLong(), anyLong())).thenReturn(0);
		assertEquals(DmErrorCode.DM_NOT_FOUND, assertThrows(() -> service.delete(404L, user())).getErrorCode());
	}

	@Test
	@DisplayName("삭제 성공 + cron 승격 위임")
	void delete_and_promote() {
		when(dmMapper.softDeleteDm(5L, 9L)).thenReturn(1);
		service.delete(5L, user());
		verify(dmMapper).softDeleteDm(5L, 9L);
		when(dmMapper.promoteScheduled()).thenReturn(2);
		assertEquals(2, service.promoteScheduled());
	}

	// ------------------------------------------------------------------

	private static BusinessException assertThrows(org.junit.jupiter.api.function.Executable exec) {
		return org.junit.jupiter.api.Assertions.assertThrows(BusinessException.class, exec);
	}
}
