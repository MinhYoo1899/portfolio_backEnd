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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import egovframework.eeum.auth.vo.AuthUser;
import egovframework.eeum.base.response.ApiResponse;
import egovframework.eeum.house.service.image.HouseImageService;
import egovframework.eeum.house.web.dto.HouseImageDeleteRequest;
import egovframework.eeum.house.web.dto.HouseImageRepresentativeRequest;
import egovframework.eeum.house.web.dto.HouseImageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 상품(고택 이미지) API — 판매자 본인 소유 고택의 공개 이미지 관리.
 *
 * <p>전부 권한 {@code HOUSE_MNG}. 이미지(jpg/jpeg/png/gif) 멀티파트 업로드, 정렬은 업로드 순서, 첫 이미지 자동 대표.
 */
@Tag(name = "house-image", description = "상품 (고택 이미지 — 판매자 관리)")
@RestController
@RequestMapping("/houses/images")
@RequiredArgsConstructor
public class HouseImageController {

	private final HouseImageService houseImageService;

	@Operation(summary = "고택 이미지 추가", description = "판매자 본인 소유. 멀티파트 업로드. 첫 이미지는 자동 대표.")
	@PreAuthorize("hasAuthority('HOUSE_MNG')")
	@PostMapping
	public ResponseEntity<ApiResponse<HouseImageResponse>> add(
			@RequestParam("houseSn") Long houseSn,
			@RequestParam("file") MultipartFile file,
			@AuthenticationPrincipal AuthUser user) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiResponse.ok(houseImageService.addImage(houseSn, file, user)));
	}

	@Operation(summary = "고택 이미지 목록", description = "판매자 본인 소유. 정렬순.")
	@PreAuthorize("hasAuthority('HOUSE_MNG')")
	@GetMapping
	public ApiResponse<List<HouseImageResponse>> list(@RequestParam("houseSn") Long houseSn,
			@AuthenticationPrincipal AuthUser user) {
		return ApiResponse.ok(houseImageService.listByHouse(houseSn, user));
	}

	@Operation(summary = "고택 이미지 삭제", description = "판매자 본인 소유. 대표 삭제 시 가장 앞 이미지로 자동 승격.")
	@PreAuthorize("hasAuthority('HOUSE_MNG')")
	@PostMapping("/delete")
	public ApiResponse<Void> delete(@Valid @RequestBody HouseImageDeleteRequest request,
			@AuthenticationPrincipal AuthUser user) {
		houseImageService.delete(request, user);
		return ApiResponse.ok();
	}

	@Operation(summary = "대표 이미지 지정", description = "판매자 본인 소유. 기존 대표 해제 후 교체.")
	@PreAuthorize("hasAuthority('HOUSE_MNG')")
	@PostMapping("/representative")
	public ApiResponse<Void> setRepresentative(@Valid @RequestBody HouseImageRepresentativeRequest request,
			@AuthenticationPrincipal AuthUser user) {
		houseImageService.setRepresentative(request, user);
		return ApiResponse.ok();
	}
}
