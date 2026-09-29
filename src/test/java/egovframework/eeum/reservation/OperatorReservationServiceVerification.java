package egovframework.eeum.reservation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import egovframework.eeum.base.exception.BusinessException;
import egovframework.eeum.reservation.exception.ReservationErrorCode;
import egovframework.eeum.reservation.service.operator.dao.OperatorReservationMapper;
import egovframework.eeum.reservation.service.operator.impl.OperatorReservationServiceImpl;
import egovframework.eeum.reservation.vo.OperatorReservationVO;
import egovframework.eeum.reservation.web.dto.OperatorReservationDetailResponse;
import egovframework.eeum.reservation.web.dto.OperatorReservationSearchRequest;

/**
 * 운영자 예약 콘솔 서비스 검증 — 목록 페이지네이션/필터 위임, 상세 404, 상태 라벨 매핑, unsupported 명시.
 */
class OperatorReservationServiceVerification {

	private final OperatorReservationMapper mapper = mock(OperatorReservationMapper.class);
	private final OperatorReservationServiceImpl service = new OperatorReservationServiceImpl(mapper);

	private OperatorReservationVO vo(long sn, String stts) {
		OperatorReservationVO vo = new OperatorReservationVO();
		vo.setReservationSn(sn);
		vo.setOrderNo("EUM-260501-027");
		vo.setReservationSttsCd(stts);
		vo.setHouseNm("청학재 종가");
		vo.setRoomNmSnap("별채");
		vo.setGuestNm("김민준");
		vo.setGuestCnt(2);
		vo.setTotalAmt(320000);
		return vo;
	}

	@Test
	@DisplayName("상세 — 없으면 RESERVATION_NOT_FOUND")
	void detail_notFound() {
		when(mapper.selectOperatorReservationDetail(404L)).thenReturn(null);
		BusinessException e = assertThrows(() -> service.detail(404L));
		assertEquals(ReservationErrorCode.RESERVATION_NOT_FOUND, e.getErrorCode());
	}

	@Test
	@DisplayName("상세 성공 — 상태 라벨(RESERVED→예약확정) + unsupported 전부 true")
	void detail_success() {
		when(mapper.selectOperatorReservationDetail(5L)).thenReturn(vo(5L, "RESERVED"));
		OperatorReservationDetailResponse res = service.detail(5L);
		assertEquals("RESERVED", res.statusCd());
		assertEquals("예약확정", res.statusLabel());
		assertEquals("김민준", res.guestName());
		assertTrue(res.unsupported().paymentMethod() && res.unsupported().reservationChannel()
				&& res.unsupported().refund() && res.unsupported().stayStatus());
	}

	@Test
	@DisplayName("목록 — count + 필터/페이지 위임, 상태 라벨 매핑(USED→이용완료)")
	void list_delegates() {
		when(mapper.countOperatorReservation(eq("USED"), any(), any(), isNull())).thenReturn(30L);
		when(mapper.selectOperatorReservationList(eq("USED"), any(), any(), isNull(), eq(10), eq(10)))
				.thenReturn(List.of(vo(1L, "USED")));

		var page = service.list(new OperatorReservationSearchRequest("USED",
				LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31), null, 2, 10));

		assertEquals(30L, page.total());
		assertEquals(3, page.totalPages());
		assertEquals(1, page.items().size());
		assertEquals("이용완료", page.items().get(0).statusLabel());
		assertNull(page.items().get(0).paymentStatusCd()); // 결제 없음 → null
		verify(mapper).selectOperatorReservationList(eq("USED"), any(), any(), isNull(), eq(10), eq(10)); // offset=10
	}

	// ------------------------------------------------------------------

	private static BusinessException assertThrows(org.junit.jupiter.api.function.Executable exec) {
		return org.junit.jupiter.api.Assertions.assertThrows(BusinessException.class, exec);
	}
}
