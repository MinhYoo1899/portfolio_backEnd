package egovframework.eeum.reservation.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 객실 소유권/판매가능 해석 결과 — ROOM→HOUSE 조인(예약 도메인 자체 매퍼 select).
 *
 * <p>소유권(PRTNR_SN/HOUSE_SN)과 판매성(객실·고택 ACTIVE)을 서버가 roomSn 으로만 해석한다(본문에서 유입 금지, IDOR 방어).
 * 객실명은 예약 항목 스냅샷용.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReservationRoomOwnershipVO {

	/** 객실일련번호. */
	private Long roomSn;

	/** 고택일련번호. */
	private Long houseSn;

	/** 파트너일련번호(소유권 — 판매자). */
	private Long prtnrSn;

	/** 객실명(스냅샷 대상). */
	private String roomNm;

	/** 객실상태(DRAFT/ACTIVE/HIDDEN/SUSPENDED). */
	private String roomSttsCd;

	/** 고택상태(DRAFT/ACTIVE/...). */
	private String houseSttsCd;
}
