package egovframework.eeum.menu.vo;

import lombok.Getter;
import lombok.Setter;

/**
 * 메뉴 VO (MENU_INFO).
 */
@Getter
@Setter
public class MenuVO {

	/** 메뉴일련번호 */
	private Long menuSn;

	/** 메뉴채널코드 (USER/PARTNER/OPERATOR) */
	private String menuChnlCd;

	/** 상위메뉴일련번호 (NULL=최상위) */
	private Long upperMenuSn;

	/** 메뉴명 */
	private String menuNm;

	/** URL주소 (SPA 라우트 — NULL=폴더형) */
	private String urlAddr;

	/** 아이콘명 (프론트 아이콘 키) */
	private String iconNm;

	/** 필요권한코드 (NULL=권한 불요) */
	private String authCd;

	/** 로그인필요여부 */
	private String lgnNeedYn;

	/** 정렬순서 */
	private Integer sortSeq;
}
