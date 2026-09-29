package egovframework.eeum.house.web;

import java.util.List;

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
import egovframework.eeum.house.service.amenity.HouseAmenityService;
import egovframework.eeum.house.web.dto.AmenityResponse;
import egovframework.eeum.house.web.dto.HouseAmenitySetRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 운영자 대행 고택 편의시설 API — 운영자(HOUSE_PROXY_MNG)가 파트너 고택의 편의시설 선택을 대행 관리.
 *
 * <p>소유자(PRTNR_SN)는 대상 고택에서 서버가 해석(요청에 prtnrSn 없음 — IDOR 차단). 행위자(MDFR_SN)=운영자.
 * 파트너용 {@code /houses/amenities}(HOUSE_MNG)와 분리하고 서비스 코어는 공유.
 * 편의시설 마스터 카탈로그 선택목록은 인증 사용자 누구나 호출 가능한 {@code GET /amenities}(AmenityController) 사용.
 */
@Tag(name = "operator-house-amenity", description = "운영자 대행 (고택 편의시설 관리)")
@RestController
@RequestMapping("/operator/houses/amenities")
@RequiredArgsConstructor
public class OperatorHouseAmenityController {

	private final HouseAmenityService houseAmenityService;

	@Operation(summary = "고택 편의시설 조회(운영자 대행)", description = "권한 HOUSE_PROXY_MNG. 대상 고택의 선택된 편의시설(사용중).")
	@PreAuthorize("hasAuthority('HOUSE_PROXY_MNG')")
	@GetMapping
	public ApiResponse<List<AmenityResponse>> list(@RequestParam("houseSn") Long houseSn,
			@AuthenticationPrincipal AuthUser user) {
		return ApiResponse.ok(houseAmenityService.listByHouseAsOperator(houseSn, user));
	}

	@Operation(summary = "고택 편의시설 설정(운영자 대행)", description = "권한 HOUSE_PROXY_MNG. 선택 집합 전체 교체(빈 배열=전부 해제).")
	@PreAuthorize("hasAuthority('HOUSE_PROXY_MNG')")
	@PostMapping("/set")
	public ApiResponse<Void> setAmenities(@Valid @RequestBody HouseAmenitySetRequest request,
			@AuthenticationPrincipal AuthUser user) {
		houseAmenityService.setAmenitiesAsOperator(request, user);
		return ApiResponse.ok();
	}
}
