package egovframework.eeum.house.service.room.dao;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import egovframework.eeum.house.vo.RoomBeddingVO;

/**
 * 객실 침구 매퍼 — XML: {@code egovframework/mapper/eeum/house/RoomBeddingMapper_mysql.xml}
 *
 * <p>설정은 교체(replace) 방식: {@link #softDeleteActiveByRoom}로 기존 활성 전부 숨김 후 {@link #insertBedding} 재삽입.
 * 소유권은 서비스가 부모 객실/고택으로 선검증.
 */
@Mapper
public interface RoomBeddingMapper {

	/** 객실의 활성 침구 목록(타입 정렬). */
	List<RoomBeddingVO> selectActiveByRoom(@Param("roomSn") long roomSn);

	/** 객실의 활성 침구 전부 soft delete(교체 전 비움). */
	int softDeleteActiveByRoom(@Param("roomSn") long roomSn, @Param("mdfrSn") long mdfrSn);

	/** 침구 1행 삽입 — MDFCN_DT=REG_DT(insert 규칙). */
	void insertBedding(RoomBeddingVO bedding);
}
