package egovframework.eeum.reservation.vo;

import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 예약 대상 숙박일의 재고/가격 스냅샷 — ROOM_CALENDAR 행(예약 도메인 자체 매퍼 select).
 *
 * <p>[체크인, 체크아웃) 각 밤에 대해 마감여부/재고/단가를 읽어 판매가능 검증 + 홀드 + 금액 스냅샷에 사용한다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReservationCalendarNightVO {

	/** 숙박일(KST). */
	private LocalDate stayDate;

	/** 판매가능 총수량. */
	private Integer totalQty;

	/** DB 확정 판매수량. */
	private Integer soldQty;

	/** 1박 단가(KRW 정수). */
	private Integer priceAmt;

	/** 마감여부(Y/N). */
	private String closedYn;
}
