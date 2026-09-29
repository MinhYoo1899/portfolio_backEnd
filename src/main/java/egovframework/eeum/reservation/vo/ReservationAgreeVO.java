package egovframework.eeum.reservation.vo;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 예약 동의 스냅샷 VO — RESERVATION_AGRE 매핑.
 *
 * <p>약관 버전번호({@code trmsVerNo})는 동의시점 스냅샷 — 서버가 TRMS_MST 에서 해석해 채운다(클라이언트 값 미신뢰).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReservationAgreeVO {

	/** 예약동의일련번호(PK). */
	private Long reservationAgreSn;

	/** 예약일련번호. */
	private Long reservationSn;

	/** 약관일련번호(스냅샷 참조 — 강제 FK 없음). */
	private Long trmsSn;

	/** 약관버전번호(동의시점 스냅샷 — 서버 해석). */
	private String trmsVerNo;

	/** 동의목적코드(예: RESERVATION_FULFILLMENT/THIRD_PARTY_PROVISION). */
	private String agrePrpsCd;

	/** 동의(수집)항목내용 스냅샷. */
	private String agreItemCn;

	/** 동의일시(UTC). */
	private LocalDateTime agreDt;

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
