package egovframework.eeum.house.web;

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

import java.util.List;

import egovframework.eeum.auth.vo.AuthUser;
import egovframework.eeum.base.response.ApiResponse;
import egovframework.eeum.base.response.PageResponse;
import egovframework.eeum.house.service.amenity.HouseAmenityService;
import egovframework.eeum.house.service.house.HouseService;
import egovframework.eeum.house.web.dto.AmenityResponse;
import egovframework.eeum.house.web.dto.HouseAmenitySetRequest;
import egovframework.eeum.house.web.dto.HouseDeleteRequest;
import egovframework.eeum.house.web.dto.HouseDetailResponse;
import egovframework.eeum.house.web.dto.HouseInsertRequest;
import egovframework.eeum.house.web.dto.HouseListItemResponse;
import egovframework.eeum.house.web.dto.HousePayMethodSetRequest;
import egovframework.eeum.house.web.dto.HousePolicyUpdateRequest;
import egovframework.eeum.house.web.dto.HouseSearchRequest;
import egovframework.eeum.house.web.dto.HouseStatusChangeRequest;
import egovframework.eeum.house.web.dto.HouseUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 상품(고택) API — 판매자(파트너) 본인 소유 고택 관리.
 *
 * <p>등록/수정/삭제/상태변경/조회 모두 권한 {@code HOUSE_MNG}(파트너) 필요. 소유자(PRTNR_SN)는 토큰에서 해석.
 * 운영자 조회·임시조치는 후속 슬라이스에서 별도 엔드포인트로 제공한다.
 */
@Tag(name = "house", description = "상품 (고택 — 판매자 관리)")
@RestController
@RequestMapping("/houses")
@RequiredArgsConstructor
public class HouseController {

	private final HouseService houseService;
	private final HouseAmenityService houseAmenityService;

	@Operation(summary = "고택 등록", description = "판매자 본인. DRAFT 상태로 생성. 소유자는 토큰에서 해석.")
	@PreAuthorize("hasAuthority('HOUSE_MNG')")
	@PostMapping("/insert")
	public ResponseEntity<ApiResponse<HouseDetailResponse>> insert(
			@Valid @RequestBody HouseInsertRequest request, @AuthenticationPrincipal AuthUser user) {
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(houseService.insert(request, user)));
	}

	@Operation(summary = "내 고택 목록", description = "판매자 본인 소유. 오프셋 페이징. 필터 status/keyword.")
	@PreAuthorize("hasAuthority('HOUSE_MNG')")
	@GetMapping("/list")
	public ApiResponse<PageResponse<HouseListItemResponse>> list(@Valid HouseSearchRequest request,
			@AuthenticationPrincipal AuthUser user) {
		return ApiResponse.ok(houseService.list(request, user));
	}

	@Operation(summary = "내 고택 상세", description = "판매자 본인 소유(타 소유는 404).")
	@PreAuthorize("hasAuthority('HOUSE_MNG')")
	@GetMapping("/detail")
	public ApiResponse<HouseDetailResponse> detail(@RequestParam("houseSn") Long houseSn,
			@AuthenticationPrincipal AuthUser user) {
		return ApiResponse.ok(houseService.detail(houseSn, user));
	}

	@Operation(summary = "고택 수정", description = "판매자 본인 소유.")
	@PreAuthorize("hasAuthority('HOUSE_MNG')")
	@PostMapping("/update")
	public ApiResponse<Void> update(@Valid @RequestBody HouseUpdateRequest request,
			@AuthenticationPrincipal AuthUser user) {
		houseService.update(request, user);
		return ApiResponse.ok();
	}

	@Operation(summary = "고택 상태변경(게시/숨김)", description = "판매자 본인. DRAFT/ACTIVE/HIDDEN. SUSPENDED 복구 불가.")
	@PreAuthorize("hasAuthority('HOUSE_MNG')")
	@PostMapping("/status")
	public ApiResponse<Void> changeStatus(@Valid @RequestBody HouseStatusChangeRequest request,
			@AuthenticationPrincipal AuthUser user) {
		houseService.changeStatus(request, user);
		return ApiResponse.ok();
	}

	@Operation(summary = "고택 정책 설정", description = "판매자 본인. 위저드 Step2 — 추가금/체크인·아웃/예약확정/취소환불·대중교통 전체저장.")
	@PreAuthorize("hasAuthority('HOUSE_MNG')")
	@PostMapping("/policy")
	public ApiResponse<Void> updatePolicy(@Valid @RequestBody HousePolicyUpdateRequest request,
			@AuthenticationPrincipal AuthUser user) {
		houseService.updatePolicy(request, user);
		return ApiResponse.ok();
	}

	@Operation(summary = "고택 결제수단 설정", description = "판매자 본인. 선택 집합 전체 교체(빈 배열=전부 해제).")
	@PreAuthorize("hasAuthority('HOUSE_MNG')")
	@PostMapping("/pay-methods/set")
	public ApiResponse<Void> setPayMethods(@Valid @RequestBody HousePayMethodSetRequest request,
			@AuthenticationPrincipal AuthUser user) {
		houseService.setPayMethods(request, user);
		return ApiResponse.ok();
	}

	@Operation(summary = "고택 삭제", description = "판매자 본인 소유(soft delete).")
	@PreAuthorize("hasAuthority('HOUSE_MNG')")
	@PostMapping("/delete")
	public ApiResponse<Void> delete(@Valid @RequestBody HouseDeleteRequest request,
			@AuthenticationPrincipal AuthUser user) {
		houseService.delete(request.houseSn(), user);
		return ApiResponse.ok();
	}

	@Operation(summary = "고택 편의시설 조회", description = "판매자 본인 소유 고택의 선택된 편의시설(사용중).")
	@PreAuthorize("hasAuthority('HOUSE_MNG')")
	@GetMapping("/amenities")
	public ApiResponse<List<AmenityResponse>> amenities(@RequestParam("houseSn") Long houseSn,
			@AuthenticationPrincipal AuthUser user) {
		return ApiResponse.ok(houseAmenityService.listByHouse(houseSn, user));
	}

	@Operation(summary = "고택 편의시설 설정", description = "판매자 본인. 선택 집합 전체 교체(빈 배열=전부 해제).")
	@PreAuthorize("hasAuthority('HOUSE_MNG')")
	@PostMapping("/amenities/set")
	public ApiResponse<Void> setAmenities(@Valid @RequestBody HouseAmenitySetRequest request,
			@AuthenticationPrincipal AuthUser user) {
		houseAmenityService.setAmenities(request, user);
		return ApiResponse.ok();
	}
}
