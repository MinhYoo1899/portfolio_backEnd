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
 * 운영자 대행 객실 API — 운영자(HOUSE_PROXY_MNG)가 파트너 고택의 객실을 대행 관리.
 *
 * <p>소유자(PRTNR_SN)는 대상 고택/객실에서 서버가 해석(요청에 prtnrSn 없음 — IDOR 차단). 행위자(MDFR_SN)=운영자.
 * 파트너용 {@code /rooms}(HOUSE_MNG)와 분리하고 서비스 코어는 공유. RoomController 미러.
 */
@Tag(name = "operator-room", description = "운영자 대행 (객실 관리)")
@RestController
@RequestMapping("/operator/rooms")
@RequiredArgsConstructor
public class OperatorRoomController {

	private final RoomService roomService;
	private final RoomAmenityService roomAmenityService;
	private final RoomOptionService roomOptionService;
	private final RoomClimateService roomClimateService;

	@Operation(summary = "객실 등록(운영자 대행)", description = "권한 HOUSE_PROXY_MNG. 대상 고택 owner 서버 해석. DRAFT 로 생성.")
	@PreAuthorize("hasAuthority('HOUSE_PROXY_MNG')")
	@PostMapping("/insert")
	public ResponseEntity<ApiResponse<RoomResponse>> insert(
			@Valid @RequestBody RoomInsertRequest request, @AuthenticationPrincipal AuthUser user) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiResponse.ok(roomService.insertAsOperator(request, user)));
	}

	@Operation(summary = "고택별 객실 목록(운영자 대행)", description = "권한 HOUSE_PROXY_MNG.")
	@PreAuthorize("hasAuthority('HOUSE_PROXY_MNG')")
	@GetMapping("/list")
	public ApiResponse<List<RoomResponse>> list(@RequestParam("houseSn") Long houseSn,
			@AuthenticationPrincipal AuthUser user) {
		return ApiResponse.ok(roomService.listByHouseAsOperator(houseSn, user));
	}

	@Operation(summary = "객실 상세(운영자 대행)", description = "권한 HOUSE_PROXY_MNG.")
	@PreAuthorize("hasAuthority('HOUSE_PROXY_MNG')")
	@GetMapping("/detail")
	public ApiResponse<RoomResponse> detail(@RequestParam("roomSn") Long roomSn,
			@AuthenticationPrincipal AuthUser user) {
		return ApiResponse.ok(roomService.detailAsOperator(roomSn, user));
	}

	@Operation(summary = "객실 수정(운영자 대행)", description = "권한 HOUSE_PROXY_MNG.")
	@PreAuthorize("hasAuthority('HOUSE_PROXY_MNG')")
	@PostMapping("/update")
	public ApiResponse<Void> update(@Valid @RequestBody RoomUpdateRequest request,
			@AuthenticationPrincipal AuthUser user) {
		roomService.updateAsOperator(request, user);
		return ApiResponse.ok();
	}

	@Operation(summary = "객실 상태변경(운영자 대행)", description = "권한 HOUSE_PROXY_MNG. DRAFT/ACTIVE/HIDDEN.")
	@PreAuthorize("hasAuthority('HOUSE_PROXY_MNG')")
	@PostMapping("/status")
	public ApiResponse<Void> changeStatus(@Valid @RequestBody RoomStatusChangeRequest request,
			@AuthenticationPrincipal AuthUser user) {
		roomService.changeStatusAsOperator(request, user);
		return ApiResponse.ok();
	}

	@Operation(summary = "객실 삭제(운영자 대행)", description = "권한 HOUSE_PROXY_MNG. soft delete.")
	@PreAuthorize("hasAuthority('HOUSE_PROXY_MNG')")
	@PostMapping("/delete")
	public ApiResponse<Void> delete(@Valid @RequestBody RoomDeleteRequest request,
			@AuthenticationPrincipal AuthUser user) {
		roomService.deleteAsOperator(request.roomSn(), user);
		return ApiResponse.ok();
	}

	@Operation(summary = "객실 침구 설정(운영자 대행, 교체)", description = "권한 HOUSE_PROXY_MNG. 타입은 COMM_CD BEDDING_TYPE.")
	@PreAuthorize("hasAuthority('HOUSE_PROXY_MNG')")
	@PostMapping("/beddings/set")
	public ApiResponse<List<RoomBeddingResponse>> setBeddings(@Valid @RequestBody RoomBeddingSetRequest request,
			@AuthenticationPrincipal AuthUser user) {
		return ApiResponse.ok(roomService.setBeddingsAsOperator(request, user));
	}

	@Operation(summary = "객실 편의시설 조회(운영자 대행)", description = "권한 HOUSE_PROXY_MNG. 대상 객실의 선택된 편의시설(사용중).")
	@PreAuthorize("hasAuthority('HOUSE_PROXY_MNG')")
	@GetMapping("/amenities")
	public ApiResponse<List<AmenityResponse>> amenities(@RequestParam("roomSn") Long roomSn,
			@AuthenticationPrincipal AuthUser user) {
		return ApiResponse.ok(roomAmenityService.listByRoomAsOperator(roomSn, user));
	}

	@Operation(summary = "객실 편의시설 설정(운영자 대행, 교체)", description = "권한 HOUSE_PROXY_MNG. 선택 집합 전체 교체(빈 배열=전부 해제).")
	@PreAuthorize("hasAuthority('HOUSE_PROXY_MNG')")
	@PostMapping("/amenities/set")
	public ApiResponse<Void> setAmenities(@Valid @RequestBody RoomAmenitySetRequest request,
			@AuthenticationPrincipal AuthUser user) {
		roomAmenityService.setAmenitiesAsOperator(request, user);
		return ApiResponse.ok();
	}

	@Operation(summary = "객실 추가옵션 설정(운영자 대행, 교체)", description = "권한 HOUSE_PROXY_MNG. 기존 활성 옵션을 비우고 재구성. 과금=예약당 1회.")
	@PreAuthorize("hasAuthority('HOUSE_PROXY_MNG')")
	@PostMapping("/options/set")
	public ApiResponse<List<RoomOptionResponse>> setOptions(@Valid @RequestBody RoomOptionSetRequest request,
			@AuthenticationPrincipal AuthUser user) {
		return ApiResponse.ok(roomOptionService.setOptionsAsOperator(request, user));
	}

	@Operation(summary = "객실 냉난방시설 설정(운영자 대행, 교체)", description = "권한 HOUSE_PROXY_MNG. 냉방/난방 각각 다중선택. 코드는 COMM_CD COOLING_TYPE/HEATING_TYPE. 둘 다 빈 배열=전부 해제.")
	@PreAuthorize("hasAuthority('HOUSE_PROXY_MNG')")
	@PostMapping("/climate/set")
	public ApiResponse<Void> setClimate(@Valid @RequestBody RoomClimateSetRequest request,
			@AuthenticationPrincipal AuthUser user) {
		roomClimateService.setClimateAsOperator(request, user);
		return ApiResponse.ok();
	}
}
