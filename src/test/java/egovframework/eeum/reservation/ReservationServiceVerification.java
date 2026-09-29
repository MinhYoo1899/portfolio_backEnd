package egovframework.eeum.reservation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;

import egovframework.eeum.auth.vo.AuthUser;
import egovframework.eeum.base.exception.BusinessException;
import egovframework.eeum.reservation.exception.ReservationErrorCode;
import egovframework.eeum.reservation.service.hold.ReservationHoldException;
import egovframework.eeum.reservation.service.hold.ReservationHoldStore;
import egovframework.eeum.reservation.service.hold.ReservationHoldStore.HoldResult;
import egovframework.eeum.reservation.service.payment.PaymentGateway;
import egovframework.eeum.reservation.service.reservation.dao.ReservationMapper;
import egovframework.eeum.reservation.service.reservation.impl.ReservationServiceImpl;
import egovframework.eeum.reservation.vo.ReservationAgreeVO;
import egovframework.eeum.reservation.vo.ReservationCalendarNightVO;
import egovframework.eeum.reservation.vo.ReservationGuestVO;
import egovframework.eeum.reservation.vo.ReservationItemVO;
import egovframework.eeum.reservation.vo.ReservationRoomOwnershipVO;
import egovframework.eeum.reservation.vo.ReservationVO;
import egovframework.eeum.reservation.web.dto.AgreementRequest;
import egovframework.eeum.reservation.web.dto.GuestRequest;
import egovframework.eeum.reservation.web.dto.ReservationCreateRequest;
import egovframework.eeum.reservation.web.dto.ReservationResponse;

/**
 * 예약 서비스 검증(DB 없이 mock) — 생성 흐름의 소유권/재고/홀드/멱등/채번 계약.
 *
 * <p>증적: (T1) 정상 HOLD 생성, (T2) 마감/누락 밤, (T3) 매진, (T4) fail-open, (T5) 기간오류,
 *   (T6) 홀드 획득 후 insert 실패 → 홀드 해제(고스트홀드 방지), (T7) 멱등 sha256 중복,
 *   (T8) ORDER_NO 재시도 vs IDEMPOTENCY 중복 구분 + 게스트 전달/약관버전 서버해석/조회 소유권.
 */
class ReservationServiceVerification {

	private static final long USER = 7L;
	private static final long ROOM = 11L;
	private static final long HOUSE = 5L;
	private static final long PRTNR = 100L;
	private static final int QTY = 1;

	/** 미래 숙박일(오늘+10 ~ +12, 2박) — 어떤 TZ 에서도 오늘(KST) 이후. */
	private static final LocalDate CHECK_IN = LocalDate.now().plusDays(10);
	private static final LocalDate CHECK_OUT = CHECK_IN.plusDays(2);

	private final ReservationMapper reservationMapper = mock(ReservationMapper.class);
	private final ReservationHoldStore holdStore = mock(ReservationHoldStore.class);
	private final PaymentGateway paymentGateway = mock(PaymentGateway.class);
	private final ReservationServiceImpl service =
			new ReservationServiceImpl(reservationMapper, holdStore, paymentGateway,
					mock(egovframework.eeum.notification.service.notification.NotificationEmitter.class));

	// ---- fixtures ----

	private static AuthUser member() {
		return new AuthUser(USER, "m@eeum.kr", "김회원", "MEMBER", "ACTIVE", List.of("CHNL_LOGIN_USER"));
	}

	private static ReservationRoomOwnershipVO ownership() {
		return ReservationRoomOwnershipVO.builder()
				.roomSn(ROOM).houseSn(HOUSE).prtnrSn(PRTNR).roomNm("안채")
				.roomSttsCd("ACTIVE").houseSttsCd("ACTIVE").build();
	}

	private static ReservationCalendarNightVO night(LocalDate d, int total, int sold, int price, String closed) {
		return ReservationCalendarNightVO.builder()
				.stayDate(d).totalQty(total).soldQty(sold).priceAmt(price).closedYn(closed).build();
	}

	/** 2박 전부 판매가능(마감 N) — 단가 150000 + 120000 = 270000. */
	private static List<ReservationCalendarNightVO> twoOpenNights() {
		return List.of(night(CHECK_IN, 3, 0, 150000, "N"), night(CHECK_IN.plusDays(1), 3, 0, 120000, "N"));
	}

	private static GuestRequest reserverGuest() {
		return new GuestRequest("RESERVER", "홍길동", "01012345678");
	}

	private static ReservationCreateRequest createReq() {
		return new ReservationCreateRequest(ROOM, CHECK_IN, CHECK_OUT, List.of(reserverGuest()), null);
	}

	// ---- stub helpers ----

	private void stubHoldAcquired() {
		when(holdStore.windowSeconds()).thenReturn(600L);
		when(holdStore.acquire(anyString(), eq(ROOM), anyList(), eq(QTY), anyLong()))
				.thenReturn(new HoldResult(true, null));
	}

	/** 헤더 insert 성공(reservationSn 채번). */
	private void stubHeaderInsertOk() {
		doAnswer(inv -> {
			inv.getArgument(0, ReservationVO.class).setReservationSn(500L);
			return null;
		}).when(reservationMapper).insertReservation(any(ReservationVO.class));
	}

	/** 항목 insert 성공(reservationItemSn 채번). */
	private void stubItemInsertOk() {
		doAnswer(inv -> {
			inv.getArgument(0, ReservationItemVO.class).setReservationItemSn(900L);
			return null;
		}).when(reservationMapper).insertItem(any(ReservationItemVO.class));
	}

	private void stubOwnershipAndCalendar(List<ReservationCalendarNightVO> nights) {
		when(reservationMapper.selectRoomOwnership(ROOM)).thenReturn(ownership());
		when(reservationMapper.selectCalendarNights(eq(ROOM), any(), any())).thenReturn(nights);
	}

	// ======================= T1 정상 =======================

	@Test
	@DisplayName("T1 정상 생성 — HOLD 예약 + 항목/일자/게스트 insert, HOLD_ID 설정, SOLD_QTY 미변경(해제 없음)")
	void t1_createsHoldReservation() {
		stubOwnershipAndCalendar(twoOpenNights());
		stubHoldAcquired();
		stubHeaderInsertOk();
		stubItemInsertOk();
		ArgumentCaptor<ReservationVO> cap = ArgumentCaptor.forClass(ReservationVO.class);

		ReservationResponse res = service.create(createReq(), null, member());

		verify(reservationMapper).insertReservation(cap.capture());
		ReservationVO header = cap.getValue();
		assertEquals("HOLD", header.getReservationSttsCd());
		assertNotNull(header.getHoldId(), "홀드 획득 성공 → HOLD_ID 설정");
		assertEquals(USER, header.getReserverUserSn(), "예약자=토큰 USER_SN");
		assertEquals(PRTNR, header.getPrtnrSn(), "판매자=서버 해석");
		assertEquals(HOUSE, header.getHouseSn());
		assertEquals(270000, header.getTotalAmt(), "밤단가 합계 스냅샷");
		assertEquals(2, header.getNgtCnt());
		assertEquals(1, header.getGuestCnt());
		assertNull(header.getIdempotencyKey(), "멱등 헤더 없음 → null");
		verify(reservationMapper).insertItem(any());
		verify(reservationMapper, times(2)).insertItemNight(any());
		verify(reservationMapper).insertGuest(any());
		verify(holdStore, never()).release(anyString(), anyLong(), anyList(), anyInt()); // 성공 → 해제 없음(SOLD 미변경)
		assertEquals(500L, res.reservationSn());
		assertEquals("HOLD", res.reservationSttsCd());
		assertNotNull(res.holdExpiry(), "홀드 만료 노출(Instant)");
	}

	// ======================= T2 마감/누락 밤 =======================

	@Test
	@DisplayName("T2 마감 밤 포함 → ROOM_UNAVAILABLE(홀드/insert 진입 안 함)")
	void t2_closedNightUnavailable() {
		when(reservationMapper.selectRoomOwnership(ROOM)).thenReturn(ownership());
		when(reservationMapper.selectCalendarNights(eq(ROOM), any(), any()))
				.thenReturn(List.of(night(CHECK_IN, 3, 0, 150000, "N"), night(CHECK_IN.plusDays(1), 3, 0, 120000, "Y")));

		BusinessException e = assertThrows(BusinessException.class, () -> service.create(createReq(), null, member()));
		assertEquals(ReservationErrorCode.RESERVATION_ROOM_UNAVAILABLE, e.getErrorCode());
		verify(holdStore, never()).acquire(anyString(), anyLong(), anyList(), anyInt(), anyLong());
		verify(reservationMapper, never()).insertReservation(any());
	}

	@Test
	@DisplayName("T2 무가격 밤(PRICE_AMT<=0) 포함 → ROOM_UNAVAILABLE(공개검색·발행게이트와 판매가능 기준 정렬, 0원 스냅샷 차단)")
	void t2_zeroPriceNightUnavailable() {
		when(reservationMapper.selectRoomOwnership(ROOM)).thenReturn(ownership());
		when(reservationMapper.selectCalendarNights(eq(ROOM), any(), any()))
				.thenReturn(List.of(night(CHECK_IN, 3, 0, 150000, "N"), night(CHECK_IN.plusDays(1), 3, 0, 0, "N")));

		BusinessException e = assertThrows(BusinessException.class, () -> service.create(createReq(), null, member()));
		assertEquals(ReservationErrorCode.RESERVATION_ROOM_UNAVAILABLE, e.getErrorCode());
		verify(holdStore, never()).acquire(anyString(), anyLong(), anyList(), anyInt(), anyLong());
		verify(reservationMapper, never()).insertReservation(any());
	}

	@Test
	@DisplayName("T2 누락 밤(캘린더 행 부족) → ROOM_UNAVAILABLE")
	void t2_missingNightUnavailable() {
		when(reservationMapper.selectRoomOwnership(ROOM)).thenReturn(ownership());
		when(reservationMapper.selectCalendarNights(eq(ROOM), any(), any()))
				.thenReturn(List.of(night(CHECK_IN, 3, 0, 150000, "N"))); // 2박 필요한데 1박만 존재

		BusinessException e = assertThrows(BusinessException.class, () -> service.create(createReq(), null, member()));
		assertEquals(ReservationErrorCode.RESERVATION_ROOM_UNAVAILABLE, e.getErrorCode());
		verify(reservationMapper, never()).insertReservation(any());
	}

	@Test
	@DisplayName("T2 소유권 없음/비활성 객실 → ROOM_UNAVAILABLE")
	void t2_inactiveRoomUnavailable() {
		when(reservationMapper.selectRoomOwnership(ROOM)).thenReturn(ReservationRoomOwnershipVO.builder()
				.roomSn(ROOM).houseSn(HOUSE).prtnrSn(PRTNR).roomNm("안채")
				.roomSttsCd("HIDDEN").houseSttsCd("ACTIVE").build());

		BusinessException e = assertThrows(BusinessException.class, () -> service.create(createReq(), null, member()));
		assertEquals(ReservationErrorCode.RESERVATION_ROOM_UNAVAILABLE, e.getErrorCode());
		verify(reservationMapper, never()).insertReservation(any());
	}

	// ======================= T3 매진 =======================

	@Test
	@DisplayName("T3 매진(HoldResult.acquired=false) → SOLD_OUT(해제 없음 — 획득 실패는 멤버 미생성)")
	void t3_soldOut() {
		stubOwnershipAndCalendar(twoOpenNights());
		when(holdStore.windowSeconds()).thenReturn(600L);
		when(holdStore.acquire(anyString(), eq(ROOM), anyList(), eq(QTY), anyLong()))
				.thenReturn(new HoldResult(false, CHECK_IN));

		BusinessException e = assertThrows(BusinessException.class, () -> service.create(createReq(), null, member()));
		assertEquals(ReservationErrorCode.RESERVATION_SOLD_OUT, e.getErrorCode());
		verify(reservationMapper, never()).insertReservation(any());
		verify(holdStore, never()).release(anyString(), anyLong(), anyList(), anyInt());
	}

	// ======================= T4 fail-open =======================

	@Test
	@DisplayName("T4 fail-open — acquire 가 ReservationHoldException 이면 홀드 없이 생성(HOLD_ID null), 예외 미전파")
	void t4_failOpenCreatesWithoutHold() {
		stubOwnershipAndCalendar(twoOpenNights());
		when(holdStore.windowSeconds()).thenReturn(600L);
		when(holdStore.acquire(anyString(), eq(ROOM), anyList(), eq(QTY), anyLong()))
				.thenThrow(new ReservationHoldException("redis down", new RuntimeException("timeout")));
		stubHeaderInsertOk();
		stubItemInsertOk();
		ArgumentCaptor<ReservationVO> cap = ArgumentCaptor.forClass(ReservationVO.class);

		ReservationResponse res = service.create(createReq(), null, member()); // 예외 없이 진행

		verify(reservationMapper).insertReservation(cap.capture());
		assertNull(cap.getValue().getHoldId(), "fail-open → HOLD_ID null(홀드 미획득)");
		assertNotNull(cap.getValue().getHoldExpryDt(), "만료는 양 경로 공통 설정");
		assertEquals(500L, res.reservationSn());
		verify(holdStore, never()).release(anyString(), anyLong(), anyList(), anyInt());
	}

	// ======================= T5 기간 오류 =======================

	@Test
	@DisplayName("T5 체크아웃<=체크인 → INVALID_RANGE(소유권 조회 진입 안 함)")
	void t5_checkoutNotAfterCheckin() {
		ReservationCreateRequest req = new ReservationCreateRequest(ROOM, CHECK_IN, CHECK_IN,
				List.of(reserverGuest()), null);
		BusinessException e = assertThrows(BusinessException.class, () -> service.create(req, null, member()));
		assertEquals(ReservationErrorCode.RESERVATION_INVALID_RANGE, e.getErrorCode());
		verify(reservationMapper, never()).selectRoomOwnership(anyLong());
	}

	@Test
	@DisplayName("T5 과거 체크인 → INVALID_RANGE")
	void t5_pastCheckin() {
		LocalDate past = LocalDate.now().minusDays(1);
		ReservationCreateRequest req = new ReservationCreateRequest(ROOM, past, past.plusDays(2),
				List.of(reserverGuest()), null);
		BusinessException e = assertThrows(BusinessException.class, () -> service.create(req, null, member()));
		assertEquals(ReservationErrorCode.RESERVATION_INVALID_RANGE, e.getErrorCode());
		verify(reservationMapper, never()).selectRoomOwnership(anyLong());
	}

	@Test
	@DisplayName("게스트 검증 — 예약자(RESERVER) 0명이면 GUEST_INVALID")
	void guestNoReserverInvalid() {
		ReservationCreateRequest req = new ReservationCreateRequest(ROOM, CHECK_IN, CHECK_OUT,
				List.of(new GuestRequest("OCCUPANT", "김투숙", null)), null);
		BusinessException e = assertThrows(BusinessException.class, () -> service.create(req, null, member()));
		assertEquals(ReservationErrorCode.RESERVATION_GUEST_INVALID, e.getErrorCode());
		verify(reservationMapper, never()).selectRoomOwnership(anyLong());
	}

	// ======================= T6 홀드 획득 후 DB 실패 → 해제 =======================

	@Test
	@DisplayName("T6 홀드 획득 후 자식 insert 실패 → holdStore.release 호출 + 원 예외 전파(고스트홀드 방지)")
	void t6_releasesHoldWhenInsertFails() {
		stubOwnershipAndCalendar(twoOpenNights());
		stubHoldAcquired();
		stubHeaderInsertOk(); // 헤더는 성공
		RuntimeException boom = new RuntimeException("db down");
		doThrow(boom).when(reservationMapper).insertItem(any()); // 자식에서 실패

		RuntimeException thrown = assertThrows(RuntimeException.class, () -> service.create(createReq(), null, member()));
		assertSame(boom, thrown, "원 예외 그대로 전파(홀드 해제가 마스킹하지 않음)");

		ArgumentCaptor<String> acquireHoldId = ArgumentCaptor.forClass(String.class);
		verify(holdStore).acquire(acquireHoldId.capture(), eq(ROOM), anyList(), eq(QTY), anyLong());
		verify(holdStore).release(eq(acquireHoldId.getValue()), eq(ROOM), anyList(), eq(QTY)); // 동일 holdId 해제
	}

	// ======================= T7 멱등 sha256 중복 =======================

	@Test
	@DisplayName("T7 멱등 sha256 중복 — 사전점검이 sha256hex(header)로 조회, 기존 발견 시 DUPLICATE(홀드 전 중단), 원시헤더 미사용")
	void t7_idempotencyShaDup() {
		String rawHeader = "client-key-abc-123";
		when(reservationMapper.selectRoomOwnership(ROOM)).thenReturn(ownership());
		when(reservationMapper.selectExistingByIdemHash(eq(USER), anyString())).thenReturn(999L);

		BusinessException e = assertThrows(BusinessException.class,
				() -> service.create(createReq(), rawHeader, member()));
		assertEquals(ReservationErrorCode.RESERVATION_DUPLICATE, e.getErrorCode());

		ArgumentCaptor<String> hashCap = ArgumentCaptor.forClass(String.class);
		verify(reservationMapper).selectExistingByIdemHash(eq(USER), hashCap.capture());
		assertEquals(ReservationServiceImpl.sha256Hex(rawHeader), hashCap.getValue(), "조회 키 = sha256hex(header)");
		assertNotEquals(rawHeader, hashCap.getValue(), "원시 Idempotency-Key 는 저장/조회에 사용하지 않음");

		verify(holdStore, never()).acquire(anyString(), anyLong(), anyList(), anyInt(), anyLong());
		verify(reservationMapper, never()).insertReservation(any());
	}

	// ======================= T8 ORDER_NO 재시도 vs IDEMPOTENCY 구분 =======================

	@Test
	@DisplayName("T8 ORDER_NO UNIQUE 충돌 → 새 ORDER_NO 로 재시도 후 성공(insertReservation 2회, 서로 다른 ORDER_NO)")
	void t8_orderNoRetryThenSucceeds() {
		stubOwnershipAndCalendar(twoOpenNights());
		stubHoldAcquired();
		stubItemInsertOk();
		List<String> orderNos = new ArrayList<>();
		doAnswer(inv -> {
			ReservationVO vo = inv.getArgument(0, ReservationVO.class);
			orderNos.add(vo.getOrderNo());
			if (orderNos.size() == 1) {
				throw new DuplicateKeyException("Duplicate entry 'R1' for key 'UK_RESERVATION_ORDER_NO'");
			}
			vo.setReservationSn(500L);
			return null;
		}).when(reservationMapper).insertReservation(any(ReservationVO.class));

		ReservationResponse res = service.create(createReq(), null, member());

		assertEquals(500L, res.reservationSn(), "재시도 후 생성 성공");
		assertEquals(2, orderNos.size(), "ORDER_NO 충돌로 1회 재시도");
		assertNotEquals(orderNos.get(0), orderNos.get(1), "재시도 시 ORDER_NO 재채번");
		verify(holdStore, never()).release(anyString(), anyLong(), anyList(), anyInt()); // 최종 성공 → 해제 없음
	}

	@Test
	@DisplayName("T8 IDEMPOTENCY UNIQUE 충돌 → DUPLICATE(재시도 금지 — insertReservation 1회)")
	void t8_idempotencyDupNotRetried() {
		stubOwnershipAndCalendar(twoOpenNights());
		stubHoldAcquired();
		// 사전점검은 빈손(동시요청이 그 사이에 INSERT 성공한 레이스) → insert 에서 IDEMPOTENCY UNIQUE 로 잡힘
		when(reservationMapper.selectExistingByIdemHash(anyLong(), anyString())).thenReturn(null);
		doThrow(new DuplicateKeyException("Duplicate entry '7-x' for key 'UK_RESERVATION_IDEMPOTENCY'"))
				.when(reservationMapper).insertReservation(any());

		BusinessException e = assertThrows(BusinessException.class,
				() -> service.create(createReq(), "hdr", member()));
		assertEquals(ReservationErrorCode.RESERVATION_DUPLICATE, e.getErrorCode());
		verify(reservationMapper, times(1)).insertReservation(any()); // 재시도 안 함
	}

	// ======================= 게스트/동의/조회 =======================

	@Test
	@DisplayName("게스트 전달 — 유형별로 insertGuest(전화는 매퍼 TypeHandler 가 암호화), guestCnt 는 게스트 수에서 파생")
	void guestInsertPassesThrough() {
		stubOwnershipAndCalendar(twoOpenNights());
		stubHoldAcquired();
		stubHeaderInsertOk();
		stubItemInsertOk();
		ReservationCreateRequest req = new ReservationCreateRequest(ROOM, CHECK_IN, CHECK_OUT,
				List.of(new GuestRequest("RESERVER", "홍길동", "01011112222"),
						new GuestRequest("OCCUPANT", "김투숙", null)),
				null);

		ReservationResponse res = service.create(req, null, member());

		ArgumentCaptor<ReservationGuestVO> cap = ArgumentCaptor.forClass(ReservationGuestVO.class);
		verify(reservationMapper, times(2)).insertGuest(cap.capture());
		ReservationGuestVO reserver = cap.getAllValues().get(0);
		assertEquals("RESERVER", reserver.getGuestTypeCd());
		assertEquals("홍길동", reserver.getGuestNm());
		assertEquals("01011112222", reserver.getGuestMblTelno(), "서비스는 평문 전달(암호화는 TypeHandler 책임)");
		assertEquals(2, res.guestCnt(), "guestCnt = 게스트 수 파생");
	}

	@Test
	@DisplayName("동의 스냅샷 — TRMS_VER_NO 는 서버가 TRMS_MST 에서 해석(클라이언트 값 미신뢰)")
	void agreementServerResolvesVersion() {
		stubOwnershipAndCalendar(twoOpenNights());
		stubHoldAcquired();
		stubHeaderInsertOk();
		stubItemInsertOk();
		when(reservationMapper.selectTermsVersion(7L)).thenReturn("1.2");
		ReservationCreateRequest req = new ReservationCreateRequest(ROOM, CHECK_IN, CHECK_OUT,
				List.of(reserverGuest()),
				List.of(new AgreementRequest(7L, "RESERVATION_FULFILLMENT", "이름,연락처")));

		service.create(req, null, member());

		ArgumentCaptor<ReservationAgreeVO> cap = ArgumentCaptor.forClass(ReservationAgreeVO.class);
		verify(reservationMapper).insertAgre(cap.capture());
		assertEquals("1.2", cap.getValue().getTrmsVerNo(), "약관버전은 서버 해석값");
		assertEquals("RESERVATION_FULFILLMENT", cap.getValue().getAgrePrpsCd());
	}

	@Test
	@DisplayName("상세 조회 — 본인 예약이면 반환")
	void detailOwned() {
		when(reservationMapper.selectReservationOwned(500L, USER)).thenReturn(ReservationVO.builder()
				.reservationSn(500L).orderNo("R1").reservationSttsCd("HOLD")
				.checkInDate(CHECK_IN).checkOutDate(CHECK_OUT).ngtCnt(2).guestCnt(1).totalAmt(270000).crncCd("KRW")
				.build());
		ReservationResponse res = service.detail(500L, member());
		assertEquals(500L, res.reservationSn());
		assertEquals("R1", res.orderNo());
	}

	@Test
	@DisplayName("상세 조회 — 타 소유/없음이면 404(소유권 WHERE 로 필터)")
	void detailForeignNotFound() {
		when(reservationMapper.selectReservationOwned(500L, USER)).thenReturn(null);
		BusinessException e = assertThrows(BusinessException.class, () -> service.detail(500L, member()));
		assertEquals(ReservationErrorCode.RESERVATION_NOT_FOUND, e.getErrorCode());
	}
}
