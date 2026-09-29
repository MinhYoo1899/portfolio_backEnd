package egovframework.eeum.house.service.calendar.dao;

import java.time.LocalDate;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import egovframework.eeum.house.vo.RoomAvailabilityVO;
import egovframework.eeum.house.vo.RoomCalendarVO;

/**
 * 객실 재고/요금 캘린더 매퍼 — XML: {@code egovframework/mapper/eeum/house/RoomCalendarMapper_mysql.xml}
 *
 * <p>소유권은 서비스가 부모 객실(ROOM→HOUSE.PRTNR_SN)로 선검증. 재고 차감(SOLD)은 후속 예약 슬라이스에서 FOR UPDATE.
 */
@Mapper
public interface RoomCalendarMapper {

	/**
	 * 기간 일괄 upsert — 주어진 날짜들에 재고/가격/마감을 설정. 기존 행이면 TOTAL/PRICE/CLOSED만 갱신(SOLD 보존).
	 */
	void upsertCalendarRange(@Param("roomSn") long roomSn, @Param("dates") List<LocalDate> dates,
			@Param("totalQty") int totalQty, @Param("priceAmt") int priceAmt,
			@Param("closedYn") String closedYn, @Param("actorSn") long actorSn);

	/**
	 * 가격/수량만 일괄 upsert — 기존 행이면 TOTAL/PRICE만 갱신(<b>CLOSED_YN·SOLD_QTY 보존</b>), 신규 행은 CLOSED_YN='N'.
	 * 요금 변경이 기존 마감일을 재오픈하지 않도록 {@link #upsertCalendarRange}(CLOSED_YN 포함)와 분리.
	 */
	void upsertPriceStockRange(@Param("roomSn") long roomSn, @Param("dates") List<LocalDate> dates,
			@Param("totalQty") int totalQty, @Param("priceAmt") int priceAmt, @Param("actorSn") long actorSn);

	/** 기간 조회(숙박일순). */
	List<RoomCalendarVO> selectCalendarByRoomAndRange(@Param("roomSn") long roomSn,
			@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

	/**
	 * 마감(Y) — 주어진 날짜들을 CLOSED_YN='Y' 로. 기존 행이면 CLOSED_YN만 갱신(TOTAL/PRICE/SOLD 보존),
	 * 행이 없으면 마감 placeholder(TOTAL_QTY=0, PRICE_AMT=0, CLOSED_YN='Y') 생성.
	 */
	int closeDatesUpsert(@Param("roomSn") long roomSn, @Param("dates") List<LocalDate> dates,
			@Param("closeRsn") String closeRsn, @Param("actorSn") long actorSn);

	/** 주어진 날짜들 중 캘린더 행이 존재하는 개수 — 해제(open) 전 "설정되지 않은 날짜" 검출용. */
	int countExistingDates(@Param("roomSn") long roomSn, @Param("dates") List<LocalDate> dates);

	/** 해제(N) — 기존 행만 CLOSED_YN='N' 로(없는 날짜는 만들지 않음). TOTAL/PRICE/SOLD 보존. 영향 행 수 반환. */
	int openDates(@Param("roomSn") long roomSn, @Param("dates") List<LocalDate> dates,
			@Param("actorSn") long actorSn);

	/**
	 * 가용성 집계(손님) — ACTIVE 고택의 ACTIVE 객실별로 {@code [checkIn, checkOut)} 기간의
	 * 판매가능 밤 수·가격합계를 집계(마감X·재고 qty 이상·가격 행 존재). 가용 판정은 서비스가.
	 */
	List<RoomAvailabilityVO> selectRoomAvailability(@Param("houseSn") long houseSn,
			@Param("checkIn") LocalDate checkIn, @Param("checkOut") LocalDate checkOut, @Param("qty") int qty);

	/**
	 * 고택의 미래 판매가능 캘린더 행 수 — 발행검증용. ACTIVE 객실의 {@code fromDate} 이후 마감X·가격>0·재고여유 행 개수.
	 */
	int countFutureSellableByHouse(@Param("houseSn") long houseSn, @Param("fromDate") LocalDate fromDate);

	/**
	 * 객실의 미래 판매가능 캘린더 행 수 — 발행검증용(객실-결속). {@code fromDate} 이후 마감X·가격>0·재고여유 행 개수.
	 */
	int countFutureSellableByRoom(@Param("roomSn") long roomSn, @Param("fromDate") LocalDate fromDate);
}
