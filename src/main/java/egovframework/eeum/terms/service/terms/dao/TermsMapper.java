package egovframework.eeum.terms.service.terms.dao;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import egovframework.eeum.terms.vo.TrmsVO;

/**
 * 약관 매퍼 — XML: {@code egovframework/mapper/eeum/terms/TermsMapper_mysql.xml}
 */
@Mapper
public interface TermsMapper {

	/** 시행 중 약관 — 유형당 최신 시행버전 1건(본문 미포함). 가입 검증·목록 공용. */
	List<TrmsVO> selectEffectiveTerms();

	/** 시행 중 약관 상세 — 본문(TRMS_CN) 포함. 시행중·사용중·미삭제만. 없으면 null. */
	TrmsVO selectEffectiveTermsDetail(@Param("trmsSn") long trmsSn);
}
