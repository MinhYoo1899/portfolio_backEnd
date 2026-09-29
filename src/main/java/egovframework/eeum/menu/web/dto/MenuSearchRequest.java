package egovframework.eeum.menu.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

/**
 * 메뉴 목록 요청 DTO (GET query 바인딩).
 */
@Schema(description = "메뉴 목록 요청")
@Getter
@Setter
public class MenuSearchRequest {

	/** 메뉴채널코드 — 어느 SPA의 메뉴인지 */
	@Schema(description = "메뉴채널코드(USER/PARTNER/OPERATOR)", example = "USER")
	@NotBlank(message = "메뉴채널코드는 필수입니다.")
	@Pattern(regexp = "USER|PARTNER|OPERATOR", message = "메뉴채널코드 값이 올바르지 않습니다.")
	private String menuChnlCd;
}
