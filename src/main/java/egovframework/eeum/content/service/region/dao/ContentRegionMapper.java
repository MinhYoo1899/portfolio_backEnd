package egovframework.eeum.content.service.region.dao;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import egovframework.eeum.content.vo.ContentRegionVO;

/**
 * 콘텐츠 즐길거리 매퍼 — CONTENT_REGION CRUD(+토글, soft delete). 목록은 카테고리/유형/지역/제목 필터 + 오프셋 페이지네이션.
 * 조회수/댓글수(VIEW_CNT/CMNT_CNT)는 등록/수정 대상 아님(읽기전용 — 수정 시 보존).
 */
@Mapper
public interface ContentRegionMapper {

	/** 등록 — useGeneratedKeys 로 contentRegionSn 채움. REG_DT=MDFCN_DT=UTC_TIMESTAMP(6). VIEW/CMNT_CNT=0(default). */
	int insertRegion(ContentRegionVO vo);

	/** 목록(미삭제) — 필터 + LIMIT/OFFSET, SN DESC. */
	List<ContentRegionVO> selectRegionList(@Param("categoryCd") String categoryCd, @Param("typeCd") String typeCd,
			@Param("sidoRegionCd") String sidoRegionCd, @Param("keyword") String keyword,
			@Param("offset") int offset, @Param("limit") int limit);

	/** 목록 총건수(미삭제, 동일 필터). */
	long countRegion(@Param("categoryCd") String categoryCd, @Param("typeCd") String typeCd,
			@Param("sidoRegionCd") String sidoRegionCd, @Param("keyword") String keyword);

	/** 단건(미삭제). 없으면 null. */
	ContentRegionVO selectRegionById(@Param("contentRegionSn") long contentRegionSn);

	/** 전체 교체 수정(미삭제) — VIEW_CNT/CMNT_CNT 보존. 영향행 0 = 없음. */
	int updateRegion(ContentRegionVO vo);

	/** 활성 토글(미삭제). 영향행 0 = 없음. */
	int updateRegionExposeYn(@Param("contentRegionSn") long contentRegionSn,
			@Param("exposeYn") String exposeYn, @Param("mdfrSn") long mdfrSn);

	/** soft delete(DEL_YN='Y', 미삭제만). 영향행 0 = 없음. */
	int softDeleteRegion(@Param("contentRegionSn") long contentRegionSn, @Param("mdfrSn") long mdfrSn);
}
