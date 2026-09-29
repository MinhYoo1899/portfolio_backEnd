package egovframework.eeum.house.service.room.dao;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import egovframework.eeum.house.vo.AmenityVO;

/**
 * 객실-편의시설 매핑 매퍼 — XML: {@code egovframework/mapper/eeum/house/RoomAmenityMapper_mysql.xml}
 *
 * <p>소유권은 부모 객실/고택(ROOM JOIN HOUSE.PRTNR_SN)으로 판정 — 변경계는 prtnrSn 가드. 마스터는 AMENITY_MST 재사용.
 * 카탈로그 유효성(활성·범위)은 {@code AmenityMapper.countActiveScopedByIds} 사용.
 */
@Mapper
public interface RoomAmenityMapper {

	/** 객실의 활성 편의시설 목록(AMENITY_MST 조인 — 표출용). 카탈로그 활성(USE_YN='Y') 항목만. */
	List<AmenityVO> selectActiveAmenitiesByRoom(@Param("roomSn") long roomSn);

	/** 객실의 현재 활성 매핑 편의시설 일련번호 집합. */
	List<Long> selectActiveAmenitySnsByRoom(@Param("roomSn") long roomSn);

	/** 매핑 추가(객실 소유권은 서비스가 부모 객실 FOR UPDATE 로 선검증). */
	void insertRoomAmenity(@Param("roomSn") long roomSn, @Param("amenitySn") long amenitySn,
			@Param("rgtrSn") long rgtrSn);

	/** 보존 집합(keepIds)에 없는 활성 매핑 soft delete — ROOM JOIN HOUSE + prtnrSn 가드. keepIds 비면 전부 해제. */
	int softDeleteRoomAmenitiesNotIn(@Param("roomSn") long roomSn, @Param("prtnrSn") long prtnrSn,
			@Param("keepIds") List<Long> keepIds, @Param("mdfrSn") long mdfrSn);
}
