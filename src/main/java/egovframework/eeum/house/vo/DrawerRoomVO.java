package egovframework.eeum.house.vo;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 상세 drawer 객실 기본 VO — 활성 객실 1행 + 대표요금(창 내 sellable 최저가).
 *
 * <p>{@code repPriceFrom} = 조회 창에서 CLOSED_YN='N' AND PRICE_AMT&gt;0 인 밤의 MIN(PRICE_AMT)(상관 서브쿼리),
 * 판매 가능 밤이 없으면 null. 침구/편의시설/이미지는 roomSn IN 배치로 별도 조회 후 서비스에서 결합.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DrawerRoomVO {

	private Long roomSn;
	private String roomNm;
	private String roomSttsCd;
	private Integer bscPrsnCnt;
	private Integer maxPrsnCnt;
	private String roomDc;
	private BigDecimal areaSqm;
	private Integer roomCnt;
	private Integer bathrmCnt;
	private Integer repPriceFrom;
}
