package egovframework.eeum.reservation.service.operator.impl;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import egovframework.eeum.base.exception.BusinessException;
import egovframework.eeum.base.response.PageResponse;
import egovframework.eeum.reservation.exception.ReservationErrorCode;
import egovframework.eeum.reservation.service.operator.OperatorReservationService;
import egovframework.eeum.reservation.service.operator.dao.OperatorReservationMapper;
import egovframework.eeum.reservation.vo.OperatorReservationVO;
import egovframework.eeum.reservation.web.dto.OperatorReservationDetailResponse;
import egovframework.eeum.reservation.web.dto.OperatorReservationListItemResponse;
import egovframework.eeum.reservation.web.dto.OperatorReservationSearchRequest;
import lombok.RequiredArgsConstructor;

/**
 * 운영자 예약 콘솔 서비스 구현 — 전 판매자 예약 조회(소유권 필터 없음, 권한 RESERVATION_VIEW 로 통제).
 * 게스트 전화(암호화 PII)·결제수단·예약경로·환불은 미제공(응답 unsupported/미포함).
 */
@Service
@RequiredArgsConstructor
public class OperatorReservationServiceImpl implements OperatorReservationService {

	private final OperatorReservationMapper reservationMapper;

	@Override
	@Transactional(readOnly = true)
	public PageResponse<OperatorReservationListItemResponse> list(OperatorReservationSearchRequest request) {
		String status = blankToNull(request.status());
		String keyword = blankToNull(request.keyword());
		int page = request.pageOrDefault();
		int size = request.sizeOrDefault();

		long total = reservationMapper.countOperatorReservation(status, request.startDate(), request.endDate(), keyword);
		List<OperatorReservationListItemResponse> items = reservationMapper
				.selectOperatorReservationList(status, request.startDate(), request.endDate(), keyword,
						request.offset(), size)
				.stream().map(OperatorReservationListItemResponse::from).toList();
		return PageResponse.of(items, total, page, size);
	}

	@Override
	@Transactional(readOnly = true)
	public OperatorReservationDetailResponse detail(long reservationId) {
		OperatorReservationVO vo = reservationMapper.selectOperatorReservationDetail(reservationId);
		if (vo == null) {
			throw new BusinessException(ReservationErrorCode.RESERVATION_NOT_FOUND);
		}
		return OperatorReservationDetailResponse.from(vo);
	}

	private static String blankToNull(String s) {
		return (s == null || s.isBlank()) ? null : s;
	}
}
