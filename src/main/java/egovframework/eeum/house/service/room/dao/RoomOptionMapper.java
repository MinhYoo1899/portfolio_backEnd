package egovframework.eeum.house.service.room.dao;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import egovframework.eeum.house.vo.RoomOptionVO;

/**
 * 객실 추가옵션 매퍼 — XML: {@code egovframework/mapper/eeum/house/RoomOptionMapper_mysql.xml}
 *
 * <p>설정은 교체(replace) 방식: {@link #softDeleteActiveByRoom}로 기존 활성 전부 숨김 후 {@link #insertOption} 재삽입.
 * 소유권은 서비스가 부모 객실/고택으로 선검증 + 변경계는 ROOM JOIN HOUSE + prtnrSn 가드.
 */
@Mapper
public interface RoomOptionMapper {

	/** 객실의 활성 추가옵션 목록(SORT_SEQ 정렬). */
	List<RoomOptionVO> selectActiveByRoom(@Param("roomSn") long roomSn);

	/** 객실의 활성 옵션 전부 soft delete(교체 전 비움) — ROOM JOIN HOUSE + prtnrSn 가드. */
	int softDeleteActiveByRoom(@Param("roomSn") long roomSn, @Param("prtnrSn") long prtnrSn,
			@Param("mdfrSn") long mdfrSn);

	/** 옵션 1행 삽입 — MDFCN_DT=REG_DT(insert 규칙). */
	void insertOption(RoomOptionVO option);
}
