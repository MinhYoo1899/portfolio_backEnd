package egovframework.eeum.house.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import egovframework.eeum.auth.vo.AuthUser;
import egovframework.eeum.base.response.ApiResponse;
import egovframework.eeum.house.service.amenity.AmenityService;
import egovframework.eeum.house.web.dto.AmenityActiveChangeRequest;
import egovframework.eeum.house.web.dto.AmenityInsertRequest;
import egovframework.eeum.house.web.dto.AmenityResponse;
import egovframework.eeum.house.web.dto.AmenityUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 편의시설 카탈로그 API.
 *
 * <p>선택용 목록(사용중 항목)은 인증 사용자 누구나, 관리(추가/수정/사용여부)는 운영자급({@code AMENITY_MNG}).
 * 승인 게이트 없음 — 운영자/파트너가 추가하면 즉시 카탈로그에 노출.
 */
@Tag(name = "amenity", description = "편의시설 카탈로그")
@RestController
@RequestMapping("/amenities")
@RequiredArgsConstructor
public class AmenityController {

	private final AmenityService amenityService;

	@Operation(summary = "편의시설 선택용 목록", description = "사용중(노출) 항목만. 판매자 고택 등록/수정 화면용.")
	@GetMapping
	public ApiResponse<List<AmenityResponse>> list(@AuthenticationPrincipal AuthUser user) {
		return ApiResponse.ok(amenityService.listForSelection());
	}

	@Operation(summary = "편의시설 관리 목록(운영자급)", description = "권한 AMENITY_MNG. 비활성 포함 전체.")
	@PreAuthorize("hasAuthority('AMENITY_MNG')")
	@GetMapping("/manage")
	public ApiResponse<List<AmenityResponse>> listAll(@AuthenticationPrincipal AuthUser user) {
		return ApiResponse.ok(amenityService.listAll());
	}

	@Operation(summary = "편의시설 추가(운영자급)", description = "권한 AMENITY_MNG.")
	@PreAuthorize("hasAuthority('AMENITY_MNG')")
	@PostMapping("/insert")
	public ResponseEntity<ApiResponse<AmenityResponse>> insert(
			@Valid @RequestBody AmenityInsertRequest request, @AuthenticationPrincipal AuthUser user) {
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(amenityService.insert(request, user)));
	}

	@Operation(summary = "편의시설 수정(운영자급)", description = "권한 AMENITY_MNG.")
	@PreAuthorize("hasAuthority('AMENITY_MNG')")
	@PostMapping("/update")
	public ApiResponse<Void> update(@Valid @RequestBody AmenityUpdateRequest request,
			@AuthenticationPrincipal AuthUser user) {
		amenityService.update(request, user);
		return ApiResponse.ok();
	}

	@Operation(summary = "편의시설 사용여부 변경(운영자급)", description = "권한 AMENITY_MNG. N=신규 선택목록에서 제외.")
	@PreAuthorize("hasAuthority('AMENITY_MNG')")
	@PostMapping("/active")
	public ApiResponse<Void> changeActive(@Valid @RequestBody AmenityActiveChangeRequest request,
			@AuthenticationPrincipal AuthUser user) {
		amenityService.changeActive(request, user);
		return ApiResponse.ok();
	}
}
