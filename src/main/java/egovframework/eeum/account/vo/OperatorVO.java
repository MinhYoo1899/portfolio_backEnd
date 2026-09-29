package egovframework.eeum.account.vo;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

/**
 * 운영자 VO (USER_ACNT + OPERATOR_DTL_INFO 조인 — 운영자 계정관리 조회용).
 *
 * <p>역할(roleCds)은 별도 조회로 채운다(USER_ROLE → ROLE_MST). 보안 불변식(SUPER 보호·역할 표시)에 사용.
 */
@Getter
@Setter
public class OperatorVO {

	/** 사용자일련번호 */
	private Long userSn;

	/** 로그인 이메일주소 */
	private String emlAddr;

	/** 사용자명 */
	private String userNm;

	/** 계정상태 (PENDING/ACTIVE/REJECTED/SUSPENDED/WITHDRAWN) */
	private String acntStts;

	/**
	 * 휴대전화번호 — 조회 시 {@code MBL_TELNO} 를 TypeHandler 로 <b>복호화한 평문</b>(정규화 번호).
	 * 응답 노출 시 목록/상세는 마스킹({@code PhoneMasker}), 본인({@code /operator/me})만 전체 노출.
	 */
	private String mblTelno;

	/** 부서명 */
	private String deptNm;

	/** 직위명 */
	private String jbpsNm;

	/** 2차인증(MFA)사용여부 */
	private String mfaUseYn;

	/** 등록일시 */
	private LocalDateTime regDt;
}
