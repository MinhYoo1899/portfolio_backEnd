package egovframework.eeum.reservation.vo;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 예약 객실(항목) VO — RESERVATION_ITEM 매핑.
 *
 * <p>MVP 는 예약당 1객실이나 다객실 확장 대비 항목 테이블로 분리. 객실명/소계금액은 예약시점 스냅샷.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReservationItemVO {

	/** 예약항목일련번호(PK). */
	private Long reservationItemSn;

	/** 예약일련번호. */
	private Long reservationSn;

	/** 객실일련번호. */
	private Long roomSn;

	/** 객실명 스냅샷(예약시점). */
	private String roomNmSnap;

	/** 객실 소계금액(KRW 정수, 스냅샷). */
	private Integer itemAmt;

	/** 예약 객실수량. */
	private Integer roomQty;

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
