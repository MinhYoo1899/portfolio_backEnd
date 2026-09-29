package egovframework.eeum.house.service.availability.impl;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import egovframework.eeum.base.exception.BusinessException;
import egovframework.eeum.house.exception.RoomErrorCode;
import egovframework.eeum.house.service.availability.AvailabilityService;
import egovframework.eeum.house.service.calendar.dao.RoomCalendarMapper;
import egovframework.eeum.house.vo.RoomAvailabilityVO;
import egovframework.eeum.house.web.dto.AvailabilityResponse;
import egovframework.eeum.house.web.dto.AvailabilitySearchRequest;
import egovframework.eeum.house.web.dto.RoomAvailabilityItem;
import lombok.RequiredArgsConstructor;

/**
 * 가용성 조회 구현 — ACTIVE 고택·객실의 일자별 재고/가격을 집계해 객실별 가용/가격을 판정.
 *
 * <p>가용 = (요청 기간의 판매가능 밤 수 == 박수) AND (최대인원 >= 인원) AND (박수 >= 최소숙박). "행 없으면 판매 불가".
 */
@Service
@RequiredArgsConstructor
public class AvailabilityServiceImpl implements AvailabilityService {

	private static final long MAX_NIGHTS = 365L;
	private static final int QTY = 1;

	private final RoomCalendarMapper roomCalendarMapper;

	@Override
	@Transactional(readOnly = true)
	public AvailabilityResponse search(AvailabilitySearchRequest request) {
		int nights = validateAndCountNights(request.checkInDate(), request.checkOutDate());
		List<RoomAvailabilityItem> rooms = roomCalendarMapper
				.selectRoomAvailability(request.houseSn(), request.checkInDate(), request.checkOutDate(), QTY).stream()
				.map(vo -> toItem(vo, nights, request.guestCount()))
				.toList();
		return AvailabilityResponse.of(request.houseSn(), request.checkInDate(), request.checkOutDate(), nights, rooms);
	}

	private static RoomAvailabilityItem toItem(RoomAvailabilityVO vo, int nights, int guestCount) {
		int sellable = vo.getSellableNights() == null ? 0 : vo.getSellableNights();
		int maxPrsn = vo.getMaxPrsnCnt() == null ? 0 : vo.getMaxPrsnCnt();
		int minStay = vo.getMinStayNgtCnt() == null ? 1 : vo.getMinStayNgtCnt();

		String reason = null;
		if (maxPrsn < guestCount) {
			reason = "CAPACITY";
		} else if (nights < minStay) {
			reason = "MIN_STAY";
		} else if (sellable < nights) {
			reason = "SOLD_OUT";
		}
		boolean available = reason == null;
		return new RoomAvailabilityItem(vo.getRoomSn(), vo.getRoomNm(), vo.getBscPrsnCnt(), vo.getMaxPrsnCnt(),
				available, reason, available ? vo.getTotalPrice() : null);
	}

	/** 체크아웃 > 체크인(최소 1박), 최대 박수 제한. 박수 반환. */
	private static int validateAndCountNights(LocalDate checkIn, LocalDate checkOut) {
		long nights = ChronoUnit.DAYS.between(checkIn, checkOut);
		if (nights < 1) {
			throw new BusinessException(RoomErrorCode.CALENDAR_INVALID_RANGE, "체크아웃일은 체크인일 이후여야 합니다(최소 1박).");
		}
		if (nights > MAX_NIGHTS) {
			throw new BusinessException(RoomErrorCode.CALENDAR_INVALID_RANGE,
					"조회 가능한 최대 숙박은 " + MAX_NIGHTS + "박입니다.");
		}
		return (int) nights;
	}
}
