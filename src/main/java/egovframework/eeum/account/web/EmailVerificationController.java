package egovframework.eeum.account.web;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import egovframework.eeum.account.service.email.EmailVerificationService;
import egovframework.eeum.account.web.dto.EmailVerifyRequest;
import egovframework.eeum.auth.vo.AuthUser;
import egovframework.eeum.base.exception.BusinessException;
import egovframework.eeum.base.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 이메일 소유확인 API — 본인(access 토큰) 대상 6자리 코드 발급/검증.
 * (가입 직후 자동 발송과 별개로, 사용자가 재발송/검증을 직접 호출할 수 있다.)
 */
@Tag(name = "account-email", description = "이메일 소유확인(인증코드 발급/검증)")
@RestController
@RequestMapping("/accounts/email")
@RequiredArgsConstructor
public class EmailVerificationController {

	private final EmailVerificationService emailVerificationService;

	@Operation(summary = "이메일 인증코드 발송 요청",
			description = "본인 이메일로 6자리 인증코드를 발송합니다. 이미 검증된 계정은 409. 재발송 쿨다운/일일 한도 초과 시 429.")
	@PostMapping("/verify-request")
	public ApiResponse<Void> requestVerification(@AuthenticationPrincipal AuthUser user) {
		emailVerificationService.requestVerification(requireUser(user).userSn());
		return ApiResponse.ok();
	}

	@Operation(summary = "이메일 인증코드 검증",
			description = "발급받은 6자리 코드를 검증합니다. 성공 시 이메일 검증 완료 처리. 불일치 400 / 만료 410 / 시도초과 429.")
	@PostMapping("/verify")
	public ApiResponse<Void> verify(@AuthenticationPrincipal AuthUser user,
			@Valid @RequestBody EmailVerifyRequest request) {
		emailVerificationService.verify(requireUser(user).userSn(), request.code());
		return ApiResponse.ok();
	}

	private static AuthUser requireUser(AuthUser user) {
		if (user == null) {
			throw BusinessException.unauthorized();
		}
		return user;
	}
}
