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
import egovframework.eeum.house.service.image.RoomImageService;
import egovframework.eeum.house.web.dto.RoomImageDeleteRequest;
import egovframework.eeum.house.web.dto.RoomImageRepresentativeRequest;
import egovframework.eeum.house.web.dto.RoomImageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 상품(객실 이미지) API — 판매자 본인 소유 객실의 공개 이미지 관리. 전부 권한 {@code HOUSE_MNG}.
 */
@Tag(name = "room-image", description = "상품 (객실 이미지 — 판매자 관리)")
@RestController
@RequestMapping("/rooms/images")
@RequiredArgsConstructor
public class RoomImageController {

	private final RoomImageService roomImageService;

	@Operation(summary = "객실 이미지 추가", description = "판매자 본인 소유. 멀티파트 업로드. 첫 이미지는 자동 대표.")
	@PreAuthorize("hasAuthority('HOUSE_MNG')")
	@PostMapping
	public ResponseEntity<ApiResponse<RoomImageResponse>> add(
			@RequestParam("roomSn") Long roomSn,
			@RequestParam("file") MultipartFile file,
			@AuthenticationPrincipal AuthUser user) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiResponse.ok(roomImageService.addImage(roomSn, file, user)));
	}

	@Operation(summary = "객실 이미지 목록", description = "판매자 본인 소유. 정렬순.")
	@PreAuthorize("hasAuthority('HOUSE_MNG')")
	@GetMapping
	public ApiResponse<List<RoomImageResponse>> list(@RequestParam("roomSn") Long roomSn,
			@AuthenticationPrincipal AuthUser user) {
		return ApiResponse.ok(roomImageService.listByRoom(roomSn, user));
	}

	@Operation(summary = "객실 이미지 삭제", description = "판매자 본인 소유. 대표 삭제 시 가장 앞 이미지로 자동 승격.")
	@PreAuthorize("hasAuthority('HOUSE_MNG')")
	@PostMapping("/delete")
	public ApiResponse<Void> delete(@Valid @RequestBody RoomImageDeleteRequest request,
			@AuthenticationPrincipal AuthUser user) {
		roomImageService.delete(request, user);
		return ApiResponse.ok();
	}

	@Operation(summary = "객실 대표 이미지 지정", description = "판매자 본인 소유. 기존 대표 해제 후 교체.")
	@PreAuthorize("hasAuthority('HOUSE_MNG')")
	@PostMapping("/representative")
	public ApiResponse<Void> setRepresentative(@Valid @RequestBody RoomImageRepresentativeRequest request,
			@AuthenticationPrincipal AuthUser user) {
		roomImageService.setRepresentative(request, user);
		return ApiResponse.ok();
	}
}
