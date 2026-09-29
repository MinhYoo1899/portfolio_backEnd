package egovframework.eeum.content.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
import egovframework.eeum.content.service.banner.ContentBannerService;
import egovframework.eeum.content.web.dto.ContentBannerCreateRequest;
import egovframework.eeum.content.web.dto.ContentBannerResponse;
import egovframework.eeum.content.web.dto.ContentBannerToggleRequest;
import egovframework.eeum.content.web.dto.ContentBannerUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 운영자 콘텐츠 — 메인배너. 조회=CONTENT_VIEW, 등록/수정/토글=CONTENT_MNG.
 *
 * <p>콘텐츠는 플랫폼(운영자) 소유 자원이라 파트너 소유권 필터가 없다 — 접근통제는 권한(@PreAuthorize).
 * status(EXPOSED/HIDDEN/ENDED)는 서버 산출값(프론트 재계산 금지). 삭제/순서저장은 현 단계 제외.
 */
@Tag(name = "operator-content-banner", description = "운영자 콘텐츠 — 메인배너 조회/등록/수정/토글")
@RestController
@RequestMapping("/operator/contents/main-banners")
@RequiredArgsConstructor
public class OperatorContentBannerController {

	private final ContentBannerService contentBannerService;

	@Operation(summary = "메인배너 목록(운영자)", description = "권한 CONTENT_VIEW. SORT_ORDER ASC, SN DESC. status 산출 포함.")
	@PreAuthorize("hasAuthority('CONTENT_VIEW')")
	@GetMapping
	public ApiResponse<PageResponse<ContentBannerResponse>> list() {
		return ApiResponse.ok(contentBannerService.list());
	}

	@Operation(summary = "메인배너 단건(운영자)", description = "권한 CONTENT_VIEW. 수정 프리필. 없으면 404.")
	@PreAuthorize("hasAuthority('CONTENT_VIEW')")
	@GetMapping("/{bannerId}")
	public ApiResponse<ContentBannerResponse> detail(@PathVariable("bannerId") Long bannerId) {
		return ApiResponse.ok(contentBannerService.get(bannerId));
	}

	@Operation(summary = "메인배너 등록(운영자)", description = "권한 CONTENT_MNG. imageUrl=자체 업로드 경로만. linkTarget/노출기간 검증.")
	@PreAuthorize("hasAuthority('CONTENT_MNG')")
	@PostMapping
	public ResponseEntity<ApiResponse<ContentBannerResponse>> create(
			@Valid @RequestBody ContentBannerCreateRequest request, @AuthenticationPrincipal AuthUser user) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiResponse.ok(contentBannerService.create(request, user)));
	}

	@Operation(summary = "메인배너 수정(운영자)", description = "권한 CONTENT_MNG. 전체 교체. exposeMode 로 노출여부 갱신. 없으면 404.")
	@PreAuthorize("hasAuthority('CONTENT_MNG')")
	@PutMapping("/{bannerId}")
	public ApiResponse<ContentBannerResponse> update(@PathVariable("bannerId") Long bannerId,
			@Valid @RequestBody ContentBannerUpdateRequest request, @AuthenticationPrincipal AuthUser user) {
		return ApiResponse.ok(contentBannerService.update(bannerId, request, user));
	}

	@Operation(summary = "메인배너 노출 토글(운영자)", description = "권한 CONTENT_MNG. 노출 on/off. 없으면 404.")
	@PreAuthorize("hasAuthority('CONTENT_MNG')")
	@PatchMapping("/{bannerId}/toggle")
	public ApiResponse<ContentBannerResponse> toggle(@PathVariable("bannerId") Long bannerId,
			@Valid @RequestBody ContentBannerToggleRequest request, @AuthenticationPrincipal AuthUser user) {
		return ApiResponse.ok(contentBannerService.toggle(bannerId, request.isActive(), user));
	}
}
