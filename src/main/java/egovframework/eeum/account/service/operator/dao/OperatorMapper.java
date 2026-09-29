package egovframework.eeum.account.service.operator.dao;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import egovframework.eeum.account.vo.OperatorVO;
import egovframework.eeum.account.web.dto.OperatorSearchRequest;

/**
 * 운영자 계정관리 매퍼 — XML: {@code egovframework/mapper/eeum/account/OperatorMapper_mysql.xml}
 *
 * <p>대상은 항상 USER_TYPE_CD='OPERATOR' (비OPERATOR 는 조회/수정에서 제외 — "없는 것처럼" 취급).
 */
@Mapper
public interface OperatorMapper {

	/** 운영자 상세정보(OPERATOR_DTL_INFO, 1:1) 생성. */
	int insertOperatorDtlInfo(@Param("userSn") long userSn,
			@Param("deptNm") String deptNm,
			@Param("jbpsNm") String jbpsNm);

	/**
	 * 운영자 상세정보(부서/직위) 부분수정 — null 인 값은 보존(동적 SET). deptNm/jbpsNm 이 모두 null 이면 호출하지 않는다.
	 * OPERATOR 상세행(미삭제) 한정. 0건이면 대상 상세행 없음.
	 */
	int updateOperatorDtlInfo(@Param("userSn") long userSn,
			@Param("deptNm") String deptNm,
			@Param("jbpsNm") String jbpsNm,
			@Param("mdfrSn") long mdfrSn);

	/**
	 * 운영자 휴대전화번호 저장/변경 — {@code MBL_TELNO}(AES-GCM TypeHandler) + {@code MBL_TELNO_HASH}(HMAC) 를
	 * <b>같은 UPDATE(같은 트랜잭션)</b>로 함께 기록(crypto-standard §2 금지규칙 3 — 암호문·해시 동시성). 대상은
	 * USER_TYPE_CD='OPERATOR' 한정. 생성 직후(계정 insert 후) 및 수정에서 공용. 0건이면 대상 아님.
	 *
	 * @param mblTelno     정규화된 평문(TypeHandler 가 암호화) — null/"" 금지(호출측이 값 있을 때만 호출)
	 * @param mblTelnoHash 정규화 번호의 HMAC-SHA256 hex64
	 */
	int updateOperatorPhone(@Param("userSn") long userSn,
			@Param("mblTelno") String mblTelno,
			@Param("mblTelnoHash") String mblTelnoHash,
			@Param("mdfrSn") long mdfrSn);

	/**
	 * 운영자 계정 조회 — USER_TYPE_CD='OPERATOR' 한정(아니면 null). 상세/검증 진입점.
	 * 행 잠금 없이 조회(상세/존재확인용).
	 */
	OperatorVO selectOperatorByUserSn(@Param("userSn") long userSn);

	/**
	 * 운영자 계정 조회(행 잠금) — 상태/역할 변경·비번초기화 트랜잭션에서 대상행을 직렬화(FOR UPDATE).
	 * USER_TYPE_CD='OPERATOR' 한정. 없으면 null.
	 */
	OperatorVO selectOperatorForUpdate(@Param("userSn") long userSn);

	/** 목록 — USER_TYPE_CD='OPERATOR', 상태/역할 필터 + 서버페이징. */
	List<OperatorVO> selectOperatorList(OperatorSearchRequest request);

	/** 목록 총건수 — selectOperatorList 와 동일 필터. */
	long countOperatorList(OperatorSearchRequest request);

	/** 사용자의 활성 보유 역할코드 목록(USER_ROLE → ROLE_MST, 전부 활성). */
	List<String> selectRoleCdsByUserSn(@Param("userSn") long userSn);

	/** 역할코드 → 역할일련번호(활성·사용중). 없으면 null. */
	Long selectRoleSnByRoleCd(@Param("roleCd") String roleCd);

	/** 사용자-역할 배정(이미 활성으로 있으면 멱등 — XML 이 NOT EXISTS 가드). */
	int insertUserRoleIfAbsent(@Param("userSn") long userSn, @Param("roleSn") long roleSn);

	/** 특정 역할코드의 사용자-역할 활성행 soft delete(역할 회수). 0건이면 보유 안 함. */
	int softDeleteUserRoleByRoleCd(@Param("userSn") long userSn,
			@Param("roleCd") String roleCd,
			@Param("mdfrSn") long mdfrSn);

	/**
	 * 가용(ACTIVE) 수퍼바이저 전원의 USER_SN 을 <b>행 잠금(FOR UPDATE)</b>으로 조회 — 마지막 SUPERVISOR 보호의
	 * race-safe 기반. 서로 다른 두 수퍼바이저를 동시에 강등/비활성/초기화해도, 둘 다 같은 ACTIVE 수퍼바이저 행집합을
	 * 잠그므로 직렬화된다(겹치는 행에서 대기 → 0명 진입 차단). 대상행만 잠그는 방식의 경합 구멍을 막는다(codex MUST).
	 * 조건: USER_TYPE_CD='OPERATOR', ACNT_STTS='ACTIVE', DEL_YN='N', 활성 OPERATOR_SUPERVISOR 역할 보유.
	 */
	List<Long> selectActiveSupervisorUserSnsForUpdate();

	/**
	 * 계정상태 조건부 변경(운영자 한정) — 현재 상태가 기대값이고 USER_TYPE_CD='OPERATOR' 일 때만. 0건 = 전이 불가.
	 */
	int updateOperatorSttsIfCurrent(@Param("userSn") long userSn,
			@Param("expectedStts") String expectedStts,
			@Param("acntStts") String acntStts,
			@Param("mdfrSn") long mdfrSn);

	/**
	 * 비밀번호 초기화 — ACNT_STTS 를 PENDING 으로 되돌리고 ENPSWD=NULL(옛 비밀번호 즉시 무효화). 설정토큰으로
	 * 본인이 새 비밀번호를 설정하면 다시 ACTIVE 가 된다(초기설정과 동일 경로). ACTIVE/PENDING 운영자만 대상
	 * (SUSPENDED 는 먼저 활성화 필요 — 잠금 우회 금지). 0건이면 대상 아님.
	 */
	int updateOperatorResetToPending(@Param("userSn") long userSn, @Param("mdfrSn") long mdfrSn);

	/**
	 * 비밀번호 설정 + 상태 활성화 — ENPSWD 세팅 + ACNT_STTS PENDING→ACTIVE(운영자·미설정 한정).
	 * 0건이면 대상 아님(이미 ACTIVE/탈퇴/비OPERATOR). setup-password 1회성 소비와 짝.
	 */
	int updateOperatorPasswordAndActivate(@Param("userSn") long userSn, @Param("enpswd") String enpswd);
}
