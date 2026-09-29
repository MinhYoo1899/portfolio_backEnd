package egovframework.eeum.account.vo;

import lombok.Getter;
import lombok.Setter;

/**
 * 사용자계정 VO (USER_ACNT — 계정 도메인 공용).
 * 조회 시 ENPSWD 는 매핑하지 않는다(인증 매퍼 전용 원칙). INSERT 시에만 enpswd 사용.
 */
@Getter
@Setter
public class AccountVO {

	/** 사용자일련번호 (INSERT 후 generated key 수신) */
	private Long userSn;

	/** 이메일주소(로그인 ID) */
	private String emlAddr;

	/** 암호화비밀번호 (INSERT 전용 — SELECT 매핑 금지) */
	private String enpswd;

	/** 사용자유형코드 (공통코드 USER_TYPE) */
	private String userTypeCd;

	/** 로그인유형 (EMAIL/SOCIAL/MIXED) */
	private String lgnType;

	/** 계정상태 (PENDING/ACTIVE/REJECTED/SUSPENDED/WITHDRAWN) */
	private String acntStts;

	/** 사용자명 */
	private String userNm;

	/** 별명(닉네임) */
	private String ncnm;

	/** 휴대전화번호(암호화) — INSERT 시 MblTelnoEncryptedTypeHandler 로 AES-GCM 암호화(정규화 평문 입력). 운영자 계정에서 사용. */
	private String mblTelno;

	/** 휴대전화번호 검색해시(HMAC-SHA256 hex64, 정규화 번호 기준) — INSERT 시 암호문과 함께 기록(crypto-standard §3). */
	private String mblTelnoHash;

	/** 휴대전화번호인증여부 */
	private String mblTelnoVrfcYn;

	/** 이메일검증여부(Y/N) — ACNT_STTS와 분리된 이메일 소유확인 상태 */
	private String emlVrfdYn;

	/** 이메일검증완료일시(UTC) */
	private java.time.LocalDateTime emlVrfdDt;

	/** 이메일검증출처(EMAIL_CODE/GOOGLE/ADMIN/LEGACY_ASSUMED) */
	private String emlVrfcSrc;

	/** 등록일시 */
	private java.time.LocalDateTime regDt;
}
