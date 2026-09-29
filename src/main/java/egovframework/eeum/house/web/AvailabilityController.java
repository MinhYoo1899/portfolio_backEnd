package egovframework.eeum.house.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import egovframework.eeum.base.response.ApiResponse;
import egovframework.eeum.house.service.availability.AvailabilityService;
import egovframework.eeum.house.web.dto.AvailabilityResponse;
import egovframework.eeum.house.web.dto.AvailabilitySearchRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 가용성 조회 API(손님) — 고택 + 체크인/아웃 + 인원으로 예약가능 객실과 가격합계 조회.
 *
 * <p>로그인 사용자 누구나(소유권 무관, 별도 권한 불요). ACTIVE 고택·객실만 노출.
 */
@Tag(name = "availability", description = "예약 가용성 조회")
@RestController
@RequestMapping("/availability")
@RequiredArgsConstructor
public class AvailabilityController {

	private final AvailabilityService availabilityService;

	@Operation(summary = "가용성 조회", description = "고택의 ACTIVE 객실 중 기간·인원에 예약 가능한 객실과 가격합계.")
	@GetMapping
	public ApiResponse<AvailabilityResponse> search(@Valid AvailabilitySearchRequest request) {
		return ApiResponse.ok(availabilityService.search(request));
	}
}
