package egovframework.eeum.account.web;

import org.springframework.http.HttpHeaders;
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

import egovframework.eeum.account.web.dto.PartnerApproveRequest;
import egovframework.eeum.account.web.dto.PartnerDetailResponse;
import egovframework.eeum.account.web.dto.PartnerInsertRequest;
import egovframework.eeum.account.web.dto.PartnerListItemResponse;
import egovframework.eeum.account.web.dto.PartnerPendingCountResponse;
import egovframework.eeum.account.web.dto.PartnerRejectRequest;
import egovframework.eeum.account.web.dto.PartnerSearchRequest;
import egovframework.eeum.account.web.dto.PartnerUpdateRequest;
import egovframework.eeum.account.service.partner.PartnerService;
import egovframework.eeum.auth.vo.AuthUser;
import egovframework.eeum.base.exception.BusinessException;
import egovframework.eeum.base.exception.CommonErrorCode;
import egovframework.eeum.base.response.ApiResponse;
import egovframework.eeum.base.response.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 파트너 API — 사업자 가입 신청(공개) + 운영자 승인 워크플로우(권한코드 PRTNR_APRV) + 사업자정보 수정.
 */
@Tag(name = "partner", description = "파트너 (가입 신청/운영자 승인/정보 수정)")
@RestController
@RequestMapping("/partners")
@RequiredArgsConstructor
public class PartnerController {

	private final PartnerService partnerService;

	@Operation(summary = "파트너 가입 신청", description = "계정(PENDING)+사업자(REQUESTED) 생성. 운영자 승인 후 이용 가능. 필수약관 전부 동의 필요.")
	@PostMapping("/insert")
	public ResponseEntity<ApiResponse<PartnerDetailResponse>> insert(
			@Valid @RequestBody PartnerInsertRequest request, HttpServletRequest httpRequest) {
		PartnerDetailResponse result = partnerService.insert(request, httpRequest.getRemoteAddr(), userAgent(httpRequest));
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(result));
	}

	@Operation(summary = "파트너 목록(운영자)", description = "승인상태 필터 + 서버사이드 페이징. 권한코드 PRTNR_VIEW 필요(일반직원 조회 가능 — V8 조회/처리 분리).")
	@PreAuthorize("hasAuthority('PRTNR_VIEW')")
	@GetMapping("/list")
	public ApiResponse<PageResponse<PartnerListItemResponse>> list(@Valid PartnerSearchRequest request) {
		return ApiResponse.ok(partnerService.list(request));
	}

	@Operation(summary = "인증요청(승인대기) 파트너 수", description = "파트너 관리 헤더칩/사이드바 뱃지용 — aprvStts=REQUESTED 수. 권한코드 PRTNR_VIEW.")
	@PreAuthorize("hasAuthority('PRTNR_VIEW')")
	@GetMapping("/pending-count")
	public ApiResponse<PartnerPendingCountResponse> pendingCount() {
		return ApiResponse.ok(PartnerPendingCountResponse.of(partnerService.pendingCount()));
	}

	@Operation(summary = "파트너 상세(운영자)", description = "권한코드 PRTNR_VIEW 필요(일반직원 조회 가능 — V8 조회/처리 분리).")
	@PreAuthorize("hasAuthority('PRTNR_VIEW')")
	@GetMapping("/detail")
	public ApiResponse<PartnerDetailResponse> detail(@RequestParam("prtnrSn") String prtnrSn) {
		return ApiResponse.ok(partnerService.detail(parseSn(prtnrSn)));
	}

	@Operation(summary = "파트너 승인(운영자)", description = "REQUESTED→APPROVED + 계정 활성화. 이미 처리된 건은 409. 권한코드 PRTNR_APRV(수퍼바이저 전용).")
	@PreAuthorize("hasAuthority('PRTNR_APRV')")
	@PostMapping("/approve")
	public ApiResponse<Void> approve(@AuthenticationPrincipal AuthUser user,
			@Valid @RequestBody PartnerApproveRequest request) {
		partnerService.approve(request.prtnrSnAsLong(), requireUser(user).userSn());
		return ApiResponse.ok();
	}

	@Operation(summary = "파트너 반려(운영자)", description = "REQUESTED→REJECTED(사유 필수). 이미 처리된 건은 409. 권한코드 PRTNR_APRV(수퍼바이저 전용).")
	@PreAuthorize("hasAuthority('PRTNR_APRV')")
	@PostMapping("/reject")
	public ApiResponse<Void> reject(@AuthenticationPrincipal AuthUser user,
			@Valid @RequestBody PartnerRejectRequest request) {
		partnerService.reject(request.prtnrSnAsLong(), request.rjctRsn(), requireUser(user).userSn());
		return ApiResponse.ok();
	}

	@Operation(summary = "파트너 사업자정보 수정(운영자)",
			description = "권한코드 PRTNR_APRV. 사업자정보 부분수정(미포함/null 필드 보존, daddr 공백 클리어 가능). "
					+ "승인/계정 상태는 불변. brno 변경 시 사업자번호검증상태 초기화(REQUESTED). DEL_YN='N' 파트너만.")
	@PreAuthorize("hasAuthority('PRTNR_APRV')")
	@PostMapping("/update")
	public ApiResponse<PartnerDetailResponse> update(@AuthenticationPrincipal AuthUser user,
			@Valid @RequestBody PartnerUpdateRequest request) {
		return ApiResponse.ok(partnerService.updateInfo(request, requireUser(user).userSn()));
	}

	private static long parseSn(String sn) {
		try {
			return Long.parseLong(sn);
		} catch (NumberFormatException e) {
			throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, "식별자 형식이 올바르지 않습니다.");
		}
	}

	private static AuthUser requireUser(AuthUser user) {
		if (user == null) {
			throw BusinessException.unauthorized();
		}
		return user;
	}

	private static String userAgent(HttpServletRequest request) {
		String ua = request.getHeader(HttpHeaders.USER_AGENT);
		return ua != null && ua.length() > 500 ? ua.substring(0, 500) : ua;
	}
}
