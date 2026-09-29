package egovframework.eeum.content.service.popup.dao;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import egovframework.eeum.content.vo.ContentPopupVO;

/**
 * 콘텐츠 팝업 매퍼 — CONTENT_POPUP CRUD(+토글, soft delete). 목록은 노출위치/제목 필터(선택) + SN DESC.
 */
@Mapper
public interface ContentPopupMapper {

	/** 등록 — useGeneratedKeys 로 contentPopupSn 채움. REG_DT=MDFCN_DT=UTC_TIMESTAMP(6). */
	int insertPopup(ContentPopupVO vo);

	/** 목록(미삭제) — 노출위치/제목 필터(선택), CONTENT_POPUP_SN DESC. */
	List<ContentPopupVO> selectPopupList(@Param("exposeLocationCd") String exposeLocationCd,
			@Param("keyword") String keyword);

	/** 단건(미삭제). 없으면 null. */
	ContentPopupVO selectPopupById(@Param("contentPopupSn") long contentPopupSn);

	/** 전체 교체 수정(미삭제). 영향행 0 = 없음. */
	int updatePopup(ContentPopupVO vo);

	/** 노출 토글(미삭제). 영향행 0 = 없음. */
	int updatePopupExposeYn(@Param("contentPopupSn") long contentPopupSn,
			@Param("exposeYn") String exposeYn, @Param("mdfrSn") long mdfrSn);

	/** soft delete(DEL_YN='Y', 미삭제만). 영향행 0 = 없음. */
	int softDeletePopup(@Param("contentPopupSn") long contentPopupSn, @Param("mdfrSn") long mdfrSn);

	/** linkTarget=PRODUCT 검증용 — 존재+미삭제 고택 수(0=없음). */
	int selectActiveHouseExists(@Param("houseSn") long houseSn);
}
