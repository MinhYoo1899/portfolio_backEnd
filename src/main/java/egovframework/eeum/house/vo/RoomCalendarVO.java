package egovframework.eeum.house.vo;

import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 객실 재고/요금 캘린더 VO — ROOM_CALENDAR 매핑.
 *
 * <p>객실 × 숙박일 일자별 재고/가격/마감. 가용수량 = TOTAL_QTY − SOLD_QTY(DB 확정) − HELD(Redis 임시점유).
 * SOLD_QTY는 결제완료 시 DB 최종확정 기준으로만 증감(오버부킹 원천차단). STAY_DATE는 KST 숙박일.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoomCalendarVO {

	/** 객실캘린더일련번호(PK). */
	private Long roomCalendarSn;

	/** 객실일련번호. */
	private Long roomSn;

	/** 숙박일(KST). */
	private LocalDate stayDate;

	/** 판매가능 총수량. */
	private Integer totalQty;

	/** 확정 판매수량(DB 최종확정). */
	private Integer soldQty;

	/** 1박 판매가(KRW 정수). */
	private Integer priceAmt;

	/** 마감여부(Y=판매중지). */
	private String closedYn;

	/** 휴무/마감 사유(선택 — 마감 시 저장). */
	private String closeRsn;

	/** 등록일시. */
	private LocalDateTime regDt;

	/** 등록자. */
	private Long rgtrSn;

	/** 수정일시. */
	private LocalDateTime mdfcnDt;

	/** 수정자. */
	private Long mdfrSn;
}
