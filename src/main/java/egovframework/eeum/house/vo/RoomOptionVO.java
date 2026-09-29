package egovframework.eeum.house.vo;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 객실 추가옵션 VO — ROOM_OPTION 매핑.
 *
 * <p>객실별 자유텍스트 옵션명 + 추가금액. 과금단위=예약당 1회 고정. 활성 유니크는 (ROOM_SN, OPTION_NM) — DEL_YN='N'만.
 * 설정은 교체(replace) 방식: 기존 활성 행 soft delete 후 정렬순서대로 재삽입.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoomOptionVO {

	/** 객실추가옵션일련번호(PK). */
	private Long roomOptionSn;

	/** 객실일련번호. */
	private Long roomSn;

	/** 옵션명(자유텍스트). */
	private String optionNm;

	/** 추가금액(원, >=0) — 과금단위=예약당 1회 고정. */
	private Integer addAmt;

	/** 정렬순서(0부터). */
	private Integer sortSeq;

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
