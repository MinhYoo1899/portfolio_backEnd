package egovframework.eeum.house.vo;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 객실 냉난방시설 VO — ROOM_CLIM_FCLTY 매핑.
 *
 * <p>냉방/난방을 단일테이블에서 {@code fcltyDivCd}(COOLING/HEATING)로 구분, 각 구분별 다중선택.
 * {@code fcltyCd}는 COMM_CD(COOLING_TYPE/HEATING_TYPE) 값. 활성 유니크는 (ROOM_SN, FCLTY_DIV_CD, FCLTY_CD) — DEL_YN='N'만.
 * 설정은 교체(replace) 방식: 기존 활성 행 soft delete 후 재삽입.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoomClimateFacilityVO {

	/** 객실냉난방시설일련번호(PK). */
	private Long roomClimFcltySn;

	/** 객실일련번호. */
	private Long roomSn;

	/** 냉난방구분(COOLING/HEATING). */
	private String fcltyDivCd;

	/** 시설코드(COMM_CD COOLING_TYPE/HEATING_TYPE). */
	private String fcltyCd;

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
