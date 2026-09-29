package egovframework.eeum.house.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 객실 가용성 집계 VO — 가용성 조회 쿼리 결과(비테이블).
 *
 * <p>요청 기간의 "판매가능 밤 수(sellableNights)"와 "가격 합계"를 객실별로 집계. 실제 가용 판정(밤 수 일치·인원·최소숙박)은 서비스가 한다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoomAvailabilityVO {

	/** 객실일련번호. */
	private Long roomSn;

	/** 객실명. */
	private String roomNm;

	/** 기준인원. */
	private Integer bscPrsnCnt;

	/** 최대인원. */
	private Integer maxPrsnCnt;

	/** 최소숙박박수. */
	private Integer minStayNgtCnt;

	/** 요청 기간 중 판매가능한 밤 수(마감X·재고있음·가격있음). */
	private Integer sellableNights;

	/** 판매가능 밤들의 가격 합계(KRW). */
	private Long totalPrice;
}
