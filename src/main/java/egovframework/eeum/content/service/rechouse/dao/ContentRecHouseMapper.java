package egovframework.eeum.content.service.rechouse.dao;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import egovframework.eeum.content.vo.ContentRecHouseVO;

/**
 * 콘텐츠 추천고택 매퍼 — CONTENT_REC_HOUSE CRUD(+토글, 순서저장, soft delete). 목록은 HOUSE 조인(고택명/주소) + 분류필터.
 */
@Mapper
public interface ContentRecHouseMapper {

	/** 등록 — useGeneratedKeys 로 contentRecHouseSn 채움. REG_DT=MDFCN_DT=UTC_TIMESTAMP(6). */
	int insertRecHouse(ContentRecHouseVO vo);

	/** 목록(미삭제) — 분류필터(선택), SORT_ORDER ASC, SN DESC. HOUSE 조인(고택명/주소). */
	List<ContentRecHouseVO> selectRecHouseList(@Param("recCategoryCd") String recCategoryCd);

	/** 단건(미삭제). 없으면 null. */
	ContentRecHouseVO selectRecHouseById(@Param("contentRecHouseSn") long contentRecHouseSn);

	/** 전체 교체 수정(미삭제) — SORT_ORDER 는 별도(순서저장). 영향행 0 = 없음. */
	int updateRecHouse(ContentRecHouseVO vo);

	/** 노출 토글(미삭제). 영향행 0 = 없음. */
	int updateRecHouseExposeYn(@Param("contentRecHouseSn") long contentRecHouseSn,
			@Param("exposeYn") String exposeYn, @Param("mdfrSn") long mdfrSn);

	/** 순서 저장(미삭제) — 단건 SORT_ORDER 갱신(서비스가 배열 루프). 영향행 0 = 없음. */
	int updateRecHouseSortOrder(@Param("contentRecHouseSn") long contentRecHouseSn,
			@Param("sortOrder") int sortOrder, @Param("mdfrSn") long mdfrSn);

	/** soft delete(DEL_YN='Y', 미삭제만). 영향행 0 = 없음. */
	int softDeleteRecHouse(@Param("contentRecHouseSn") long contentRecHouseSn, @Param("mdfrSn") long mdfrSn);

	/** houseId 검증용 — 존재+미삭제 고택 수(0=없음). */
	int selectActiveHouseExists(@Param("houseSn") long houseSn);
}
