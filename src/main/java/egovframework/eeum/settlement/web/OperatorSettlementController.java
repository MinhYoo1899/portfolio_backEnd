package egovframework.eeum.settlement.web;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import egovframework.eeum.auth.vo.AuthUser;
import egovframework.eeum.base.response.ApiResponse;
import egovframework.eeum.settlement.service.settlement.SettlementService;
import egovframework.eeum.settlement.web.dto.PartnerSettlementExecuteRequest;
import egovframework.eeum.settlement.web.dto.PartnerSettlementResponse;
import egovframework.eeum.settlement.web.dto.SettlementSettingResponse;
import egovframework.eeum.settlement.web.dto.SettlementSettingUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 운영자 정산 — 설정 조회=SETTLEMENT_VIEW, 저장/초기화/실행=SETTLEMENT_MNG(SUPERVISOR 전용).
 *
 * <p>정산 설정(수수료율·정산기준/주기·입금예정일) 조회/저장/초기화 + 회차별 파트너 판매집계 목록·정산 실행.
 * 판매집계는 APPROVED 결제·체크아웃월 기준. 실제 계좌이체는 오프라인(시스템 상태만 기록).
 */
@Tag(name = "operator-settlement", description = "운영자 정산 설정 조회/저장/초기화·파트너 정산 목록/실행")
@RestController
@RequestMapping("/operator/settlements")
@RequiredArgsConstructor
public class OperatorSettlementController {

	private final SettlementService settlementService;

	@Operation(summary = "정산 설정 조회(운영자)", description = "권한 SETTLEMENT_VIEW. 수수료율·정산기준/주기·입금예정일.")
	@PreAuthorize("hasAuthority('SETTLEMENT_VIEW')")
	@GetMapping("/settings")
	public ApiResponse<SettlementSettingResponse> getSettings() {
		return ApiResponse.ok(settlementService.getSettings());
	}

	@Operation(summary = "정산 설정 저장(운영자)", description = "권한 SETTLEMENT_MNG. 전체 저장. 수수료율 0~100·정산기준/주기 코드 검증.")
	@PreAuthorize("hasAuthority('SETTLEMENT_MNG')")
	@PutMapping("/settings")
	public ApiResponse<SettlementSettingResponse> updateSettings(
			@Valid @RequestBody SettlementSettingUpdateRequest request, @AuthenticationPrincipal AuthUser user) {
		return ApiResponse.ok(settlementService.updateSettings(request, user));
	}

	@Operation(summary = "정산 설정 초기화(운영자)", description = "권한 SETTLEMENT_MNG. 서버 기본값(플랫폼15%·카드3%·부가세10%·월정산) 복원.")
	@PreAuthorize("hasAuthority('SETTLEMENT_MNG')")
	@PostMapping("/settings/reset")
	public ApiResponse<SettlementSettingResponse> resetSettings(@AuthenticationPrincipal AuthUser user) {
		return ApiResponse.ok(settlementService.resetSettings(user));
	}

	@Operation(summary = "파트너 정산 목록(운영자)", description = "권한 SETTLEMENT_VIEW. 회차(YYYY-MM) 파트너별 판매집계·산출수수료·정산상태.")
	@PreAuthorize("hasAuthority('SETTLEMENT_VIEW')")
	@GetMapping("/partners")
	public ApiResponse<List<PartnerSettlementResponse>> listPartnerSettlements(
			@RequestParam("roundYm") String roundYm) {
		return ApiResponse.ok(settlementService.listPartnerSettlements(roundYm));
	}

	@Operation(summary = "파트너 정산 실행(운영자)", description = "권한 SETTLEMENT_MNG. 회차·파트너 스냅샷 기록(COMPLETED). 중복 실행 차단.")
	@PreAuthorize("hasAuthority('SETTLEMENT_MNG')")
	@PostMapping("/partners/execute")
	public ApiResponse<PartnerSettlementResponse> executePartnerSettlement(
			@Valid @RequestBody PartnerSettlementExecuteRequest request, @AuthenticationPrincipal AuthUser user) {
		return ApiResponse.ok(settlementService.executePartnerSettlement(request, user));
	}
}
