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
import egovframework.eeum.content.service.adbanner.ContentAdBannerService;
import egovframework.eeum.content.web.dto.ContentAdBannerCreateRequest;
import egovframework.eeum.content.web.dto.ContentAdBannerResponse;
import egovframework.eeum.content.web.dto.ContentAdBannerToggleRequest;
import egovframework.eeum.content.web.dto.ContentAdBannerUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 운영자 콘텐츠 — 광고배너. 조회=CONTENT_VIEW, 등록/수정/삭제/토글=CONTENT_MNG.
 *
 * <p>콘텐츠는 플랫폼(운영자) 소유 자원이라 파트너 소유권 필터가 없다 — 접근통제는 권한(@PreAuthorize).
 * status(EXPOSED/HIDDEN/ENDED)는 서버 산출값(프론트 재계산 금지). 이미지 업로드는 기존 /operator/uploads/images 재사용.
 */
@Tag(name = "operator-content-ad-banner", description = "운영자 콘텐츠 — 광고배너 조회/등록/수정/삭제/토글")
@RestController
@RequestMapping("/operator/contents/ad-banners")
@RequiredArgsConstructor
public class OperatorContentAdBannerController {

	private final ContentAdBannerService contentAdBannerService;

	@Operation(summary = "광고배너 목록(운영자)", description = "권한 CONTENT_VIEW. 노출위치 SORT_SEQ ASC, SN DESC. status 산출 포함.")
	@PreAuthorize("hasAuthority('CONTENT_VIEW')")
	@GetMapping
	public ApiResponse<PageResponse<ContentAdBannerResponse>> list() {
		return ApiResponse.ok(contentAdBannerService.list());
	}

	@Operation(summary = "광고배너 단건(운영자)", description = "권한 CONTENT_VIEW. 수정 프리필. 없으면 404.")
	@PreAuthorize("hasAuthority('CONTENT_VIEW')")
	@GetMapping("/{adBannerId}")
	public ApiResponse<ContentAdBannerResponse> detail(@PathVariable("adBannerId") Long adBannerId) {
		return ApiResponse.ok(contentAdBannerService.get(adBannerId));
	}

	@Operation(summary = "광고배너 등록(운영자)",
			description = "권한 CONTENT_MNG. positionCd/linkTarget/노출기간 검증. imageUrl=선택(있으면 자체 업로드 경로만). 생성 시 노출 ON.")
	@PreAuthorize("hasAuthority('CONTENT_MNG')")
	@PostMapping
	public ResponseEntity<ApiResponse<ContentAdBannerResponse>> create(
			@Valid @RequestBody ContentAdBannerCreateRequest request, @AuthenticationPrincipal AuthUser user) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiResponse.ok(contentAdBannerService.create(request, user)));
	}

	@Operation(summary = "광고배너 수정(운영자)", description = "권한 CONTENT_MNG. 전체 교체(노출여부 보존 — 토글은 별도). 없으면 404.")
	@PreAuthorize("hasAuthority('CONTENT_MNG')")
	@PutMapping("/{adBannerId}")
	public ApiResponse<ContentAdBannerResponse> update(@PathVariable("adBannerId") Long adBannerId,
			@Valid @RequestBody ContentAdBannerUpdateRequest request, @AuthenticationPrincipal AuthUser user) {
		return ApiResponse.ok(contentAdBannerService.update(adBannerId, request, user));
	}

	@Operation(summary = "광고배너 삭제(운영자)", description = "권한 CONTENT_MNG. soft delete(DEL_YN='Y'). 없으면 404.")
	@PreAuthorize("hasAuthority('CONTENT_MNG')")
	@DeleteMapping("/{adBannerId}")
	public ApiResponse<Void> delete(@PathVariable("adBannerId") Long adBannerId,
			@AuthenticationPrincipal AuthUser user) {
		contentAdBannerService.delete(adBannerId, user);
		return ApiResponse.ok();
	}

	@Operation(summary = "광고배너 노출 토글(운영자)", description = "권한 CONTENT_MNG. 노출 on/off. 없으면 404.")
	@PreAuthorize("hasAuthority('CONTENT_MNG')")
	@PatchMapping("/{adBannerId}/toggle")
	public ApiResponse<ContentAdBannerResponse> toggle(@PathVariable("adBannerId") Long adBannerId,
			@Valid @RequestBody ContentAdBannerToggleRequest request, @AuthenticationPrincipal AuthUser user) {
		return ApiResponse.ok(contentAdBannerService.toggle(adBannerId, request.isActive(), user));
	}
}
