package egovframework.eeum.account.vo;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

/**
 * 파트너사업자 VO (PRTNR + 계정 일부 조인).
 */
@Getter
@Setter
public class PartnerVO {

	/** 파트너일련번호 */
	private Long prtnrSn;

	/** 사용자일련번호(로그인 계정) */
	private Long userSn;

	/** 상호(사업자명) */
	private String conm;

	/** 사업자등록번호 */
	private String brno;

	/** 대표자명 */
	private String rprsvNm;

	/** 사업자등록번호검증상태 (REQUESTED/VERIFIED/FAILED) */
	private String brnoVrfcStts;

	/** 사업장주소 */
	private String addr;

	/** 사업장상세주소 */
	private String daddr;

	/** 통신판매업신고번호(게시 필수화 권고 — 선택, VARCHAR(40)) */
	private String mailOrderSalesNo;

	/** 담당자명 */
	private String picNm;

	/** 담당자휴대전화번호(암호화 저장 — TypeHandler 로 detail 조회 시 복호화, 응답은 마스킹) */
	private String picMblTelno;

	/** 파트너이메일주소(회사 대표 메일) */
	private String prtnrEmlAddr;

	/** 승인상태 (REQUESTED/APPROVED/REJECTED) */
	private String aprvStts;

	/** 승인자(사용자일련번호) */
	private Long aprvUserSn;

	/** 승인일시(반려 시 반려일시) */
	private LocalDateTime aprvDt;

	/** 반려사유 */
	private String rjctRsn;

	/** 등록일시(신청일시) */
	private LocalDateTime regDt;

	// ---- 계정 조인 컬럼 (USER_ACNT) ----

	/** 로그인 이메일주소 */
	private String emlAddr;

	/** 사용자명(가입자) */
	private String userNm;

	/** 계정상태 */
	private String acntStts;

	// ---- 목록 집계 컬럼 (HOUSE COUNT 서브쿼리 — 비정규화 컬럼 아님, list 전용) ----

	/** 등록 고택 수(미삭제 기준). 목록 조회에서만 채워짐. */
	private Integer houseCnt;
}
