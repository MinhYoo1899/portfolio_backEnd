package egovframework.eeum.house.service.house.dao;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import egovframework.eeum.house.vo.HousePayMethodVO;

/**
 * 고택 결제수단 매퍼 — XML: {@code egovframework/mapper/eeum/house/HousePayMethodMapper_mysql.xml}
 *
 * <p>설정은 교체(replace) 방식: {@link #softDeleteActiveByHouse}로 기존 활성 전부 숨김 후 {@link #insertPayMethod} 재삽입.
 * 소유권은 서비스가 부모 고택 FOR UPDATE 로 선검증.
 */
@Mapper
public interface HousePayMethodMapper {

	/** 고택의 활성 결제수단 코드 목록(SORT_SEQ 순). 상세 표출/발행검증용. */
	List<String> selectActiveCodesByHouse(@Param("houseSn") long houseSn);

	/** 고택의 활성 결제수단 행 전부 soft delete(교체 전 비움). */
	int softDeleteActiveByHouse(@Param("houseSn") long houseSn, @Param("mdfrSn") long mdfrSn);

	/** 결제수단 1행 삽입 — MDFCN_DT=REG_DT(insert 규칙). */
	void insertPayMethod(HousePayMethodVO payMethod);
}
