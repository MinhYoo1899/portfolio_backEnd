package egovframework.eeum.house.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 고택 이미지 VO — HOUSE_IMAGE 매핑.
 *
 * <p>이미지 파일 자체는 ATCH_FILE(공개 LINKED)이 보관하고, 정렬·대표·삭제상태는 이 테이블이 책임진다(논리참조).
 * {@code ownerPrtnrSn}(부모 고택 소유자 조인)·{@code urlAddr}/{@code orgnlFileNm}(ATCH_FILE 조인)은 비테이블.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HouseImageVO {

	/** 고택이미지일련번호(PK). */
	private Long houseImgSn;

	/** 고택일련번호. */
	private Long houseSn;

	/** 첨부파일일련번호(ATCH_FILE 논리참조). */
	private Long atchFileSn;

	/** 정렬순서. */
	private Integer sortSeq;

	/** 대표(썸네일)여부. */
	private String rprsYn;

	/** 삭제여부. */
	private String delYn;

	/** 등록자. */
	private Long rgtrSn;

	/** 수정자. */
	private Long mdfrSn;

	/** 부모 고택 소유 파트너(HOUSE.PRTNR_SN 조인). 비테이블 — 소유권 검증용. */
	private Long ownerPrtnrSn;

	/** 공개URL주소(ATCH_FILE.URL_ADDR 조인). 비테이블 — 표출용. */
	private String urlAddr;

	/** 원본파일명(ATCH_FILE.ORGNL_FILE_NM 조인). 비테이블. */
	private String orgnlFileNm;
}
