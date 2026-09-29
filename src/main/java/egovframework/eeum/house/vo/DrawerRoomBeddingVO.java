package egovframework.eeum.house.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 상세 drawer 객실 침구 VO(roomSn IN 배치) — 타입코드 + 표시명(COMM_CD BEDDING_TYPE) + 개수.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DrawerRoomBeddingVO {

	private Long roomSn;
	private String beddingTypeCd;
	private String beddingTypeNm;
	private Integer bedCnt;
}
