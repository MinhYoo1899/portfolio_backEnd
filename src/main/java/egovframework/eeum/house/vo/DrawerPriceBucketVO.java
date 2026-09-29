package egovframework.eeum.house.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 상세 drawer 요일 요금 버킷 VO — 하우스 단위 sellable 밤(CLOSED_YN='N' AND PRICE_AMT&gt;0)을
 * 요일버킷(WEEKDAY=일~목 / FRI / SAT)으로 집계한 MIN/MAX 가격. SOLD_QTY 무관.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DrawerPriceBucketVO {

	/** WEEKDAY / FRI / SAT. */
	private String bucket;
	private Integer minPrice;
	private Integer maxPrice;
}
