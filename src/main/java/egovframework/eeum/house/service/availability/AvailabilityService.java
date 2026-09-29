package egovframework.eeum.house.service.availability;

import egovframework.eeum.house.web.dto.AvailabilityResponse;
import egovframework.eeum.house.web.dto.AvailabilitySearchRequest;

/**
 * 가용성 조회 서비스(손님) — 고택의 ACTIVE 객실 중 요청 기간·인원에 예약 가능한 객실과 가격합계.
 *
 * <p>재고는 DB(ROOM_CALENDAR) 기준(TOTAL−SOLD). Redis 임시점유(HOLD) 차감은 예약 슬라이스에서 추가 예정.
 */
public interface AvailabilityService {

	AvailabilityResponse search(AvailabilitySearchRequest request);
}
