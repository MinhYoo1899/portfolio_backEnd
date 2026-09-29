package egovframework.eeum.content.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import egovframework.eeum.auth.vo.AuthUser;
import egovframework.eeum.base.response.ApiResponse;
import egovframework.eeum.base.response.PageResponse;
import egovframework.eeum.content.service.region.ContentRegionService;
import egovframework.eeum.content.web.dto.ContentRegionCreateRequest;
import egovframework.eeum.content.web.dto.ContentRegionResponse;
import egovframework.eeum.content.web.dto.ContentRegionSearchRequest;
import egovframework.eeum.content.web.dto.ContentRegionToggleRequest;
import egovframework.eeum.content.web.dto.ContentRegionUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 운영자 콘텐츠 — 지역별 즐길거리. 조회=CONTENT_VIEW, 등록/수정/삭제/토글=CONTENT_MNG.
 *
 * <p>콘텐츠는 플랫폼(운영자) 소유 자원 — 접근통제는 권한(@PreAuthorize). status(EXPOSED/HIDDEN/ENDED)는 서버 산출값.
 * 조회수/댓글수는 읽기전용. 이미지 업로드는 기존 /operator/uploads/images 재사용.
 */
@Tag(name = "operator-content-region", description = "운영자 콘텐츠 — 지역별 즐길거리 조회/등록/수정/삭제/토글")
@RestController
@RequestMapping("/operator/contents/regions")
@RequiredArgsConstructor
public class OperatorContentRegionController {

	private final ContentRegionService contentRegionService;

	@Operation(summary = "즐길거리 목록(운영자)", description = "권한 CONTENT_VIEW. 카테고리/유형/지역/제목 필터 + 페이지네이션(기본 8). status 산출.")
	@PreAuthorize("hasAuthority('CONTENT_VIEW')")
	@GetMapping
	public ApiResponse<PageResponse<ContentRegionResponse>> list(@Valid ContentRegionSearchRequest request) {
		return ApiResponse.ok(contentRegionService.list(request));
	}

	@Operation(summary = "즐길거리 상세(운영자)", description = "권한 CONTENT_VIEW. 수정 프리필/본문 포함. 없으면 404.")
	@PreAuthorize("hasAuthority('CONTENT_VIEW')")
	@GetMapping("/{contentId}")
	public ApiResponse<ContentRegionResponse> detail(@PathVariable("contentId") Long contentId) {
		return ApiResponse.ok(contentRegionService.get(contentId));
	}

	@Operation(summary = "즐길거리 등록(운영자)", description = "권한 CONTENT_MNG. 카테고리/유형/지역/이미지/노출기간 검증. 조회수/댓글수 0.")
	@PreAuthorize("hasAuthority('CONTENT_MNG')")
	@PostMapping
	public ResponseEntity<ApiResponse<ContentRegionResponse>> create(
			@Valid @RequestBody ContentRegionCreateRequest request, @AuthenticationPrincipal AuthUser user) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiResponse.ok(contentRegionService.create(request, user)));
	}

	@Operation(summary = "즐길거리 수정(운영자)", description = "권한 CONTENT_MNG. 전체 교체(조회수/댓글수 보존). 없으면 404.")
	@PreAuthorize("hasAuthority('CONTENT_MNG')")
	@PutMapping("/{contentId}")
	public ApiResponse<ContentRegionResponse> update(@PathVariable("contentId") Long contentId,
			@Valid @RequestBody ContentRegionUpdateRequest request, @AuthenticationPrincipal AuthUser user) {
		return ApiResponse.ok(contentRegionService.update(contentId, request, user));
	}

	@Operation(summary = "즐길거리 삭제(운영자)", description = "권한 CONTENT_MNG. soft delete(DEL_YN='Y'). 없으면 404.")
	@PreAuthorize("hasAuthority('CONTENT_MNG')")
	@DeleteMapping("/{contentId}")
	public ApiResponse<Void> delete(@PathVariable("contentId") Long contentId,
			@AuthenticationPrincipal AuthUser user) {
		contentRegionService.delete(contentId, user);
		return ApiResponse.ok();
	}

	@Operation(summary = "즐길거리 활성 토글(운영자)", description = "권한 CONTENT_MNG. 활성 on/off. 없으면 404.")
	@PreAuthorize("hasAuthority('CONTENT_MNG')")
	@PatchMapping("/{contentId}/toggle")
	public ApiResponse<ContentRegionResponse> toggle(@PathVariable("contentId") Long contentId,
			@Valid @RequestBody ContentRegionToggleRequest request, @AuthenticationPrincipal AuthUser user) {
		return ApiResponse.ok(contentRegionService.toggle(contentId, request.isActive(), user));
	}
}
