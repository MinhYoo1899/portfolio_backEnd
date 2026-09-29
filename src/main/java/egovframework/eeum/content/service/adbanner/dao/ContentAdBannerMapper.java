package egovframework.eeum.content.service.adbanner.dao;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import egovframework.eeum.content.vo.ContentAdBannerVO;

/**
 * 콘텐츠 광고배너 매퍼 — CONTENT_AD_BANNER CRUD(+토글, soft delete).
 * 목록은 노출위치 코드(AD_BANNER_POSITION) SORT_SEQ ASC, SN DESC 결정적 정렬.
 */
@Mapper
public interface ContentAdBannerMapper {

	/** 등록 — useGeneratedKeys 로 contentAdBannerSn 채움. REG_DT=MDFCN_DT=UTC_TIMESTAMP(6). */
	int insertAdBanner(ContentAdBannerVO vo);

	/** 목록(미삭제) — 노출위치 SORT_SEQ ASC, CONTENT_AD_BANNER_SN DESC. */
	List<ContentAdBannerVO> selectAdBannerList();

	/** 단건(미삭제). 없으면 null. */
	ContentAdBannerVO selectAdBannerById(@Param("contentAdBannerSn") long contentAdBannerSn);

	/** 전체 교체 수정(미삭제) — EXPOSE_YN 은 건드리지 않음(보존). 영향행 0 = 없음. */
	int updateAdBanner(ContentAdBannerVO vo);

	/** 노출 토글(미삭제). 영향행 0 = 없음. */
	int updateAdBannerExposeYn(@Param("contentAdBannerSn") long contentAdBannerSn,
			@Param("exposeYn") String exposeYn, @Param("mdfrSn") long mdfrSn);

	/** soft delete(DEL_YN='Y', 미삭제만). 영향행 0 = 없음. */
	int softDeleteAdBanner(@Param("contentAdBannerSn") long contentAdBannerSn, @Param("mdfrSn") long mdfrSn);

	/** linkTarget=PRODUCT 검증용 — 존재+미삭제 고택 수(0=없음). */
	int selectActiveHouseExists(@Param("houseSn") long houseSn);
}
