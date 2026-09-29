package egovframework.eeum.reservation.vo;

import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

/**
 * 운영자 예약 콘솔 조회 VO — RESERVATION + HOUSE + ITEM(객실명) + GUEST(예약자명) + PAYMENT(결제상태) 조인 집계.
 *
 * <p>조회 전용. 게스트 전화(암호화 PII)는 미포함(이번 슬라이스). 결제수단/예약경로/환불은 스키마 부재로 미제공(unsupported).
 */
@Getter
@Setter
public class OperatorReservationVO {

	/** 예약일련번호(PK). */
	private Long reservationSn;

	/** 주문번호(화면 예약번호). */
	private String orderNo;

	/** 예약상태코드(HOLD/PAYMENT_PENDING/RESERVED/USED/CANCELLED/EXPIRED). */
	private String reservationSttsCd;

	/** 고택일련번호. */
	private Long houseSn;

	/** 고택명(HOUSE 조인). */
	private String houseNm;

	/** 객실명 스냅샷(RESERVATION_ITEM). */
	private String roomNmSnap;

	/** 예약자명(RESERVATION_GUEST RESERVER, 평문). */
	private String guestNm;

	/** 투숙인원수. */
	private Integer guestCnt;

	/** 체크인 숙박일(KST). */
	private LocalDate checkInDate;

	/** 체크아웃일(KST). */
	private LocalDate checkOutDate;

	/** 박수. */
	private Integer ngtCnt;

	/** 총결제금액(KRW). */
	private Integer totalAmt;

	/** 등록일시. */
	private LocalDateTime regDt;

	/** 결제상태코드(PAYMENT 최신, 없으면 null). */
	private String paymentSttsCd;

	/** PG제공자코드(PAYMENT 최신). */
	private String pgPrvdrCd;

	/** 결제 승인일시(PAYMENT 최신, 확정일 표시용). */
	private LocalDateTime aprvDt;
}
