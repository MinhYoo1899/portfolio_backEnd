package egovframework.eeum.terms.vo;

import lombok.Getter;
import lombok.Setter;

/**
 * 약관 VO (TRMS — 약관 조회 + 가입 시 필수약관 검증/동의 기록용).
 */
@Getter
@Setter
public class TrmsVO {

	/** 약관일련번호 */
	private Long trmsSn;

	/** 약관유형코드 (공통코드 TRMS_TYPE) */
	private String trmsTypeCd;

	/** 버전번호 */
	private String verNo;

	/** 약관제목 */
	private String trmsTtl;

	/** 필수여부 */
	private String esntlYn;

	/** 약관본문 (MEDIUMTEXT) — 목록 조회는 null, 상세 조회만 채움. */
	private String trmsCn;
}
