package egovframework.eeum.dashboard.web;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import egovframework.eeum.base.response.ApiResponse;
import egovframework.eeum.dashboard.service.OperatorDashboardService;
import egovframework.eeum.dashboard.web.dto.OperatorDashboardResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * 운영자 대시보드 API — 운영자(OPERATOR) 전용. 현재 더미 데이터.
 *
 * <p>{@code CHNL_LOGIN_OPERATOR}(운영자 채널) 보유자만 접근. KPI/매출/정산/파트너/승인대기 일괄 반환.
 */
@Tag(name = "operator-dashboard", description = "운영자 대시보드")
@RestController
@RequestMapping("/operator/dashboard")
@RequiredArgsConstructor
public class OperatorDashboardController {

	private final OperatorDashboardService operatorDashboardService;

	@Operation(summary = "운영자 대시보드 조회", description = "KPI·매출추이·지역비중·정산·파트너현황·승인대기 일괄(현재 더미).")
	@PreAuthorize("hasAuthority('CHNL_LOGIN_OPERATOR')")
	@GetMapping
	public ApiResponse<OperatorDashboardResponse> dashboard() {
		return ApiResponse.ok(operatorDashboardService.getDashboard());
	}
}
