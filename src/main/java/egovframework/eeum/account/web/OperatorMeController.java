package egovframework.eeum.account.web;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import egovframework.eeum.account.service.operator.OperatorService;
import egovframework.eeum.account.web.dto.OperatorMeResponse;
import egovframework.eeum.auth.vo.AuthUser;
import egovframework.eeum.base.exception.BusinessException;
import egovframework.eeum.base.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * 운영자 <b>본인</b> 셀프 API — {@code /operator/me}. 대상은 항상 토큰의 본인(타인 계정관리는 {@code /operators/**}).
 *
 * <p>인증만 필요(특정 권한코드 불요) — 서비스가 대상이 OPERATOR 인지 검증(아니면 404). 연락처는 본인 데이터라 전체 노출.
 */
@Tag(name = "operator-me", description = "운영자 본인 셀프(내 정보)")
@RestController
@RequestMapping("/operator/me")
@RequiredArgsConstructor
public class OperatorMeController {

	private final OperatorService operatorService;

	@Operation(summary = "본인 정보 조회", description = "access 토큰 기준 본인(운영자) 정보 — 이메일/이름/부서/직위/연락처(전체)/역할/가입일.")
	@GetMapping
	public ApiResponse<OperatorMeResponse> me(@AuthenticationPrincipal AuthUser user) {
		return ApiResponse.ok(operatorService.me(requireUser(user).userSn()));
	}

	private static AuthUser requireUser(AuthUser user) {
		if (user == null) {
			throw BusinessException.unauthorized();
		}
		return user;
	}
}
