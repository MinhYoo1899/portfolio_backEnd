package egovframework.eeum.auth.vo;

import lombok.Getter;
import lombok.Setter;

/**
 * 인증용 사용자계정 조회 VO (USER_ACNT — 인증에 필요한 컬럼만).
 * MyBatis mapUnderscoreToCamelCase 매핑 대상이라 setter 필요(record 미사용).
 */
@Getter
@Setter
public class AuthAccountVO {

	/** 사용자일련번호 */
	private Long userSn;

	/** 이메일주소(로그인 ID) */
	private String emlAddr;

	/** 암호화비밀번호 (BCrypt — 소셜전용 계정은 NULL) */
	private String enpswd;

	/** 사용자유형코드 */
	private String userTypeCd;

	/** 로그인유형 (EMAIL/SOCIAL/MIXED) */
	private String lgnType;

	/** 계정상태 (PENDING/ACTIVE/REJECTED/SUSPENDED/WITHDRAWN) */
	private String acntStts;

	/** 사용자명 */
	private String userNm;

	/** 별명 */
	private String ncnm;
}
