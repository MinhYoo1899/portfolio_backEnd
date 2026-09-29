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

import egovframework.eeum.auth.vo.AuthUser;
import egovframework.eeum.base.response.ApiResponse;
import egovframework.eeum.house.service.room.RoomAmenityService;
import egovframework.eeum.house.service.room.RoomClimateService;
import egovframework.eeum.house.service.room.RoomOptionService;
import egovframework.eeum.house.service.room.RoomService;
import egovframework.eeum.house.web.dto.AmenityResponse;
import egovframework.eeum.house.web.dto.RoomAmenitySetRequest;
import egovframework.eeum.house.web.dto.RoomBeddingResponse;
import egovframework.eeum.house.web.dto.RoomBeddingSetRequest;
import egovframework.eeum.house.web.dto.RoomClimateSetRequest;
import egovframework.eeum.house.web.dto.RoomDeleteRequest;
import egovframework.eeum.house.web.dto.RoomInsertRequest;
import egovframework.eeum.house.web.dto.RoomOptionResponse;
import egovframework.eeum.house.web.dto.RoomOptionSetRequest;
import egovframework.eeum.house.web.dto.RoomResponse;
import egovframework.eeum.house.web.dto.RoomStatusChangeRequest;
import egovframework.eeum.house.web.dto.RoomUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 상품(객실) API — 판매자(파트너) 본인 소유 고택의 객실 관리.
 *
 * <p>등록/수정/삭제/상태변경/조회 모두 권한 {@code HOUSE_MNG}(파트너) 필요. 소유권은 부모 고택의 PRTNR_SN 으로 판정.
 */
@Tag(name = "room", description = "상품 (객실 — 판매자 관리)")
@RestController
@RequestMapping("/rooms")
@RequiredArgsConstructor
public class RoomController {

	private final RoomService roomService;
	private final RoomAmenityService roomAmenityService;
	private final RoomOptionService roomOptionService;
	private final RoomClimateService roomClimateService;

	@Operation(summary = "객실 등록", description = "판매자 본인 소유 고택 아래 DRAFT 로 생성.")
	@PreAuthorize("hasAuthority('HOUSE_MNG')")
	@PostMapping("/insert")
	public ResponseEntity<ApiResponse<RoomResponse>> insert(
			@Valid @RequestBody RoomInsertRequest request, @AuthenticationPrincipal AuthUser user) {
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(roomService.insert(request, user)));
	}

	@Operation(summary = "고택별 객실 목록", description = "판매자 본인 소유 고택의 객실 전체.")
	@PreAuthorize("hasAuthority('HOUSE_MNG')")
	@GetMapping("/list")
	public ApiResponse<List<RoomResponse>> list(@RequestParam("houseSn") Long houseSn,
			@AuthenticationPrincipal AuthUser user) {
		return ApiResponse.ok(roomService.listByHouse(houseSn, user));
	}

	@Operation(summary = "객실 상세", description = "판매자 본인 소유(타 소유는 404).")
	@PreAuthorize("hasAuthority('HOUSE_MNG')")
	@GetMapping("/detail")
	public ApiResponse<RoomResponse> detail(@RequestParam("roomSn") Long roomSn,
			@AuthenticationPrincipal AuthUser user) {
		return ApiResponse.ok(roomService.detail(roomSn, user));
	}

	@Operation(summary = "객실 수정", description = "판매자 본인 소유.")
	@PreAuthorize("hasAuthority('HOUSE_MNG')")
	@PostMapping("/update")
	public ApiResponse<Void> update(@Valid @RequestBody RoomUpdateRequest request,
			@AuthenticationPrincipal AuthUser user) {
		roomService.update(request, user);
		return ApiResponse.ok();
	}

	@Operation(summary = "객실 상태변경(게시/숨김)", description = "판매자 본인. DRAFT/ACTIVE/HIDDEN. SUSPENDED 복구 불가.")
	@PreAuthorize("hasAuthority('HOUSE_MNG')")
	@PostMapping("/status")
	public ApiResponse<Void> changeStatus(@Valid @RequestBody RoomStatusChangeRequest request,
			@AuthenticationPrincipal AuthUser user) {
		roomService.changeStatus(request, user);
		return ApiResponse.ok();
	}

	@Operation(summary = "객실 삭제", description = "판매자 본인 소유(soft delete).")
	@PreAuthorize("hasAuthority('HOUSE_MNG')")
	@PostMapping("/delete")
	public ApiResponse<Void> delete(@Valid @RequestBody RoomDeleteRequest request,
			@AuthenticationPrincipal AuthUser user) {
		roomService.delete(request.roomSn(), user);
		return ApiResponse.ok();
	}

	@Operation(summary = "객실 침구 설정(교체)", description = "판매자 본인 소유. 기존 활성 침구를 비우고 재구성. 타입은 COMM_CD BEDDING_TYPE.")
	@PreAuthorize("hasAuthority('HOUSE_MNG')")
	@PostMapping("/beddings/set")
	public ApiResponse<List<RoomBeddingResponse>> setBeddings(@Valid @RequestBody RoomBeddingSetRequest request,
			@AuthenticationPrincipal AuthUser user) {
		return ApiResponse.ok(roomService.setBeddings(request, user));
	}

	@Operation(summary = "객실 편의시설 조회", description = "판매자 본인 소유. 객실의 선택된 편의시설(사용중).")
	@PreAuthorize("hasAuthority('HOUSE_MNG')")
	@GetMapping("/amenities")
	public ApiResponse<List<AmenityResponse>> amenities(@RequestParam("roomSn") Long roomSn,
			@AuthenticationPrincipal AuthUser user) {
		return ApiResponse.ok(roomAmenityService.listByRoom(roomSn, user));
	}

	@Operation(summary = "객실 편의시설 설정(교체)", description = "판매자 본인 소유. 선택 집합 전체 교체(빈 배열=전부 해제).")
	@PreAuthorize("hasAuthority('HOUSE_MNG')")
	@PostMapping("/amenities/set")
	public ApiResponse<Void> setAmenities(@Valid @RequestBody RoomAmenitySetRequest request,
			@AuthenticationPrincipal AuthUser user) {
		roomAmenityService.setAmenities(request, user);
		return ApiResponse.ok();
	}

	@Operation(summary = "객실 추가옵션 설정(교체)", description = "판매자 본인 소유. 기존 활성 옵션을 비우고 재구성. 과금=예약당 1회.")
	@PreAuthorize("hasAuthority('HOUSE_MNG')")
	@PostMapping("/options/set")
	public ApiResponse<List<RoomOptionResponse>> setOptions(@Valid @RequestBody RoomOptionSetRequest request,
			@AuthenticationPrincipal AuthUser user) {
		return ApiResponse.ok(roomOptionService.setOptions(request, user));
	}

	@Operation(summary = "객실 냉난방시설 설정(교체)", description = "판매자 본인 소유. 냉방/난방 각각 다중선택. 코드는 COMM_CD COOLING_TYPE/HEATING_TYPE. 둘 다 빈 배열=전부 해제.")
	@PreAuthorize("hasAuthority('HOUSE_MNG')")
	@PostMapping("/climate/set")
	public ApiResponse<Void> setClimate(@Valid @RequestBody RoomClimateSetRequest request,
			@AuthenticationPrincipal AuthUser user) {
		roomClimateService.setClimate(request, user);
		return ApiResponse.ok();
	}
}
