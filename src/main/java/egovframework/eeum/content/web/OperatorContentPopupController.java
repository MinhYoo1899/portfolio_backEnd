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
import egovframework.eeum.content.service.popup.ContentPopupService;
import egovframework.eeum.content.web.dto.ContentPopupCreateRequest;
import egovframework.eeum.content.web.dto.ContentPopupResponse;
import egovframework.eeum.content.web.dto.ContentPopupSearchRequest;
import egovframework.eeum.content.web.dto.ContentPopupToggleRequest;
import egovframework.eeum.content.web.dto.ContentPopupUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 운영자 콘텐츠 — 팝업. 조회=CONTENT_VIEW, 등록/수정/삭제/토글=CONTENT_MNG.
 *
 * <p>콘텐츠는 플랫폼(운영자) 소유 자원 — 접근통제는 권한(@PreAuthorize). status(EXPOSED/HIDDEN/ENDED)는 서버 산출값.
 * 이미지 업로드는 기존 /operator/uploads/images 재사용.
 */
@Tag(name = "operator-content-popup", description = "운영자 콘텐츠 — 팝업 조회/등록/수정/삭제/토글")
@RestController
@RequestMapping("/operator/contents/popups")
@RequiredArgsConstructor
public class OperatorContentPopupController {

	private final ContentPopupService contentPopupService;

	@Operation(summary = "팝업 목록(운영자)", description = "권한 CONTENT_VIEW. 상태/노출위치/제목 필터. SN DESC. status 산출 포함.")
	@PreAuthorize("hasAuthority('CONTENT_VIEW')")
	@GetMapping
	public ApiResponse<PageResponse<ContentPopupResponse>> list(@Valid ContentPopupSearchRequest request) {
		return ApiResponse.ok(contentPopupService.list(request));
	}

	@Operation(summary = "팝업 단건(운영자)", description = "권한 CONTENT_VIEW. 수정 프리필. 없으면 404.")
	@PreAuthorize("hasAuthority('CONTENT_VIEW')")
	@GetMapping("/{popupId}")
	public ApiResponse<ContentPopupResponse> detail(@PathVariable("popupId") Long popupId) {
		return ApiResponse.ok(contentPopupService.get(popupId));
	}

	@Operation(summary = "팝업 등록(운영자)", description = "권한 CONTENT_MNG. 이미지/위치/대상/linkTarget/노출기간 검증. exposeMode 로 노출여부.")
	@PreAuthorize("hasAuthority('CONTENT_MNG')")
	@PostMapping
	public ResponseEntity<ApiResponse<ContentPopupResponse>> create(
			@Valid @RequestBody ContentPopupCreateRequest request, @AuthenticationPrincipal AuthUser user) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiResponse.ok(contentPopupService.create(request, user)));
	}

	@Operation(summary = "팝업 수정(운영자)", description = "권한 CONTENT_MNG. 전체 교체. exposeMode 로 노출여부 갱신. 없으면 404.")
	@PreAuthorize("hasAuthority('CONTENT_MNG')")
	@PutMapping("/{popupId}")
	public ApiResponse<ContentPopupResponse> update(@PathVariable("popupId") Long popupId,
			@Valid @RequestBody ContentPopupUpdateRequest request, @AuthenticationPrincipal AuthUser user) {
		return ApiResponse.ok(contentPopupService.update(popupId, request, user));
	}

	@Operation(summary = "팝업 삭제(운영자)", description = "권한 CONTENT_MNG. soft delete(DEL_YN='Y'). 없으면 404.")
	@PreAuthorize("hasAuthority('CONTENT_MNG')")
	@DeleteMapping("/{popupId}")
	public ApiResponse<Void> delete(@PathVariable("popupId") Long popupId, @AuthenticationPrincipal AuthUser user) {
		contentPopupService.delete(popupId, user);
		return ApiResponse.ok();
	}

	@Operation(summary = "팝업 노출 토글(운영자)", description = "권한 CONTENT_MNG. 노출 on/off. 없으면 404.")
	@PreAuthorize("hasAuthority('CONTENT_MNG')")
	@PatchMapping("/{popupId}/toggle")
	public ApiResponse<ContentPopupResponse> toggle(@PathVariable("popupId") Long popupId,
			@Valid @RequestBody ContentPopupToggleRequest request, @AuthenticationPrincipal AuthUser user) {
		return ApiResponse.ok(contentPopupService.toggle(popupId, request.isActive(), user));
	}
}
