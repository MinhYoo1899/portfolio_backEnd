package egovframework.eeum.menu.web;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import egovframework.eeum.auth.vo.AuthUser;
import egovframework.eeum.menu.web.dto.MenuItemResponse;
import egovframework.eeum.menu.web.dto.MenuSearchRequest;
import egovframework.eeum.menu.service.menu.MenuService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import egovframework.eeum.base.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 메뉴 API — SPA 3종의 동적 메뉴 (비로그인 호출 가능, 토큰이 있으면 권한 반영).
 */
@Tag(name = "menu", description = "메뉴 (SPA 동적 메뉴 조회)")
@RestController
@RequestMapping("/menus")
@RequiredArgsConstructor
public class MenuController {

	private final MenuService menuService;

	@Operation(summary = "메뉴 목록", description = "채널(SPA)의 노출 가능 메뉴 평면 목록 — 트리 조립은 프론트. 비로그인=공개 메뉴만, 로그인=권한 보유 메뉴까지. 전량 반환(페이징 없음).")
	@GetMapping("/list")
	public ApiResponse<List<MenuItemResponse>> list(@Valid MenuSearchRequest request,
			@AuthenticationPrincipal AuthUser user) {
		return ApiResponse.ok(menuService.list(request.getMenuChnlCd(), user));
	}
}
