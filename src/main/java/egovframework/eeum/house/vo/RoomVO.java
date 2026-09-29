package egovframework.eeum.house.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 객실 VO — ROOM 매핑.
 *
 * <p>객실 = 손님이 예약/결제하는 "예약가능 객실유형/상품"(물리 객실 아님).
 * 독채는 ROOM 1개 + {@code dfltStockQty}=1 인 고택. 소유권은 부모 고택(HOUSE.PRTNR_SN)으로 판정.
 * {@code ownerPrtnrSn}(부모 고택 소유 파트너 조인)은 비테이블 — 소유권 검증용.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoomVO {

	/** 객실일련번호(PK). */
	private Long roomSn;

	/** 고택일련번호. */
	private Long houseSn;

	/** 객실명(객실유형명). */
	private String roomNm;

	/** 객실상태(DRAFT/ACTIVE/HIDDEN/SUSPENDED). */
	private String roomSttsCd;

	/** 기준인원. */
	private Integer bscPrsnCnt;

	/** 최대인원. */
	private Integer maxPrsnCnt;

	/** 기본재고수량(같은유형 객실 수). */
	private Integer dfltStockQty;

	/** 최소숙박박수. */
	private Integer minStayNgtCnt;

	/** 객실설명. */
	private String roomDc;

	/** 면적(m²) — 선택. */
	private BigDecimal areaSqm;

	/** 방 개수 — 선택. */
	private Integer roomCnt;

	/** 화장실 개수 — 선택. */
	private Integer bathrmCnt;

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

	/** 부모 고택 소유 파트너(HOUSE.PRTNR_SN 조인). 비테이블 — 소유권 검증용. */
	private Long ownerPrtnrSn;
}
