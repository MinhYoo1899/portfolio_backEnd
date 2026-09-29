package egovframework.eeum.reservation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import egovframework.eeum.auth.vo.AuthUser;
import egovframework.eeum.base.exception.BusinessException;
import egovframework.eeum.reservation.exception.ReservationErrorCode;
import egovframework.eeum.reservation.service.hold.ReservationHoldStore;
import egovframework.eeum.reservation.service.payment.PaymentApproval;
import egovframework.eeum.reservation.service.payment.PaymentGateway;
import egovframework.eeum.reservation.service.reservation.dao.ReservationMapper;
import egovframework.eeum.reservation.service.reservation.impl.ReservationServiceImpl;
import egovframework.eeum.reservation.vo.ReservationVO;
import egovframework.eeum.reservation.web.dto.ReservationResponse;

/**
 * 예약 확정(슬라이스③) 검증 — DB 없이 mock + 인메모리 ROOM_CALENDAR 모델(confirmNight 원자 조건부 UPDATE 재현).
 *
 * <p>증적: (a) 다박 정상 확정, (b) 부분실패 롤백(상태변경/홀드해제 없음), (c) 이중확정 멱등(결제/재고 미호출),
 *   (d) HOLD 해제 실증, (e) 동시 확정 경합(정확히 1건만 RESERVED), (f) 상태머신(불법 상태 → INVALID_STATE),
 *   + NOT_FOUND(타 소유/없음).
 *
 * <p>단위테스트는 활성 트랜잭션이 없으므로 홀드 해제는 즉시(direct) 실행되어 검증 가능하다
 *   (운영은 afterCommit — 롤백 시 홀드 유지). DB 행 잠금 직렬화는 dev 스모크 몫이고, 여기서는
 *   {@link FakeRoomCalendar} 가 조건부 UPDATE 의 원자성(잔여≥qty 일 때만 sold+=q)을 모델링한다.
 */
class ReservationConfirmVerification {

	private static final long USER = 7L;
	private static final long ROOM = 11L;
	private static final int QTY = 1;

	private static final LocalDate CHECK_IN = LocalDate.now().plusDays(10);
	private static final LocalDate CHECK_OUT = CHECK_IN.plusDays(2); // 2박

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

	/** 확정 대상 예약(2박, 지정 상태/holdId). */
	private static ReservationVO reservation(String sttsCd, String holdId) {
		return ReservationVO.builder()
				.reservationSn(500L).orderNo("R1").reservationSttsCd(sttsCd).roomSn(ROOM)
				.checkInDate(CHECK_IN).checkOutDate(CHECK_OUT).ngtCnt(2).guestCnt(1)
				.totalAmt(270000).crncCd("KRW").holdId(holdId).build();
	}

	private void stubApproved() {
		lenient().when(paymentGateway.approve(anyString(), anyInt(), anyString()))
				.thenReturn(new PaymentApproval(true, "STUB", "STUB-x"));
		// 확정(RESERVED) 상태전이 = 정확히 1행 갱신(서비스가 affected==1 을 단언 — 방어 가드).
		lenient().when(reservationMapper.updateStatusToReserved(anyLong(), anyLong())).thenReturn(1);
	}

	/**
	 * 인메모리 ROOM_CALENDAR — {@code stayDate → [total, sold]}. confirmNight 를 원자 조건부 UPDATE 로 재현:
	 * {@code synchronized} 블록에서 마감 아님 AND 잔여(total-sold)≥q 이면 sold+=q 후 1, 아니면 0 을 반환한다.
	 * 동시성 시나리오(e)에서 두 스레드가 같은 밤을 경합해도 오버부킹 0 을 보장한다.
	 */
	static final class FakeRoomCalendar {
		private final Map<LocalDate, int[]> byDate = new HashMap<>(); // [0]=total, [1]=sold
		private final java.util.Set<LocalDate> closed = new java.util.HashSet<>();

		void put(LocalDate d, int total, int sold) {
			byDate.put(d, new int[] {total, sold});
		}

		void close(LocalDate d) {
			closed.add(d);
		}

		synchronized int confirmNight(LocalDate d, int q) {
			int[] ts = byDate.get(d);
			if (ts == null || closed.contains(d) || (ts[0] - ts[1]) < q) {
				return 0;
			}
			ts[1] += q;
			return 1;
		}

		synchronized int sold(LocalDate d) {
			return byDate.get(d)[1];
		}
	}

	/** confirmNight 매퍼 호출을 인메모리 캘린더로 위임. */
	private void wireCalendar(FakeRoomCalendar cal) {
		when(reservationMapper.confirmNight(eq(ROOM), any(LocalDate.class), eq(QTY), eq(USER)))
				.thenAnswer(inv -> cal.confirmNight(inv.getArgument(1, LocalDate.class), QTY));
	}

	// ======================= (a) 다박 정상 확정 =======================

	@Test
	@DisplayName("(a) 다박 정상 확정 — 모든 밤 SOLD_QTY+=q, 상태 RESERVED, 응답 RESERVED")
	void a_confirmsMultiNight() {
		FakeRoomCalendar cal = new FakeRoomCalendar();
		cal.put(CHECK_IN, 3, 0);
		cal.put(CHECK_IN.plusDays(1), 3, 0);
		when(reservationMapper.selectReservationForUpdate(500L, USER)).thenReturn(reservation("HOLD", "hold-1"));
		stubApproved();
		wireCalendar(cal);

		ReservationResponse res = service.confirm(500L, member());

		assertEquals("RESERVED", res.reservationSttsCd(), "응답 상태 RESERVED");
		assertEquals(1, cal.sold(CHECK_IN), "1박 SOLD_QTY += q");
		assertEquals(1, cal.sold(CHECK_IN.plusDays(1)), "2박 SOLD_QTY += q");
		verify(reservationMapper).updateStatusToReserved(500L, USER);
		verify(paymentGateway).approve(eq("R1"), eq(270000), eq("KRW"));
	}

	// ======================= (b) 부분실패 롤백 =======================

	@Test
	@DisplayName("(b) 부분실패 — 한 밤 confirmNight=0 → SOLD_OUT, 상태변경/홀드해제 안 함(트랜잭션 롤백 의도)")
	void b_partialFailureRollsBack() {
		FakeRoomCalendar cal = new FakeRoomCalendar();
		cal.put(CHECK_IN, 3, 0);            // 1박 성공
		cal.put(CHECK_IN.plusDays(1), 1, 1); // 2박 만석(잔여 0) → 0 반환
		when(reservationMapper.selectReservationForUpdate(500L, USER)).thenReturn(reservation("HOLD", "hold-1"));
		stubApproved();
		wireCalendar(cal);

		BusinessException e = assertThrows(BusinessException.class, () -> service.confirm(500L, member()));
		assertEquals(ReservationErrorCode.RESERVATION_SOLD_OUT, e.getErrorCode());
		verify(reservationMapper, never()).updateStatusToReserved(anyLong(), anyLong()); // 상태변경 안 함
		verify(holdStore, never()).release(anyString(), anyLong(), anyList(), anyInt()); // 실패 경로 → 홀드 해제 없음
		// 실제 원복은 Spring @Transactional 이 담당(단위테스트는 SOLD_OUT throw + 상태 미변경으로 롤백 의도 증명)
	}

	// ======================= (c) 이중확정 멱등 =======================

	@Test
	@DisplayName("(c) 이중확정 멱등 — 이미 RESERVED → 즉시 반환, confirmNight/결제 NEVER(이중차감 방지)")
	void c_idempotentNoOp() {
		when(reservationMapper.selectReservationForUpdate(500L, USER)).thenReturn(reservation("RESERVED", "hold-1"));

		ReservationResponse res = service.confirm(500L, member());

		assertEquals("RESERVED", res.reservationSttsCd());
		verify(reservationMapper, never()).confirmNight(anyLong(), any(), anyInt(), anyLong());
		verify(paymentGateway, never()).approve(anyString(), anyInt(), anyString());
		verify(reservationMapper, never()).updateStatusToReserved(anyLong(), anyLong());
		verify(holdStore, never()).release(anyString(), anyLong(), anyList(), anyInt());
	}

	// ======================= (d) HOLD 해제 실증 =======================

	@Test
	@DisplayName("(d) 확정 성공(활성 tx 없음) → holdStore.release 호출(해당 holdId + 숙박일)")
	void d_releasesHold() {
		FakeRoomCalendar cal = new FakeRoomCalendar();
		cal.put(CHECK_IN, 3, 0);
		cal.put(CHECK_IN.plusDays(1), 3, 0);
		when(reservationMapper.selectReservationForUpdate(500L, USER)).thenReturn(reservation("PAYMENT_PENDING", "hold-9"));
		stubApproved();
		wireCalendar(cal);

		service.confirm(500L, member());

		verify(holdStore).release(eq("hold-9"), eq(ROOM), eq(List.of(CHECK_IN, CHECK_IN.plusDays(1))), eq(QTY));
	}

	// ======================= (e) 동시 확정 경합 =======================

	@Test
	@DisplayName("(e) 동시 확정 경합 — 두 예약이 용량 1인 밤을 경합 → 정확히 1건 RESERVED, 나머지 SOLD_OUT(오버부킹 0)")
	void e_concurrentConfirmSerializes() throws Exception {
		// 단일 숙박일, 용량 1 — 두 확정이 같은 밤을 노린다. 인메모리 캘린더의 synchronized 가 조건부 UPDATE 원자성 모델링.
		LocalDate day = CHECK_IN;
		FakeRoomCalendar cal = new FakeRoomCalendar();
		cal.put(day, 1, 0);

		// 각기 다른 예약(다른 예약자 스코프여도 여기선 같은 USER 로 단순화 — 관심사는 캘린더 경합)
		ReservationVO r1 = ReservationVO.builder().reservationSn(501L).orderNo("R-A").reservationSttsCd("HOLD")
				.roomSn(ROOM).checkInDate(day).checkOutDate(day.plusDays(1)).ngtCnt(1).guestCnt(1)
				.totalAmt(100000).crncCd("KRW").holdId("hold-A").build();
		ReservationVO r2 = ReservationVO.builder().reservationSn(502L).orderNo("R-B").reservationSttsCd("HOLD")
				.roomSn(ROOM).checkInDate(day).checkOutDate(day.plusDays(1)).ngtCnt(1).guestCnt(1)
				.totalAmt(100000).crncCd("KRW").holdId("hold-B").build();
		when(reservationMapper.selectReservationForUpdate(501L, USER)).thenReturn(r1);
		when(reservationMapper.selectReservationForUpdate(502L, USER)).thenReturn(r2);
		stubApproved();
		wireCalendar(cal);

		AtomicInteger reserved = new AtomicInteger();
		AtomicInteger soldOut = new AtomicInteger();
		ExecutorService pool = Executors.newFixedThreadPool(2);
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch go = new CountDownLatch(1);
		try {
			Future<?> f1 = pool.submit(() -> race(501L, ready, go, reserved, soldOut));
			Future<?> f2 = pool.submit(() -> race(502L, ready, go, reserved, soldOut));
			ready.await(5, TimeUnit.SECONDS);
			go.countDown();
			f1.get(5, TimeUnit.SECONDS);
			f2.get(5, TimeUnit.SECONDS);
		} finally {
			pool.shutdownNow();
		}

		assertEquals(1, reserved.get(), "정확히 1건만 확정");
		assertEquals(1, soldOut.get(), "나머지 1건은 매진");
		assertEquals(1, cal.sold(day), "SOLD_QTY=1 (오버부킹 0)");
	}

	private void race(long reservationSn, CountDownLatch ready, CountDownLatch go,
			AtomicInteger reserved, AtomicInteger soldOut) {
		ready.countDown();
		try {
			go.await(5, TimeUnit.SECONDS);
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			return;
		}
		try {
			service.confirm(reservationSn, member());
			reserved.incrementAndGet();
		} catch (BusinessException ex) {
			if (ReservationErrorCode.RESERVATION_SOLD_OUT == ex.getErrorCode()) {
				soldOut.incrementAndGet();
			}
		}
	}

	// ======================= (f) 상태머신 =======================

	@Test
	@DisplayName("(f) 불법 상태(CANCELLED/EXPIRED/USED) → INVALID_STATE, confirmNight/결제 NOT 호출")
	void f_illegalStateRejected() {
		for (String bad : List.of("CANCELLED", "EXPIRED", "USED")) {
			when(reservationMapper.selectReservationForUpdate(500L, USER)).thenReturn(reservation(bad, "hold-1"));

			BusinessException e = assertThrows(BusinessException.class, () -> service.confirm(500L, member()),
					"상태 " + bad + " → 확정 불가");
			assertEquals(ReservationErrorCode.RESERVATION_INVALID_STATE, e.getErrorCode(), "상태 " + bad);
		}
		verify(reservationMapper, never()).confirmNight(anyLong(), any(), anyInt(), anyLong());
		verify(paymentGateway, never()).approve(anyString(), anyInt(), anyString());
		verify(reservationMapper, never()).updateStatusToReserved(anyLong(), anyLong());
	}

	// ======================= NOT_FOUND =======================

	@Test
	@DisplayName("타 소유/없음(FOR UPDATE 결과 null) → NOT_FOUND(소유권 WHERE 로 필터, 결제/재고 미진입)")
	void notFoundWhenForeignOrMissing() {
		when(reservationMapper.selectReservationForUpdate(500L, USER)).thenReturn(null);

		BusinessException e = assertThrows(BusinessException.class, () -> service.confirm(500L, member()));
		assertEquals(ReservationErrorCode.RESERVATION_NOT_FOUND, e.getErrorCode());
		verify(paymentGateway, never()).approve(anyString(), anyInt(), anyString());
		verify(reservationMapper, never()).confirmNight(anyLong(), any(), anyInt(), anyLong());
	}

	@Test
	@DisplayName("member null → unauthorized(401)")
	void unauthorizedWhenNoMember() {
		BusinessException e = assertThrows(BusinessException.class, () -> service.confirm(500L, null));
		assertTrue(e.getErrorCode().httpStatus().is4xxClientError(), "인증 오류(4xx)");
		verify(reservationMapper, never()).selectReservationForUpdate(anyLong(), anyLong());
	}
}
