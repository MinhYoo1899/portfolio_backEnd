package egovframework.eeum.code.web.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 공통코드 목록 응답 DTO — 프론트 계약상 {@code result.items} 래퍼(frontend-common-code-guide).
 *
 * @param items 코드 목록(정렬순서 순, 빈 그룹이면 빈 목록)
 */
@Schema(description = "공통코드 목록 응답")
public record CodeListResponse(
		@Schema(description = "코드 목록") List<CodeItemResponse> items) {
}
