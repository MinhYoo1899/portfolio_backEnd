package egovframework.eeum.menu.service.menu.impl;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import egovframework.eeum.auth.vo.AuthUser;
import egovframework.eeum.menu.web.dto.MenuItemResponse;
import egovframework.eeum.menu.vo.MenuVO;
import egovframework.eeum.menu.service.menu.MenuService;
import egovframework.eeum.menu.service.menu.dao.MenuMapper;
import lombok.RequiredArgsConstructor;

/**
 * 메뉴 서비스 구현.
 *
 * <p>원본 메뉴는 DB(추후 트래픽 증가 시 Redis 캐시 — redis-strategy.md cache:menu 예약),
 * 사용자별 필터(로그인/권한)는 요청마다 수행한다(codex 자문).
 */
@Service
@RequiredArgsConstructor
public class MenuServiceImpl implements MenuService {

	private final MenuMapper menuMapper;

	@Override
	@Transactional(readOnly = true)
	public List<MenuItemResponse> list(String menuChnlCd, AuthUser user) {
		List<MenuVO> menus = menuMapper.selectActiveMenus(menuChnlCd);

		// 1) 개별 노출 조건: (로그인 불요 OR 로그인) AND (권한 불요 OR 보유)
		List<MenuVO> visible = menus.stream()
				.filter(m -> "N".equals(m.getLgnNeedYn()) || user != null)
				.filter(m -> m.getAuthCd() == null
						|| (user != null && user.permissions().contains(m.getAuthCd())))
				.toList();

		// 2) 고아 제거 — 조상 체인 전체가 노출 가능해야 함 (부모 제거 시 손자까지 연쇄 제거, codex 자문)
		Set<Long> visibleSns = new HashSet<>();
		visible.forEach(m -> visibleSns.add(m.getMenuSn()));
		boolean removed = true;
		while (removed) {
			removed = false;
			for (MenuVO m : visible) {
				if (visibleSns.contains(m.getMenuSn())
						&& m.getUpperMenuSn() != null
						&& !visibleSns.contains(m.getUpperMenuSn())) {
					visibleSns.remove(m.getMenuSn());
					removed = true;
				}
			}
		}

		return visible.stream()
				.filter(m -> visibleSns.contains(m.getMenuSn()))
				.map(MenuItemResponse::from)
				.toList();
	}
}
