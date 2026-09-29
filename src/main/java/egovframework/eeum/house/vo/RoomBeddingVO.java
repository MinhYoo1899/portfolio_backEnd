package egovframework.eeum.house.vo;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 객실 침구 VO — ROOM_BEDDING 매핑.
 *
 * <p>객실별 침구 타입(COMM_CD BEDDING_TYPE)과 개수. 활성 유니크는 (ROOM_SN, BEDDING_TYPE_CD) — DEL_YN='N'만.
 * 설정은 교체(replace) 방식: 기존 활성 행 soft delete 후 재삽입.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoomBeddingVO {

	/** 객실침구일련번호(PK). */
	private Long roomBeddingSn;

	/** 객실일련번호. */
	private Long roomSn;

	/** 침구타입코드(COMM_CD BEDDING_TYPE). */
	private String beddingTypeCd;

	/** 개수(>=1). */
	private Integer bedCnt;

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
