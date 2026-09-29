package egovframework.eeum.menu.service.menu.dao;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import egovframework.eeum.menu.vo.MenuVO;

/**
 * 메뉴 매퍼 — XML: {@code egovframework/mapper/eeum/menu/MenuMapper_mysql.xml}
 */
@Mapper
public interface MenuMapper {

	/** 채널의 사용중(USE_YN='Y')·활성(DEL_YN='N') 메뉴 전체 — 정렬: 상위→정렬순서→SN. 권한/로그인 필터는 서비스 담당. */
	List<MenuVO> selectActiveMenus(@Param("menuChnlCd") String menuChnlCd);
}
