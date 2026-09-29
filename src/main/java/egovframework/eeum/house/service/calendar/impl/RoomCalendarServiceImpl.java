package egovframework.eeum.house.service.calendar.impl;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import egovframework.eeum.account.service.partner.PartnerService;
import egovframework.eeum.auth.vo.AuthUser;
import egovframework.eeum.base.audit.Audit;
import egovframework.eeum.base.exception.BusinessException;
import egovframework.eeum.house.exception.HouseErrorCode;
import egovframework.eeum.house.exception.RoomErrorCode;
import egovframework.eeum.house.service.calendar.RoomCalendarService;
import egovframework.eeum.house.service.calendar.dao.RoomCalendarMapper;
import egovframework.eeum.house.service.house.HouseProxyOwnerResolver;
import egovframework.eeum.house.service.house.HousePublishGuard;
import egovframework.eeum.house.service.house.ReadinessSnapshot;
import egovframework.eeum.house.service.house.HouseReviewGuard;
import egovframework.eeum.house.service.room.dao.RoomMapper;
import egovframework.eeum.house.vo.RoomVO;
import egovframework.eeum.house.web.dto.RoomCalendarCloseRequest;
import egovframework.eeum.house.web.dto.RoomCalendarPriceStockSetRequest;
import egovframework.eeum.house.web.dto.RoomCalendarResponse;
import egovframework.eeum.house.web.dto.RoomCalendarSearchRequest;
import egovframework.eeum.house.web.dto.RoomCalendarSetRequest;
import lombok.RequiredArgsConstructor;

/**
 * 객실 캘린더 서비스 구현(판매자) — 부모 객실 소유권(ROOM→HOUSE→PRTNR_SN) 검증 후 기간 일괄 upsert/조회.
 */
@Service
@RequiredArgsConstructor
public class RoomCalendarServiceImpl implements RoomCalendarService {

	/** 1회 일괄설정/조회 최대 기간(일) — 과대 upsert 방지. */
	private static final long MAX_RANGE_DAYS = 365L;
	/** 1회 마감/해제 최대 날짜 수 — 과대 변경 방지(DTO @Size 와 동일, 서비스 2중 방어). */
	private static final int MAX_CLOSE_DATES = 366;
	/** 숙박일 기준 타임존(날짜는 KST LocalDate — timezone-standard). 과거 날짜 판정용. */
	private static final ZoneId KST = ZoneId.of("Asia/Seoul");

	private final RoomCalendarMapper roomCalendarMapper;
	private final RoomMapper roomMapper;
	private final PartnerService partnerService;
	private final HouseProxyOwnerResolver proxyResolver;
	private final HousePublishGuard publishGuard;
	private final HouseReviewGuard reviewGuard;

	// ===================== 판매자(파트너 본인) — owner=토큰 해석 =====================

	@Override
	@Transactional
	@Audit(actn = "ROOM_CALENDAR_SET", trgtType = "ROOM", trgtId = "#request.roomSn()")
	public void setRange(RoomCalendarSetRequest request, AuthUser partner) {
		setRangeCore(request, requirePrtnrSn(partner), partner.userSn());
	}

	@Override
	@Transactional
	@Audit(actn = "ROOM_CALENDAR_PRICE_STOCK_SET", trgtType = "ROOM", trgtId = "#request.roomSn()")
	public void setPriceStockRange(RoomCalendarPriceStockSetRequest request, AuthUser partner) {
		setPriceStockRangeCore(request, requirePrtnrSn(partner), partner.userSn());
	}

	@Override
	@Transactional
	@Audit(actn = "ROOM_CALENDAR_CLOSE", trgtType = "ROOM", trgtId = "#request.roomSn()")
	public void closeRange(RoomCalendarCloseRequest request, AuthUser partner) {
		closeRangeCore(request, requirePrtnrSn(partner), partner.userSn());
	}

	// ============ 운영자 대행(HOUSE_PROXY_MNG) — owner=대상객실 서버해석, 행위자=운영자 ============

	@Override
	@Transactional
	@Audit(actn = "ROOM_PROXY_CALENDAR_SET", trgtType = "ROOM", trgtId = "#request.roomSn()")
	public void setRangeAsOperator(RoomCalendarSetRequest request, AuthUser operator) {
		setRangeCore(request, proxyResolver.resolveOwnerForRoom(request.roomSn(), operator), operator.userSn());
		// 검수요청 상태였으면 내용 변경으로 DRAFT 자동복귀(+REVERT 이력)
		Long houseSn = roomMapper.selectHouseSnByRoom(request.roomSn());
		if (houseSn != null) {
			reviewGuard.markDirtyIfUnderReview(houseSn, operator.userSn());
		}
	}

	@Override
	@Transactional
	@Audit(actn = "ROOM_PROXY_CALENDAR_PRICE_STOCK_SET", trgtType = "ROOM", trgtId = "#request.roomSn()")
	public void setPriceStockRangeAsOperator(RoomCalendarPriceStockSetRequest request, AuthUser operator) {
		setPriceStockRangeCore(request, proxyResolver.resolveOwnerForRoom(request.roomSn(), operator), operator.userSn());
		// 검수요청 상태였으면 내용 변경으로 DRAFT 자동복귀(+REVERT 이력)
		Long houseSn = roomMapper.selectHouseSnByRoom(request.roomSn());
		if (houseSn != null) {
			reviewGuard.markDirtyIfUnderReview(houseSn, operator.userSn());
		}
	}

	@Override
	@Transactional
	@Audit(actn = "ROOM_PROXY_CALENDAR_CLOSE", trgtType = "ROOM", trgtId = "#request.roomSn()")
	public void closeRangeAsOperator(RoomCalendarCloseRequest request, AuthUser operator) {
		closeRangeCore(request, proxyResolver.resolveOwnerForRoom(request.roomSn(), operator), operator.userSn());
		// 검수요청 상태였으면 내용 변경으로 DRAFT 자동복귀(+REVERT 이력)
		Long houseSn = roomMapper.selectHouseSnByRoom(request.roomSn());
		if (houseSn != null) {
			reviewGuard.markDirtyIfUnderReview(houseSn, operator.userSn());
		}
	}

	// ============ 코어(소유자=ownerPrtnrSn, 행위자=actorUserSn 공유 — 파트너/운영자 동일 로직) ============

	private void setRangeCore(RoomCalendarSetRequest request, long ownerPrtnrSn, long actorUserSn) {
		requireOwnedRoom(request.roomSn(), ownerPrtnrSn);
		List<LocalDate> dates = resolveDates(request.startDate(), request.endDate(), request.daysOfWeek());
		// 발행 후 표류 방지(비퇴행) — 변경 전 부모 고택 readiness 캡처
		Long houseSn = roomMapper.selectHouseSnByRoom(request.roomSn());
		ReadinessSnapshot before = houseSn == null ? null : publishGuard.snapshotIfActive(houseSn, ownerPrtnrSn);
		try {
			roomCalendarMapper.upsertCalendarRange(request.roomSn(), dates, request.totalQty(),
					request.priceAmt(), request.closedYnOrDefault(), actorUserSn);
		} catch (DataIntegrityViolationException e) {
			// CK_ROOM_CALENDAR_QTY(SOLD<=TOTAL) 위반 — 이미 판매된 수량보다 적게 설정 시도
			throw new BusinessException(RoomErrorCode.CALENDAR_TOTAL_BELOW_SOLD,
					RoomErrorCode.CALENDAR_TOTAL_BELOW_SOLD.defaultMessage(), e);
		}
		// 마감/재고0 으로 미래 판매가능을 소멸(true→false)시키면 거부, 오픈/증가는 허용(롤백)
		if (houseSn != null) {
			publishGuard.enforceNoRegression(houseSn, ownerPrtnrSn, before);
		}
	}

	// ============ 가격/수량 전용 코어 — CLOSED_YN 보존(요금 변경이 마감일을 재오픈하지 않음). set 과 분리 ============

	private void setPriceStockRangeCore(RoomCalendarPriceStockSetRequest request, long ownerPrtnrSn, long actorUserSn) {
		requireOwnedRoom(request.roomSn(), ownerPrtnrSn);
		List<LocalDate> dates = resolveDates(request.startDate(), request.endDate(), request.daysOfWeek());
		// 발행 후 표류 방지(비퇴행) — 변경 전 부모 고택 readiness 캡처
		Long houseSn = roomMapper.selectHouseSnByRoom(request.roomSn());
		ReadinessSnapshot before = houseSn == null ? null : publishGuard.snapshotIfActive(houseSn, ownerPrtnrSn);
		try {
			roomCalendarMapper.upsertPriceStockRange(request.roomSn(), dates, request.totalQty(),
					request.priceAmt(), actorUserSn);
		} catch (DataIntegrityViolationException e) {
			// CK_ROOM_CALENDAR_QTY(SOLD<=TOTAL) 위반 — set 과 동일 정책
			throw new BusinessException(RoomErrorCode.CALENDAR_TOTAL_BELOW_SOLD,
					RoomErrorCode.CALENDAR_TOTAL_BELOW_SOLD.defaultMessage(), e);
		}
		// 가격/수량 변경도 미래 판매가능 수에 영향 — 충족→미충족 전환이면 거부(롤백)
		if (houseSn != null) {
			publishGuard.enforceNoRegression(houseSn, ownerPrtnrSn, before);
		}
	}

	// ============ 마감/해제 토글 코어 — CLOSED_YN만 변경(가격/수량/판매수량 보존), set 계약 무영향 ============

	private void closeRangeCore(RoomCalendarCloseRequest request, long ownerPrtnrSn, long actorUserSn) {
		requireOwnedRoom(request.roomSn(), ownerPrtnrSn);
		List<LocalDate> dates = distinctFutureDates(request.dates());
		if (request.isClose()) {
			// 마감(Y) — 기존 행은 CLOSED_YN만, 없는 날짜는 0/0/'Y' placeholder. 가격/수량 클로버 없음.
			String closeRsn = (request.closeRsn() == null || request.closeRsn().isBlank())
					? null : request.closeRsn().trim();
			// 닫기는 판매가능 감소 방향 — 변경 전 부모 고택 readiness 캡처
			Long houseSn = roomMapper.selectHouseSnByRoom(request.roomSn());
			ReadinessSnapshot before = houseSn == null ? null : publishGuard.snapshotIfActive(houseSn, ownerPrtnrSn);
			roomCalendarMapper.closeDatesUpsert(request.roomSn(), dates, closeRsn, actorUserSn);
			// 미래 판매가능을 소멸(true→false)시키면 거부(롤백)
			if (houseSn != null) {
				publishGuard.enforceNoRegression(houseSn, ownerPrtnrSn, before);
			}
		} else {
			// 해제(N) — 설정되지 않은 날짜는 원가격/원수량이 없어 열 수 없음(거부). 있는 날짜만 CLOSED_YN='N'.
			if (roomCalendarMapper.countExistingDates(request.roomSn(), dates) < dates.size()) {
				throw new BusinessException(RoomErrorCode.CALENDAR_ROW_NOT_FOUND);
			}
			roomCalendarMapper.openDates(request.roomSn(), dates, actorUserSn);
			// 해제는 판매가능 증가 방향 — publishGuard 불요. 무결성(소유권·행존재)은 위에서 보장.
		}
	}

	/** 중복 제거 + 과거 날짜(오늘 KST 이전) 거부 + 최대 개수 제한. 빈 결과면 INVALID_RANGE. */
	private static List<LocalDate> distinctFutureDates(List<LocalDate> raw) {
		LocalDate today = LocalDate.now(KST);
		LinkedHashSet<LocalDate> set = new LinkedHashSet<>();
		for (LocalDate d : raw) {
			if (d == null) {
				continue;
			}
			if (d.isBefore(today)) {
				throw new BusinessException(RoomErrorCode.CALENDAR_INVALID_RANGE, "과거 날짜는 설정할 수 없습니다.");
			}
			set.add(d);
		}
		if (set.isEmpty()) {
			throw new BusinessException(RoomErrorCode.CALENDAR_INVALID_RANGE, "설정할 날짜가 없습니다.");
		}
		if (set.size() > MAX_CLOSE_DATES) {
			throw new BusinessException(RoomErrorCode.CALENDAR_INVALID_RANGE,
					"한 번에 설정할 수 있는 날짜 수는 최대 " + MAX_CLOSE_DATES + "일입니다.");
		}
		return new ArrayList<>(set);
	}

	@Override
	@Transactional(readOnly = true)
	public List<RoomCalendarResponse> list(RoomCalendarSearchRequest request, AuthUser partner) {
		long prtnrSn = requirePrtnrSn(partner);
		requireOwnedRoom(request.roomSn(), prtnrSn);
		validateRange(request.startDate(), request.endDate());
		return roomCalendarMapper.selectCalendarByRoomAndRange(request.roomSn(), request.startDate(),
				request.endDate()).stream().map(RoomCalendarResponse::from).toList();
	}

	@Override
	@Transactional(readOnly = true)
	public List<RoomCalendarResponse> listAsOperator(RoomCalendarSearchRequest request, AuthUser operator) {
		// owner=대상객실 서버해석(미승인/비활성 파트너면 resolver 가 차단). list() 의 운영자 미러 — 동일 검증/쿼리/응답.
		long ownerPrtnrSn = proxyResolver.resolveOwnerForRoom(request.roomSn(), operator);
		requireOwnedRoom(request.roomSn(), ownerPrtnrSn);
		validateRange(request.startDate(), request.endDate());
		return roomCalendarMapper.selectCalendarByRoomAndRange(request.roomSn(), request.startDate(),
				request.endDate()).stream().map(RoomCalendarResponse::from).toList();
	}

	// ------------------------------------------------------------------

	private long requirePrtnrSn(AuthUser partner) {
		if (partner == null) {
			throw BusinessException.unauthorized();
		}
		Long prtnrSn = partnerService.findManageablePrtnrSn(partner.userSn());
		if (prtnrSn == null) {
			throw new BusinessException(HouseErrorCode.HOUSE_NOT_PARTNER);
		}
		return prtnrSn;
	}

	/** 부모 객실이 본인 소유인지(ROOM→HOUSE 조인 ownerPrtnrSn) — 아니면 404. */
	private void requireOwnedRoom(long roomSn, long prtnrSn) {
		RoomVO room = roomMapper.selectRoomDetail(roomSn);
		if (room == null || room.getOwnerPrtnrSn() == null || room.getOwnerPrtnrSn() != prtnrSn) {
			throw new BusinessException(RoomErrorCode.ROOM_NOT_FOUND);
		}
	}

	/** 기간 검증 + 요일 필터 적용한 날짜 목록 산출. 비면 INVALID_RANGE. */
	private static List<LocalDate> resolveDates(LocalDate start, LocalDate end, List<Integer> daysOfWeek) {
		validateRange(start, end);
		Set<Integer> dows = (daysOfWeek == null || daysOfWeek.isEmpty()) ? null : new HashSet<>(daysOfWeek);
		List<LocalDate> dates = new ArrayList<>();
		for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
			if (dows == null || dows.contains(d.getDayOfWeek().getValue())) {
				dates.add(d);
			}
		}
		if (dates.isEmpty()) {
			throw new BusinessException(RoomErrorCode.CALENDAR_INVALID_RANGE,
					"설정할 날짜가 없습니다(요일 필터 확인).");
		}
		return dates;
	}

	private static void validateRange(LocalDate start, LocalDate end) {
		if (end.isBefore(start)) {
			throw new BusinessException(RoomErrorCode.CALENDAR_INVALID_RANGE, "종료일이 시작일보다 빠릅니다.");
		}
		if (ChronoUnit.DAYS.between(start, end) > MAX_RANGE_DAYS) {
			throw new BusinessException(RoomErrorCode.CALENDAR_INVALID_RANGE,
					"한 번에 설정/조회할 수 있는 기간은 최대 " + MAX_RANGE_DAYS + "일입니다.");
		}
	}
}
