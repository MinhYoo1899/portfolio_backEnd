package egovframework.eeum.account.service.member.dao;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import egovframework.eeum.account.vo.AccountVO;

/**
 * 일반회원 관리 매퍼 — XML: {@code egovframework/mapper/eeum/account/MemberMapper_mysql.xml}
 *
 * <p>모든 쿼리는 {@code USER_TYPE_CD='MEMBER' AND DEL_YN='N'} 고정(운영자/파트너/마스터 계정 격리 — 권한상승/오조작 차단).
 */
@Mapper
public interface MemberMapper {

	/** 회원 목록(오프셋 페이징, REG_DT DESC). status/keyword 동적. */
	List<AccountVO> selectMemberListOffset(@Param("status") String status, @Param("keyword") String keyword,
			@Param("limit") int limit, @Param("offset") int offset);

	/** 회원 목록 총건수(동일 필터). */
	long countMemberList(@Param("status") String status, @Param("keyword") String keyword);

	/** 회원 상세 — 없으면 null(비MEMBER/삭제 포함, 열거 방지 404). */
	AccountVO selectMemberDetail(@Param("userSn") long userSn);

	/** 상태변경 선잠금(FOR UPDATE) — MEMBER + 미삭제일 때만. */
	AccountVO selectMemberForUpdate(@Param("userSn") long userSn);

	/** 상태변경 — MEMBER + 미삭제일 때만(계정유형 2중가드). */
	int updateMemberStatus(@Param("userSn") long userSn, @Param("acntStts") String acntStts,
			@Param("mdfrSn") long mdfrSn);
}
