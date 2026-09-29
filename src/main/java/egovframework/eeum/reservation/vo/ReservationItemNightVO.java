package egovframework.eeum.reservation.vo;

import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 예약 일자별 단가 스냅샷 VO — RESERVATION_ITEM_NIGHT 매핑.
 *
 * <p>총액만 저장하지 않고 숙박일별 단가를 스냅샷으로 남긴다(가격 변동 무관 증적). 숙박일은 KST LocalDate.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReservationItemNightVO {

	/** 예약일자단가일련번호(PK). */
	private Long reservationItemNightSn;

	/** 예약항목일련번호. */
	private Long reservationItemSn;

	/** 숙박일(KST). */
	private LocalDate stayDate;

	/** 1박 단가(KRW 정수, 예약시점 스냅샷). */
	private Integer nightPriceAmt;

	/** 해당 숙박일 점유수량. */
	private Integer nightQty;

	/** 등록일시. */
	private LocalDateTime regDt;

	/** 등록자(행위자). */
	private Long rgtrSn;

	/** 수정일시. */
	private LocalDateTime mdfcnDt;

	/** 수정자(행위자). */
	private Long mdfrSn;

	/** 삭제여부. */
	private String delYn;
}
