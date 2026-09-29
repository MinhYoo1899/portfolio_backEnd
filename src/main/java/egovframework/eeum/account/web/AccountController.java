package egovframework.eeum.account.web;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import egovframework.eeum.account.web.dto.AccountDeleteRequest;
import egovframework.eeum.account.web.dto.AccountDetailResponse;
import egovframework.eeum.account.web.dto.AccountInsertRequest;
import egovframework.eeum.account.web.dto.AccountUpdateRequest;
import egovframework.eeum.account.web.dto.PasswordChangeRequest;
import egovframework.eeum.account.web.dto.ProfileImageResponse;
import egovframework.eeum.account.service.account.AccountService;
import egovframework.eeum.account.service.account.ProfileImageService;
import egovframework.eeum.auth.vo.AuthUser;
import egovframework.eeum.base.exception.BusinessException;
import egovframework.eeum.base.response.ApiResponse;
import egovframework.eeum.base.security.RefreshCookieSupport;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 계정 API — 일반회원 가입 / 본인 조회·수정·탈퇴.
 * (조회/수정/탈퇴 대상 = 항상 토큰의 본인 — 타인 계정 관리는 운영자 기능에서 별도)
 */
@Tag(name = "account", description = "계정 (가입/본인 조회·수정·탈퇴)")
@RestController
@RequestMapping("/accounts")
@RequiredArgsConstructor
public class AccountController {

	private final AccountService accountService;
	private final ProfileImageService profileImageService;
	private final RefreshCookieSupport refreshCookieSupport;

	@Operation(summary = "일반회원 가입", description = "이메일 가입. 시행 중 필수약관 전부 동의(Y) 필요. 가입 직후 ACTIVE(전화 본인인증 연동 전 임시 정책).")
	@PostMapping("/insert")
	public ResponseEntity<ApiResponse<AccountDetailResponse>> insert(
			@Valid @RequestBody AccountInsertRequest request, HttpServletRequest httpRequest) {
		AccountDetailResponse result = accountService.insert(request, httpRequest.getRemoteAddr(), userAgent(httpRequest));
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(result));
	}

	@Operation(summary = "본인 계정 상세", description = "access 토큰 기준 본인 정보.")
	@GetMapping("/detail")
	public ApiResponse<AccountDetailResponse> detail(@AuthenticationPrincipal AuthUser user) {
		return ApiResponse.ok(accountService.detail(requireUser(user).userSn()));
	}

	@Operation(summary = "내 동의 현황", description = "약관 유형별 현재 동의상태(유형당 최신 1건 — 동의/철회·버전·시각). 개인정보 동의 접근권.")
	@GetMapping("/consents")
	public ApiResponse<java.util.List<egovframework.eeum.account.web.dto.ConsentStatusResponse>> consents(
			@AuthenticationPrincipal AuthUser user) {
		return ApiResponse.ok(accountService.getMyConsents(requireUser(user).userSn()));
	}

	@Operation(summary = "선택약관 동의 변경", description = "시행 중 선택약관(마케팅 등)의 동의/철회. 필수약관은 변경 불가(400). "
			+ "변경은 새 동의이력 행으로 기록(append-only, 증적 보존) — IP/User-Agent 함께 기록.")
	@PostMapping("/consents/change")
	public ApiResponse<Void> changeConsent(@AuthenticationPrincipal AuthUser user,
			@Valid @RequestBody egovframework.eeum.account.web.dto.ConsentChangeRequest request,
			HttpServletRequest httpRequest) {
		accountService.changeConsent(requireUser(user).userSn(), request, httpRequest.getRemoteAddr(), userAgent(httpRequest));
		return ApiResponse.ok();
	}

	@Operation(summary = "본인 정보 수정", description = "이름/별명 수정 (대상 = 본인).")
	@PostMapping("/update")
	public ApiResponse<Void> update(@AuthenticationPrincipal AuthUser user,
			@Valid @RequestBody AccountUpdateRequest request) {
		accountService.update(requireUser(user).userSn(), request);
		return ApiResponse.ok();
	}

	@Operation(summary = "회원 탈퇴", description = "비밀번호 본인확인 후 soft delete + 전체 세션 회수(refresh+access 세대 무효화). 성공 시 refresh 쿠키도 만료됩니다.")
	@PostMapping("/delete")
	public ApiResponse<Void> delete(@AuthenticationPrincipal AuthUser user,
			@Valid @RequestBody AccountDeleteRequest request, HttpServletResponse httpResponse) {
		accountService.delete(requireUser(user).userSn(), request);
		refreshCookieSupport.expireRefreshCookie(httpResponse); // 서버는 무효화됐고, 클라의 죽은 쿠키도 정리(codex SHOULD ⑥)
		return ApiResponse.ok();
	}

	@Operation(summary = "비밀번호 변경", description = "로그인 상태에서 현재 비밀번호 확인 후 새 비밀번호(8~64자, 영문·숫자·특수문자 중 2종 이상)로 변경합니다. "
			+ "성공 시 모든 세션(refresh + 기존 access)이 즉시 무효화되어 재로그인이 필요합니다. 소셜 전용 계정은 변경 대상이 아닙니다(409).")
	@PostMapping("/password")
	public ApiResponse<Void> changePassword(@AuthenticationPrincipal AuthUser user,
			@Valid @RequestBody PasswordChangeRequest request, HttpServletResponse httpResponse) {
		accountService.changePassword(requireUser(user).userSn(), request);
		refreshCookieSupport.expireRefreshCookie(httpResponse);
		return ApiResponse.ok();
	}

	@Operation(summary = "프로필 이미지 업로드/교체",
			description = "멀티파트 업로드. 본인 인증만 필요(FILE_UPLOAD 권한 불요). jpg/jpeg/png·5MB. "
					+ "EXIF/GPS 제거 재인코딩 후 저장하고 USER_DTL_INFO.PRFL_IMG_FILE_SN 을 교체(이전 파일 soft delete). 대상=본인.")
	@PostMapping("/profile-image")
	public ResponseEntity<ApiResponse<ProfileImageResponse>> profileImage(
			@AuthenticationPrincipal AuthUser user,
			@RequestParam("file") MultipartFile file) {
		ProfileImageResponse result = profileImageService.replace(requireUser(user).userSn(), file);
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(result));
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
