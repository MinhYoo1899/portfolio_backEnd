package egovframework.eeum.house.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 객실 이미지 VO — ROOM_IMAGE 매핑. (HOUSE_IMAGE 와 동일 패턴, 부모만 ROOM)
 *
 * <p>소유권은 ROOM→HOUSE.PRTNR_SN 경로로 판정. {@code urlAddr}/{@code orgnlFileNm}은 ATCH_FILE 조인(비테이블).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoomImageVO {

	/** 객실이미지일련번호(PK). */
	private Long roomImgSn;

	/** 객실일련번호. */
	private Long roomSn;

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

	/** 공개URL주소(ATCH_FILE.URL_ADDR 조인). 비테이블 — 표출용. */
	private String urlAddr;

	/** 원본파일명(ATCH_FILE.ORGNL_FILE_NM 조인). 비테이블. */
	private String orgnlFileNm;
}
