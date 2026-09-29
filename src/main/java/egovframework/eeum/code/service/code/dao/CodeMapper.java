package egovframework.eeum.code.service.code.dao;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import egovframework.eeum.code.vo.CommCdVO;

/**
 * 공통코드 매퍼 — XML: {@code egovframework/mapper/eeum/code/CodeMapper_mysql.xml}
 */
@Mapper
public interface CodeMapper {

	/** 그룹의 사용중(USE_YN='Y')·활성(DEL_YN='N') 공통코드 — 정렬순서 순. */
	List<CommCdVO> selectActiveCodesByGroup(@Param("groupCd") String groupCd);
}
