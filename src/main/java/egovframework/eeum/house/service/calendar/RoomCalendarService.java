package egovframework.eeum.house.service.calendar;

import java.util.List;

import egovframework.eeum.auth.vo.AuthUser;
import egovframework.eeum.house.web.dto.RoomCalendarCloseRequest;
import egovframework.eeum.house.web.dto.RoomCalendarPriceStockSetRequest;
import egovframework.eeum.house.web.dto.RoomCalendarResponse;
import egovframework.eeum.house.web.dto.RoomCalendarSearchRequest;
import egovframework.eeum.house.web.dto.RoomCalendarSetRequest;

/**
 * 객실 재고/요금 캘린더 서비스(판매자) — 본인 소유 객실의 일자별 재고·가격·마감 설정/조회.
 *
 * <p>가격은 날짜/요일별로 다를 수 있고(기간+요일 일괄 설정), 재고 최종확정(SOLD)은 후속 예약 슬라이스가 담당.
 */
public interface RoomCalendarService {

	/** 기간 일괄 설정(본인 소유 객실) — 재고/가격/마감 upsert. */
	void setRange(RoomCalendarSetRequest request, AuthUser partner);

	/**
	 * 가격/수량 일괄 설정(본인 소유 객실) — TOTAL/PRICE만 갱신하고 <b>기존 CLOSED_YN 보존</b>(신규 행은 N).
	 * 요금 변경이 마감일을 재오픈하지 않도록 {@link #setRange}(마감 포함)와 분리. SOLD_QTY 보존.
	 */
	void setPriceStockRange(RoomCalendarPriceStockSetRequest request, AuthUser partner);

	/** 운영자 대행(HOUSE_PROXY_MNG) 가격/수량 설정 — owner=대상객실 서버해석. {@link #setPriceStockRange} 미러. */
	void setPriceStockRangeAsOperator(RoomCalendarPriceStockSetRequest request, AuthUser operator);

	/**
	 * 마감/해제 토글(본인 소유 객실) — 지정 날짜의 CLOSED_YN만 변경(가격/수량/판매수량 보존).
	 * Y=마감(없는 날짜는 placeholder 생성), N=해제(설정되지 않은 날짜 포함 시 거부). 과거 날짜 거부.
	 */
	void closeRange(RoomCalendarCloseRequest request, AuthUser partner);

	/** 운영자 대행(HOUSE_PROXY_MNG) 마감/해제 — owner=대상객실 서버해석, 행위자=운영자. {@link #closeRange} 미러. */
	void closeRangeAsOperator(RoomCalendarCloseRequest request, AuthUser operator);

	/** 기간 조회(본인 소유 객실). */
	List<RoomCalendarResponse> list(RoomCalendarSearchRequest request, AuthUser partner);

	/** 운영자 대행(HOUSE_PROXY_MNG) — owner=대상객실 서버해석, 행위자=운영자. */
	void setRangeAsOperator(RoomCalendarSetRequest request, AuthUser operator);

	/** 운영자 대행 조회(HOUSE_PROXY_MNG) — owner=대상객실 서버해석. {@link #list} 의 운영자 미러(읽기 전용). */
	List<RoomCalendarResponse> listAsOperator(RoomCalendarSearchRequest request, AuthUser operator);
}
