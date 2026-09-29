package egovframework.eeum.reservation.service.operator;

import egovframework.eeum.base.response.PageResponse;
import egovframework.eeum.reservation.web.dto.OperatorReservationDetailResponse;
import egovframework.eeum.reservation.web.dto.OperatorReservationListItemResponse;
import egovframework.eeum.reservation.web.dto.OperatorReservationSearchRequest;

/**
 * 운영자 예약 콘솔 서비스 — 전 판매자 예약 조회(목록/상세). 조회 전용(환불/취소 mutation 은 후속 슬라이스).
 */
public interface OperatorReservationService {

	/** 목록(상태/기간/키워드 필터 + 페이지네이션). */
	PageResponse<OperatorReservationListItemResponse> list(OperatorReservationSearchRequest request);

	/** 단건 상세. 없으면 RESERVATION_NOT_FOUND. */
	OperatorReservationDetailResponse detail(long reservationId);
}
