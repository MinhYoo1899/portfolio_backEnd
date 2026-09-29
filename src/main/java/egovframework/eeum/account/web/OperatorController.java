package egovframework.eeum.account.web;

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

import egovframework.eeum.account.service.operator.OperatorService;
import egovframework.eeum.account.web.dto.OperatorCreatedResponse;
import egovframework.eeum.account.web.dto.OperatorDetailResponse;
import egovframework.eeum.account.web.dto.OperatorInsertRequest;
import egovframework.eeum.account.web.dto.OperatorListItemResponse;
import egovframework.eeum.account.web.dto.OperatorPasswordResetRequest;
import egovframework.eeum.account.web.dto.OperatorRoleChangeRequest;
import egovframework.eeum.account.web.dto.OperatorSearchRequest;
import egovframework.eeum.account.web.dto.OperatorSetupPasswordRequest;
import egovframework.eeum.account.web.dto.OperatorStatusChangeRequest;
import egovframework.eeum.account.web.dto.OperatorUpdateRequest;
import egovframework.eeum.auth.vo.AuthUser;
import egovframework.eeum.base.exception.BusinessException;
import egovframework.eeum.base.exception.CommonErrorCode;
import egovframework.eeum.base.response.ApiResponse;
import egovframework.eeum.base.response.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 운영자 계정관리 API — 직원 생성/조회/역할·상태 변경/비번 초기화(권한코드 ACNT_MNG) + 비밀번호 설정(비인증).
 *
 * <p>대상은 항상 USER_TYPE_CD='OPERATOR'(서비스가 강제). 비OPERATOR 계정은 "없는 것처럼" 404 로 가린다.
 * 역할/권한은 서버가 허용역할(STAFF/SUPERVISOR)만 매핑 — 요청 body 의 raw ROLE_CD/AUTH_CD 신뢰 금지.
 */
@Tag(name = "operator", description = "운영자 계정관리 (직원 생성/역할·상태/비번)")
@RestController
@RequestMapping("/operators")
@RequiredArgsConstructor
public class OperatorController {

	private final OperatorService operatorService;

	@Operation(summary = "운영자(직원) 생성", description = "계정(PENDING, 비번 미설정)+상세+역할 생성 후 1회성 설정토큰/URL 발급. "
			+ "역할은 STAFF/SUPERVISOR 만(SUPERVISOR 면 STAFF 동시부여). 권한코드 ACNT_MNG.")
	@PreAuthorize("hasAuthority('ACNT_MNG')")
	@PostMapping("/insert")
	public ResponseEntity<ApiResponse<OperatorCreatedResponse>> insert(@AuthenticationPrincipal AuthUser user,
			@Valid @RequestBody OperatorInsertRequest request) {
		OperatorCreatedResponse result = operatorService.insert(request, requireUser(user).userSn());
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(result));
	}

	@Operation(summary = "운영자 목록", description = "USER_TYPE_CD='OPERATOR' 만, 상태/역할 필터 + 서버페이징. 권한코드 ACNT_MNG.")
	@PreAuthorize("hasAuthority('ACNT_MNG')")
	@GetMapping("/list")
	public ApiResponse<PageResponse<OperatorListItemResponse>> list(@Valid OperatorSearchRequest request) {
		return ApiResponse.ok(operatorService.list(request));
	}

	@Operation(summary = "운영자 상세", description = "대상이 OPERATOR 아니면 404. 연락처는 마스킹. 권한코드 ACNT_MNG.")
	@PreAuthorize("hasAuthority('ACNT_MNG')")
	@GetMapping("/detail")
	public ApiResponse<OperatorDetailResponse> detail(@RequestParam("userSn") String userSn) {
		return ApiResponse.ok(operatorService.detail(parseSn(userSn)));
	}

	@Operation(summary = "운영자 상세 수정", description = "부서/직위/연락처 수정. 생략 필드는 기존값 보존. "
			+ "전화번호는 서버가 정규화·암호화·해시(생략 시 보존). 대상이 OPERATOR 아니면 404, SUPER 는 보호(403). 권한코드 ACNT_MNG.")
	@PreAuthorize("hasAuthority('ACNT_MNG')")
	@PostMapping("/update")
	public ApiResponse<Void> update(@AuthenticationPrincipal AuthUser user,
			@Valid @RequestBody OperatorUpdateRequest request) {
		operatorService.update(request, requireUser(user).userSn());
		return ApiResponse.ok();
	}

	@Operation(summary = "운영자 역할 변경", description = "STAFF↔SUPERVISOR. SUPERVISOR 부여 시 STAFF 동시 유지, 강등 시 STAFF 잔류. "
			+ "마지막 활성 SUPERVISOR 강등 거부(409). 권한코드 ACNT_MNG.")
	@PreAuthorize("hasAuthority('ACNT_MNG')")
	@PostMapping("/role")
	public ApiResponse<Void> changeRole(@AuthenticationPrincipal AuthUser user,
			@Valid @RequestBody OperatorRoleChangeRequest request) {
		operatorService.changeRole(request, requireUser(user).userSn());
		return ApiResponse.ok();
	}

	@Operation(summary = "운영자 상태 변경", description = "활성(ACTIVE)/비활성·잠금(SUSPENDED). 자기 자신·SUPER·마지막 SUPERVISOR 보호. "
			+ "비활성화 시 세션 즉시 무효화. 권한코드 ACNT_MNG.")
	@PreAuthorize("hasAuthority('ACNT_MNG')")
	@PostMapping("/status")
	public ApiResponse<Void> changeStatus(@AuthenticationPrincipal AuthUser user,
			@Valid @RequestBody OperatorStatusChangeRequest request) {
		operatorService.changeStatus(request, requireUser(user).userSn());
		return ApiResponse.ok();
	}

	@Operation(summary = "운영자 비밀번호 초기화", description = "대상 운영자에 설정토큰 재발급(1회 노출) + 세션 무효화. 권한코드 ACNT_MNG.")
	@PreAuthorize("hasAuthority('ACNT_MNG')")
	@PostMapping("/password-reset")
	public ApiResponse<OperatorCreatedResponse> resetPassword(@AuthenticationPrincipal AuthUser user,
			@Valid @RequestBody OperatorPasswordResetRequest request) {
		return ApiResponse.ok(operatorService.resetPassword(request, requireUser(user).userSn()));
	}

	@Operation(summary = "운영자 비밀번호 설정(비인증)", description = "설정토큰 + 새 비밀번호 → ENPSWD 설정 + PENDING→ACTIVE, 토큰 1회 소비. "
			+ "생성/초기화로 발급된 토큰 보유자만 호출(사전 인증 불요).")
	@PostMapping("/setup-password")
	public ApiResponse<Void> setupPassword(@Valid @RequestBody OperatorSetupPasswordRequest request) {
		operatorService.setupPassword(request);
		return ApiResponse.ok();
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
}
