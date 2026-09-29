package egovframework.eeum.code.vo;

import lombok.Getter;

/**
 * 공통코드(COMM_CD) 조회 VO. (map-underscore-to-camel-case: GROUP_CD→groupCd 등)
 */
@Getter
public class CommCdVO {

	/** 그룹코드 */
	private String groupCd;

	/** 코드(값 비교용) */
	private String cd;

	/** 코드명(화면 표시용) */
	private String cdNm;

	/** 정렬순서 */
	private Integer sortSeq;
}
