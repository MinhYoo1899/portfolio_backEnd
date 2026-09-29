package egovframework.eeum.house.service.room.dao;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import egovframework.eeum.house.vo.RoomClimateFacilityVO;

/**
 * 객실 냉난방시설 매퍼 — XML: {@code egovframework/mapper/eeum/house/RoomClimateFacilityMapper_mysql.xml}
 *
 * <p>설정은 교체(replace) 방식: {@link #softDeleteActiveByRoom}로 기존 활성 전부 숨김 후 {@link #insertClimateFacility} 재삽입.
 * 소유권은 서비스가 부모 객실/고택으로 선검증 + 변경계는 ROOM JOIN HOUSE + prtnrSn 가드.
 */
@Mapper
public interface RoomClimateFacilityMapper {

	/** 객실의 활성 냉난방시설 목록(FCLTY_DIV_CD, FCLTY_CD 정렬). */
	List<RoomClimateFacilityVO> selectActiveByRoom(@Param("roomSn") long roomSn);

	/** 객실의 활성 냉난방시설 전부 soft delete(교체 전 비움) — ROOM JOIN HOUSE + prtnrSn 가드. */
	int softDeleteActiveByRoom(@Param("roomSn") long roomSn, @Param("prtnrSn") long prtnrSn,
			@Param("mdfrSn") long mdfrSn);

	/** 냉난방시설 1행 삽입 — MDFCN_DT=REG_DT(insert 규칙). */
	void insertClimateFacility(RoomClimateFacilityVO facility);
}
