package egovframework.eeum.region.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 지역(REGION) 조회 VO — 시도/시군구 계층 마스터. (map-underscore-to-camel-case: REGION_CD→regionCd 등)
 *
 * <p>값 비교/필터는 {@code regionCd}(법정동코드 앞자리 — 시도=2자리), 화면 표시는 {@code regionShortNm}(없으면 {@code regionNm}).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RegionVO {

	/** 지역코드(법정동코드 앞자리 — 시도=2자리, 시군구=5자리). 값 비교/필터 기준. */
	private String regionCd;

	/** 상위지역코드(시군구→시도). 시도는 null. */
	private String upperRegionCd;

	/** 지역단계(1=시도, 2=시군구). */
	private Integer regionDepth;

	/** 지역명(공식 — 예: 서울특별시). */
	private String regionNm;

	/** 짧은표시명(예: 강원특별자치도→강원). null 가능. */
	private String regionShortNm;

	/** 정렬순서. */
	private Integer sortSeq;
}
