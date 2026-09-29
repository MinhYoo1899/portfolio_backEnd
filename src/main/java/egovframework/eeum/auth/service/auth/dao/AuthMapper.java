package egovframework.eeum.auth.service.auth.dao;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import egovframework.eeum.auth.vo.AuthAccountVO;
import egovframework.eeum.auth.vo.LoginLogInfoVO;

/**
 * 인증 매퍼 — XML: {@code egovframework/mapper/eeum/auth/AuthMapper_mysql.xml}
 * (멀티 DB 대비 vendor suffix 패턴 — EgovConfigAppMapper 스캔 경로)
 */
@Mapper
public interface AuthMapper {

	/** 활성(DEL_YN='N') 계정을 이메일로 조회. */
	AuthAccountVO selectActiveAccountByEmlAddr(@Param("emlAddr") String emlAddr);

	/** 활성 계정을 사용자일련번호로 조회 (refresh 재발급 시 상태 재확인용). */
	AuthAccountVO selectActiveAccountByUserSn(@Param("userSn") long userSn);

	/**
	 * 사용자의 권한코드 목록 (USER_ROLE → ROLE → ROLE_AUTH → AUTH, 전부 활성·사용중).
	 * ※ 역할 계층(UPPER_ROLE_SN) 상속은 M1 범위 외 — 직접 배정 역할만 평가(추후 확장).
	 */
	List<String> selectAuthCdsByUserSn(@Param("userSn") long userSn);

	/** 최종로그인일시 갱신. */
	int updateLastLgnDt(@Param("userSn") long userSn);

	/** 로그인 로그 기록 (성공/실패 모두). */
	int insertLoginLogInfo(LoginLogInfoVO loginLog);
}
