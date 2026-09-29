package egovframework.eeum.house.vo;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 손님 공개 객실 요약(읽기 전용 프로젝션) — ACTIVE 객실만.
 *
 * <p>내부 재고설정(DFLT_STOCK_QTY)·상태코드·감사컬럼은 담지 않는다. 재고/가격은 가용검색 API 소관.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublicRoomSummaryVO {

	private Long roomSn;
	private String roomNm;
	private Integer bscPrsnCnt;
	private Integer maxPrsnCnt;
	private Integer minStayNgtCnt;  // 날짜 선택 UI 의 사전검증용
	private String roomDc;
	private BigDecimal areaSqm;
	private Integer roomCnt;
	private Integer bathrmCnt;
	private Long rprsAtchFileSn;    // 객실 대표이미지(ROOM_IMAGE RPRS_YN='Y'). 없으면 null
}
