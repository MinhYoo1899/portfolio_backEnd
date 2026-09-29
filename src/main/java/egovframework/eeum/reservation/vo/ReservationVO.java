package egovframework.eeum.reservation.vo;

import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 예약 헤더 VO — RESERVATION 매핑.
 *
 * <p>소유권 컬럼(PRTNR_SN 판매자 / RESERVER_USER_SN 예약자)은 스키마에 고정 — IDOR 방어의 WHERE 근거.
 * 숙박일은 KST LocalDate([체크인, 체크아웃) 반개구간), 발생/만료 시각은 UTC LocalDateTime.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReservationVO {

	/** 예약일련번호(PK). */
	private Long reservationSn;

	/** 주문번호(사용자 노출·결제 대응, UNIQUE). */
	private String orderNo;

	/** 멱등키(생성 재시도 중복예약 차단, UNIQUE — 헤더에서 유입, 없으면 null). */
	private String idempotencyKey;

	/** 예약상태코드(HOLD/PAYMENT_PENDING/RESERVED/USED/CANCELLED/EXPIRED). */
	private String reservationSttsCd;

	/** 파트너일련번호(소유권 — 판매자). */
	private Long prtnrSn;

	/** 고택일련번호. */
	private Long houseSn;

	/** 객실일련번호. */
	private Long roomSn;

	/** 예약자(사용자일련번호 — EEUM 회원, 소유권). */
	private Long reserverUserSn;

	/** 체크인 숙박일(KST, 포함). */
	private LocalDate checkInDate;

	/** 체크아웃일(KST, 미포함 — 반개구간). */
	private LocalDate checkOutDate;

	/** 박수. */
	private Integer ngtCnt;

	/** 투숙인원수. */
	private Integer guestCnt;

	/** 총결제금액(KRW 정수, 스냅샷). */
	private Integer totalAmt;

	/** 통화코드. */
	private String crncCd;

	/** 홀드만료일시(UTC — HOLD/PAYMENT_PENDING 한정). */
	private LocalDateTime holdExpryDt;

	/** 소프트홀드 식별자(Redis ZSET member 접두 — release 대상). */
	private String holdId;

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
