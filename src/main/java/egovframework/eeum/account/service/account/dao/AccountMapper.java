package egovframework.eeum.account.service.account.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import egovframework.eeum.account.vo.AccountVO;

/**
 * 계정 매퍼 — XML: {@code egovframework/mapper/eeum/account/AccountMapper_mysql.xml}
 */
@Mapper
public interface AccountMapper {

	/** 활성(DEL_YN='N') 이메일 존재 여부 (1/0). */
	int existsActiveEmlAddr(@Param("emlAddr") String emlAddr);

	/** 계정 생성 — userSn generated key 수신. */
	int insertAccount(AccountVO account);

	/** 사용자상세정보(USER_DTL_INFO, 1:1) 생성. */
	int insertUserDtlInfo(@Param("userSn") long userSn);

	/**
	 * 프로필 이미지 파일일련번호 조회(행 잠금) — 동시 교체 정합성 위해 트랜잭션 내 SELECT ... FOR UPDATE.
	 * 행이 없으면 null(미존재), 컬럼이 NULL 이면 null(프로필 미설정).
	 */
	Long selectPrflImgFileSnForUpdate(@Param("userSn") long userSn);

	/** 프로필 이미지 파일일련번호 갱신 — 0건이면 대상 없음. */
	int updatePrflImgFileSn(@Param("userSn") long userSn, @Param("prflImgFileSn") long prflImgFileSn);

	/** 프로필 이미지 연결 해제 — PRFL_IMG_FILE_SN 을 NULL 로(탈퇴 시 프로필 파일 정리와 짝). 0건이면 대상 없음. */
	int clearPrflImgFileSn(@Param("userSn") long userSn);

	/** 약관 동의/철회 이력 기록 (append-only). */
	int insertTrmsAgre(@Param("userSn") long userSn,
			@Param("trmsSn") long trmsSn,
			@Param("agreYn") String agreYn,
			@Param("agreChnlCd") String agreChnlCd,
			@Param("cntnIp") String cntnIp,
			@Param("userAgnt") String userAgnt);

	/**
	 * 내 동의 현황 — 약관 유형별 현재 동의상태(유형당 최신 행 1건). 한 번도 동의기록이 없는 유형은 미포함.
	 * 최신 판정 = AGRE_DT DESC, 동률 시 TRMS_AGRE_SN DESC (TRMS_AGRE_INFO 증적 설계와 동일).
	 */
	java.util.List<egovframework.eeum.account.vo.ConsentStatusVO> selectMyConsents(@Param("userSn") long userSn);

	/** 역할코드 → 역할일련번호 (활성·사용중). 없으면 null. */
	Long selectRoleSnByRoleCd(@Param("roleCd") String roleCd);

	/** 사용자-역할 배정. */
	int insertUserRole(@Param("userSn") long userSn, @Param("roleSn") long roleSn);

	/** 소셜 식별자(provider+providerUserId)로 연결된 활성 사용자일련번호 조회 — 없으면 null. */
	Long selectUserSnBySocial(@Param("prvdrCd") String prvdrCd, @Param("prvdrUserId") String prvdrUserId);

	/** 소셜계정 연결 생성(USER_SOCIAL_ACNT). */
	int insertUserSocialAcnt(@Param("userSn") long userSn,
			@Param("prvdrCd") String prvdrCd,
			@Param("prvdrUserId") String prvdrUserId,
			@Param("emlAddr") String emlAddr);

	/** 사용자의 소셜연결 전체 해제(soft delete) — 탈퇴 시 재가입 가능하도록(활성 유니크 해소). */
	int softDeleteSocialByUserSn(@Param("userSn") long userSn);

	/** 활성 계정 조회 (ENPSWD 미포함). */
	AccountVO selectAccountByUserSn(@Param("userSn") long userSn);

	/** 본인확인용 암호화비밀번호 조회 (탈퇴·비밀번호 변경 등 — 이 용도 외 사용 금지). */
	String selectEnpswdByUserSn(@Param("userSn") long userSn);

	/** 비밀번호(ENPSWD) 갱신 — 0건이면 대상 없음. */
	int updateEnpswd(@Param("userSn") long userSn, @Param("enpswd") String enpswd);

	/**
	 * 이메일검증 완료 처리 — EML_VRFD_YN='Y', EML_VRFD_DT=now(UTC), EML_VRFC_SRC 세팅.
	 * 활성·미검증(EML_VRFD_YN='N') 계정에만 적용. 이미 검증이면 0건(멱등).
	 */
	int updateEmailVerified(@Param("userSn") long userSn, @Param("emlVrfcSrc") String emlVrfcSrc);

	/** 본인 정보 수정(이름/별명). */
	int updateAccount(@Param("userSn") long userSn,
			@Param("userNm") String userNm,
			@Param("ncnm") String ncnm);

	/** 탈퇴 — soft delete + WHDWL_DT + ACNT_STTS=WITHDRAWN. */
	int withdrawAccount(@Param("userSn") long userSn);

	/**
	 * 계정상태 조건부 변경 — 현재 상태가 기대값일 때만 (전이 정합성, 0건 = 전이 불가).
	 * (파트너 승인/반려 등 — 같은 도메인 내 공용)
	 */
	int updateAcntSttsIfCurrent(@Param("userSn") long userSn,
			@Param("expectedStts") String expectedStts,
			@Param("acntStts") String acntStts,
			@Param("mdfrSn") long mdfrSn);
}
