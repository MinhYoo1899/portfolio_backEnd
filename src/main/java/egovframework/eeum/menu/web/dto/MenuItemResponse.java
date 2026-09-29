package egovframework.eeum.menu.web.dto;

import egovframework.eeum.menu.vo.MenuVO;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 메뉴 항목 응답 DTO — 평면 목록(트리 조립은 프론트 담당).
 *
 * @param menuSn      메뉴일련번호 (문자열 — ID 규약)
 * @param upperMenuSn 상위메뉴일련번호 (null=최상위)
 * @param menuNm      메뉴명
 * @param urlAddr     SPA 라우트 경로 (null=폴더형 그룹메뉴)
 * @param iconNm      아이콘 키
 * @param sortSeq     정렬순서(같은 부모 내)
 */
@Schema(description = "메뉴 항목 (평면 목록 — 트리 조립은 프론트)")
public record MenuItemResponse(
		@Schema(description = "메뉴일련번호(문자열)", example = "1") String menuSn,
		@Schema(description = "상위메뉴일련번호(null=최상위)", example = "null") String upperMenuSn,
		@Schema(description = "메뉴명") String menuNm,
		@Schema(description = "SPA 라우트 경로(null=폴더형)") String urlAddr,
		@Schema(description = "아이콘 키") String iconNm,
		@Schema(description = "정렬순서") int sortSeq) {

	public static MenuItemResponse from(MenuVO vo) {
		return new MenuItemResponse(
				String.valueOf(vo.getMenuSn()),
				vo.getUpperMenuSn() == null ? null : String.valueOf(vo.getUpperMenuSn()),
				vo.getMenuNm(),
				vo.getUrlAddr(),
				vo.getIconNm(),
				vo.getSortSeq() == null ? 0 : vo.getSortSeq());
	}
}
