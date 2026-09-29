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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import egovframework.eeum.auth.vo.AuthUser;
import egovframework.eeum.base.response.ApiResponse;
import egovframework.eeum.base.response.PageResponse;
import egovframework.eeum.content.service.rechouse.ContentRecHouseService;
import egovframework.eeum.content.web.dto.ContentRecHouseCreateRequest;
import egovframework.eeum.content.web.dto.ContentRecHouseOrderRequest;
import egovframework.eeum.content.web.dto.ContentRecHouseResponse;
import egovframework.eeum.content.web.dto.ContentRecHouseToggleRequest;
import egovframework.eeum.content.web.dto.ContentRecHouseUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 운영자 콘텐츠 — 추천고택. 조회=CONTENT_VIEW, 등록/수정/삭제/토글/순서=CONTENT_MNG.
 *
 * <p>콘텐츠는 플랫폼(운영자) 소유 자원 — 접근통제는 권한(@PreAuthorize). status(EXPOSED/HIDDEN/ENDED)는 서버 산출값.
 * 고택 검색은 기존 GET /operator/houses?keyword= 재사용.
 */
@Tag(name = "operator-content-rec-house", description = "운영자 콘텐츠 — 추천고택 조회/등록/수정/삭제/토글/순서")
@RestController
@RequestMapping("/operator/contents/houses/recommend")
@RequiredArgsConstructor
public class OperatorContentRecHouseController {

	private final ContentRecHouseService contentRecHouseService;

	@Operation(summary = "추천고택 목록(운영자)", description = "권한 CONTENT_VIEW. 분류 필터(recCategory). SORT_ORDER ASC, SN DESC. status 산출·고택명/주소 포함.")
	@PreAuthorize("hasAuthority('CONTENT_VIEW')")
	@GetMapping
	public ApiResponse<PageResponse<ContentRecHouseResponse>> list(
			@RequestParam(value = "recCategory", required = false) String recCategory) {
		return ApiResponse.ok(contentRecHouseService.list(recCategory));
	}

	@Operation(summary = "추천고택 단건(운영자)", description = "권한 CONTENT_VIEW. 수정 프리필. 없으면 404.")
	@PreAuthorize("hasAuthority('CONTENT_VIEW')")
	@GetMapping("/{recId}")
	public ApiResponse<ContentRecHouseResponse> detail(@PathVariable("recId") Long recId) {
		return ApiResponse.ok(contentRecHouseService.get(recId));
	}

	@Operation(summary = "추천고택 등록(운영자)", description = "권한 CONTENT_MNG. 분류/고택(존재+미삭제)/노출기간 검증. exposeMode 로 노출여부.")
	@PreAuthorize("hasAuthority('CONTENT_MNG')")
	@PostMapping
	public ResponseEntity<ApiResponse<ContentRecHouseResponse>> create(
			@Valid @RequestBody ContentRecHouseCreateRequest request, @AuthenticationPrincipal AuthUser user) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiResponse.ok(contentRecHouseService.create(request, user)));
	}

	@Operation(summary = "추천고택 수정(운영자)", description = "권한 CONTENT_MNG. 전체 교체(순서 제외). 없으면 404.")
	@PreAuthorize("hasAuthority('CONTENT_MNG')")
	@PutMapping("/{recId}")
	public ApiResponse<ContentRecHouseResponse> update(@PathVariable("recId") Long recId,
			@Valid @RequestBody ContentRecHouseUpdateRequest request, @AuthenticationPrincipal AuthUser user) {
		return ApiResponse.ok(contentRecHouseService.update(recId, request, user));
	}

	@Operation(summary = "추천고택 순서 저장(운영자)", description = "권한 CONTENT_MNG. 드래그앤드롭 [{recId, sortOrder}] 배열 반영. 없는 항목은 404.")
	@PreAuthorize("hasAuthority('CONTENT_MNG')")
	@PatchMapping("/order")
	public ApiResponse<Void> reorder(@Valid @RequestBody ContentRecHouseOrderRequest request,
			@AuthenticationPrincipal AuthUser user) {
		contentRecHouseService.reorder(request, user);
		return ApiResponse.ok();
	}

	@Operation(summary = "추천고택 삭제(운영자)", description = "권한 CONTENT_MNG. soft delete(DEL_YN='Y'). 없으면 404.")
	@PreAuthorize("hasAuthority('CONTENT_MNG')")
	@DeleteMapping("/{recId}")
	public ApiResponse<Void> delete(@PathVariable("recId") Long recId, @AuthenticationPrincipal AuthUser user) {
		contentRecHouseService.delete(recId, user);
		return ApiResponse.ok();
	}

	@Operation(summary = "추천고택 노출 토글(운영자)", description = "권한 CONTENT_MNG. 노출 on/off. 없으면 404.")
	@PreAuthorize("hasAuthority('CONTENT_MNG')")
	@PatchMapping("/{recId}/toggle")
	public ApiResponse<ContentRecHouseResponse> toggle(@PathVariable("recId") Long recId,
			@Valid @RequestBody ContentRecHouseToggleRequest request, @AuthenticationPrincipal AuthUser user) {
		return ApiResponse.ok(contentRecHouseService.toggle(recId, request.isActive(), user));
	}
}
