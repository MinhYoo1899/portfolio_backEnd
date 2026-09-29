package egovframework.eeum.content.service.banner.dao;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import egovframework.eeum.content.vo.ContentMainBannerVO;

/**
 * 콘텐츠 메인배너 매퍼 — CONTENT_MAIN_BANNER CRUD(+토글). 삭제 API는 현단계 제외(soft delete 컬럼만 예약).
 */
@Mapper
public interface ContentBannerMapper {

	/** 등록 — useGeneratedKeys 로 contentMainBannerSn 채움. REG_DT=MDFCN_DT=UTC_TIMESTAMP(6). */
	int insertBanner(ContentMainBannerVO vo);

	/** 목록(미삭제) — SORT_ORDER ASC, CONTENT_MAIN_BANNER_SN DESC. */
	List<ContentMainBannerVO> selectBannerList();

	/** 단건(미삭제). 없으면 null. */
	ContentMainBannerVO selectBannerById(@Param("contentMainBannerSn") long contentMainBannerSn);

	/** 전체 교체 수정(미삭제). 영향행 0 = 없음. */
	int updateBanner(ContentMainBannerVO vo);

	/** 노출 토글(미삭제). 영향행 0 = 없음. */
	int updateBannerExposeYn(@Param("contentMainBannerSn") long contentMainBannerSn,
			@Param("exposeYn") String exposeYn, @Param("mdfrSn") long mdfrSn);

	/** linkTarget=PRODUCT 검증용 — 존재+미삭제 고택 수(0=없음). */
	int selectActiveHouseExists(@Param("houseSn") long houseSn);
}
