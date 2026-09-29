package egovframework.eeum.house.service.publichouse.dao;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import egovframework.eeum.house.vo.PublicHouseDetailVO;
import egovframework.eeum.house.vo.PublicHouseListVO;
import egovframework.eeum.house.vo.PublicRoomSummaryVO;

/**
 * 손님 공개 고택 조회 매퍼 — 공개 가시성(publicVisibleWhere: ACTIVE 고택 + 승인·활성 파트너)만(소유권 무관, 공개).
 */
@Mapper
public interface PublicHouseMapper {

	/** 공개 고택 총 건수(키워드 필터). */
	long countPublicHouses(@Param("keyword") String keyword);

	/** 공개 고택 목록(오프셋 페이징, 최신순). */
	List<PublicHouseListVO> selectPublicHouseListOffset(@Param("keyword") String keyword,
			@Param("limit") int limit, @Param("offset") int offset);

	/** 공개 고택 상세(가시성 통과 행만 — 비공개/미승인은 null → 서비스가 404 은닉). */
	PublicHouseDetailVO selectPublicHouseDetail(@Param("houseSn") long houseSn);

	/** 공개 객실 요약(ACTIVE 객실만 — 파트너 관리조회와 별도 SELECT). */
	List<PublicRoomSummaryVO> selectPublicRoomsByHouse(@Param("houseSn") long houseSn);

	/** 공개 가시성 존재 확인(가용검색 404 은닉 선체크 — 상세와 동일 기준). */
	int countPublicVisibleHouse(@Param("houseSn") long houseSn);
}
