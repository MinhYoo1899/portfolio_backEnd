package egovframework.eeum.house.service.amenity.dao;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import egovframework.eeum.house.vo.AmenityVO;

/**
 * 고택-편의시설 매핑 매퍼 — XML: {@code egovframework/mapper/eeum/house/HouseAmenityMapper_mysql.xml}
 *
 * <p>소유권은 부모 고택(HOUSE.PRTNR_SN)으로 판정 — 변경계는 JOIN HOUSE + prtnrSn 가드.
 */
@Mapper
public interface HouseAmenityMapper {

	/** 고택의 활성 편의시설 목록(AMENITY_MST 조인 — 표출용). 사용중 항목만. */
	List<AmenityVO> selectActiveAmenitiesByHouse(@Param("houseSn") long houseSn);

	/** 고택의 현재 활성 매핑 편의시설 일련번호 집합. */
	List<Long> selectActiveAmenitySnsByHouse(@Param("houseSn") long houseSn);

	/** 매핑 추가(고택 소유권은 서비스가 고택 FOR UPDATE 로 선검증). */
	void insertHouseAmenity(@Param("houseSn") long houseSn, @Param("amenitySn") long amenitySn,
			@Param("rgtrSn") long rgtrSn);

	/** 보존 집합(keepIds)에 없는 활성 매핑 soft delete — JOIN HOUSE + prtnrSn 가드. keepIds 비면 전부 해제. */
	int softDeleteHouseAmenitiesNotIn(@Param("houseSn") long houseSn, @Param("prtnrSn") long prtnrSn,
			@Param("keepIds") List<Long> keepIds, @Param("mdfrSn") long mdfrSn);
}
