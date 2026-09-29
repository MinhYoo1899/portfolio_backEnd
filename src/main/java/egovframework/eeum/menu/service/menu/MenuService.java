package egovframework.eeum.menu.service.menu;

import java.util.List;

import egovframework.eeum.auth.vo.AuthUser;
import egovframework.eeum.menu.web.dto.MenuItemResponse;

/**
 * 메뉴 서비스 — SPA 동적 메뉴 조회.
 */
public interface MenuService {

	/**
	 * 채널의 노출 가능 메뉴 평면 목록.
	 * 노출 규칙: 사용중·미삭제 AND (로그인 불요 OR 로그인) AND (권한 불요 OR 보유) — 부모(조상 전체)가 비노출이면 자식도 제외.
	 *
	 * @param menuChnlCd 메뉴채널코드 (USER/PARTNER/OPERATOR)
	 * @param user       인증 사용자 (비로그인 = null)
	 */
	List<MenuItemResponse> list(String menuChnlCd, AuthUser user);
}
