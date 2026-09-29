package egovframework.eeum.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import egovframework.eeum.notification.service.notification.NotificationType;
import egovframework.eeum.notification.service.notification.dao.OperatorNotificationMapper;
import egovframework.eeum.notification.service.notification.impl.NotificationEmitterImpl;
import egovframework.eeum.notification.vo.NotificationTargetVO;
import egovframework.eeum.notification.vo.OperatorNotificationVO;

/**
 * 알림 적재 포트 검증 — 권한 보유자만 수신(ALWAYS도 무권한 미수신), 설정 게이팅, 대상유형/딥링크=유형 소유, 입력검증(DB 전 차단).
 */
class NotificationEmitterVerification {

	private static final long ACTOR = 100L;

	private final OperatorNotificationMapper mapper = mock(OperatorNotificationMapper.class);
	private final NotificationEmitterImpl emitter = new NotificationEmitterImpl(mapper);

	private static NotificationTargetVO target(long sn, String r, String i, String s, String m) {
		NotificationTargetVO t = new NotificationTargetVO();
		t.setUserSn(sn);
		t.setRsvtNotiYn(r);
		t.setInqrNotiYn(i);
		t.setStlmNotiYn(s);
		t.setMktgNotiYn(m);
		return t;
	}

	@Test
	@DisplayName("ALWAYS 유형 — 해당 권한 보유 활성 운영자에 팬아웃, 대상유형/딥링크/actor=유형·서버 소유")
	void alwaysTypeFansOutToAuthorityHolders() {
		when(mapper.selectActiveOperatorTargets("PRTNR_APRV")).thenReturn(List.of(
				target(1L, "N", "N", "N", "N"),      // 토글 전부 off 여도 ALWAYS 는 적재
				target(2L, null, null, null, null))); // 설정 없어도

		emitter.emit(ACTOR, "PARTNER_AUTH:77", NotificationType.PARTNER_AUTH, "인증요청", "본문", "77");

		verify(mapper).selectActiveOperatorTargets("PRTNR_APRV"); // 권한 필터 사용
		ArgumentCaptor<OperatorNotificationVO> cap = ArgumentCaptor.forClass(OperatorNotificationVO.class);
		verify(mapper, times(2)).insertIfAbsent(cap.capture());
		OperatorNotificationVO v = cap.getAllValues().get(0);
		assertEquals("PARTNER_AUTH", v.getNotiTypeCd());
		assertEquals("PARTNER_AUTH:77", v.getEventKey());
		assertEquals(ACTOR, v.getRgtrSn());
		assertEquals("PARTNER", v.getTrgtTypeCd());        // 유형이 소유(호출자 지정 아님)
		assertEquals("/partner/77", v.getLinkUrl());       // OPS 딥링크
	}

	@Test
	@DisplayName("ALWAYS 유형도 무권한자는 미수신 — 권한 보유자 0명이면 적재 없음")
	void alwaysTypeStillRequiresAuthority() {
		when(mapper.selectActiveOperatorTargets("HOUSE_REVIEW")).thenReturn(List.of()); // 권한 보유자 없음

		emitter.emit(ACTOR, "PRODUCT_REVIEW:9", NotificationType.PRODUCT_REVIEW, "검수요청", null, "9");

		verify(mapper, never()).insertIfAbsent(any());
	}

	@Test
	@DisplayName("게이팅 유형(RESERVATION) — 권한 보유자 중 설정 on 인 운영자에게만 적재")
	void gatedTypeRespectsSetting() {
		when(mapper.selectActiveOperatorTargets("RESERVATION_VIEW")).thenReturn(List.of(
				target(1L, "Y", "N", "N", "N"),   // 예약 on
				target(2L, "N", "Y", "Y", "Y"))); // 예약 off

		emitter.emit(ACTOR, "RESERVATION:9", NotificationType.RESERVATION, "새 예약", null, "9");

		verify(mapper, times(1)).insertIfAbsent(any());
	}

	@Test
	@DisplayName("대상유형/딥링크는 유형이 소유 — 쿼리형 딥링크(정산)도 서버 산출")
	void targetTypeAndLinkOwnedByType() {
		when(mapper.selectActiveOperatorTargets("SETTLEMENT_VIEW")).thenReturn(List.of(target(1L, null, null, "Y", null)));

		emitter.emit(ACTOR, "SETTLEMENT:3", NotificationType.SETTLEMENT, "정산 예정", null, "3");

		ArgumentCaptor<OperatorNotificationVO> cap = ArgumentCaptor.forClass(OperatorNotificationVO.class);
		verify(mapper).insertIfAbsent(cap.capture());
		assertEquals("SETTLEMENT", cap.getValue().getTrgtTypeCd());
		assertEquals("/settlement?settlementId=3", cap.getValue().getLinkUrl());
	}

	@Test
	@DisplayName("입력 검증 — 잘못된 입력은 DB 조회 전 IllegalArgumentException, 대상조회·적재 없음")
	void invalidInputBlockedBeforeDb() {
		// eventKey 공백
		assertThrows(IllegalArgumentException.class,
				() -> emitter.emit(ACTOR, " ", NotificationType.PARTNER_AUTH, "t", null, "1"));
		// title 200자 초과
		assertThrows(IllegalArgumentException.class,
				() -> emitter.emit(ACTOR, "k", NotificationType.PARTNER_AUTH, "a".repeat(201), null, "1"));
		// targetId 누락
		assertThrows(IllegalArgumentException.class,
				() -> emitter.emit(ACTOR, "k", NotificationType.PARTNER_AUTH, "t", null, null));
		// targetId 숫자 아님
		assertThrows(IllegalArgumentException.class,
				() -> emitter.emit(ACTOR, "k", NotificationType.PARTNER_AUTH, "t", null, "abc"));

		verify(mapper, never()).selectActiveOperatorTargets(anyString());
		verify(mapper, never()).insertIfAbsent(any());
	}
}
