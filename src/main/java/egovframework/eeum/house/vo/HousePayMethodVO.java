package egovframework.eeum.house.vo;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 고택 결제수단 VO — HOUSE_PAY_MTHD 매핑.
 *
 * <p>고택별 결제수단(COMM_CD PAY_MTHD)과 정렬순서. 활성 유니크는 (HOUSE_SN, PAY_MTHD_CD) — DEL_YN='N'만.
 * 설정은 교체(replace) 방식: 기존 활성 행 soft delete 후 재삽입.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HousePayMethodVO {

	/** 고택결제수단일련번호(PK). */
	private Long housePayMthdSn;

	/** 고택일련번호. */
	private Long houseSn;

	/** 결제수단코드(COMM_CD PAY_MTHD). */
	private String payMthdCd;

	/** 정렬순서. */
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
