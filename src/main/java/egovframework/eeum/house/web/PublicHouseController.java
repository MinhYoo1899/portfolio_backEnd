package egovframework.eeum.house.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import egovframework.eeum.base.response.ApiResponse;
import egovframework.eeum.base.response.PageResponse;
import egovframework.eeum.house.service.publichouse.PublicHouseService;
import egovframework.eeum.house.web.dto.AvailabilitySearchRequest;
import egovframework.eeum.house.web.dto.PublicAvailabilityResponse;
import egovframework.eeum.house.web.dto.PublicHouseDetailResponse;
import egovframework.eeum.house.web.dto.PublicHouseListItem;
import egovframework.eeum.house.web.dto.PublicHouseSearchRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 손님 공개 고택 브라우즈 API — 비로그인 공개(메인/검색/상세/가용검색). ACTIVE 고택만 노출.
 *
 * <p>소유권 무관. 비공개 고택은 전부 404 은닉. 등록/수정은 파트너 전용({@code /houses/*}).
 * 예약(생성/확정)은 회원 전용({@code /reservations}) — 구경은 공개, 이용은 로그인.
 */
@Tag(name = "public-house", description = "공개 고택 브라우즈(손님)")
@RestController
@RequestMapping("/public/houses")
@RequiredArgsConstructor
public class PublicHouseController {

	private final PublicHouseService publicHouseService;

	@Operation(summary = "공개 고택 목록", description = "ACTIVE 고택 목록(지역/대표이미지/인원). 키워드(고택명·주소) 검색, 오프셋 페이징.")
	@GetMapping("/list")
	public ApiResponse<PageResponse<PublicHouseListItem>> list(@Valid PublicHouseSearchRequest request) {
		return ApiResponse.ok(publicHouseService.list(request));
	}

	@Operation(summary = "공개 고택 상세", description = "이미지/편의시설/결제수단/객실요약/판매자 표시정보. ACTIVE 외(비공개·미승인 파트너 포함)는 404.")
	@GetMapping("/detail")
	public ApiResponse<PublicHouseDetailResponse> detail(@RequestParam("houseSn") Long houseSn) {
		return ApiResponse.ok(publicHouseService.detail(houseSn));
	}

	@Operation(summary = "공개 가용검색", description = "체크인/아웃([in,out) 반개구간)·인원으로 객실별 가용/사유/총액과 최저가. KST 과거 체크인 400, 비공개 고택 404.")
	@GetMapping("/availability")
	public ApiResponse<PublicAvailabilityResponse> availability(@Valid AvailabilitySearchRequest request) {
		return ApiResponse.ok(publicHouseService.availability(request));
	}
}
