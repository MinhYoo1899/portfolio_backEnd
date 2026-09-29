package egovframework.eeum.account.web;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import egovframework.eeum.account.service.partner.PartnerService;
import egovframework.eeum.account.web.dto.PartnerSelfResponse;
import egovframework.eeum.auth.vo.AuthUser;
import egovframework.eeum.base.exception.BusinessException;
import egovframework.eeum.base.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * 파트너 본인(PTNS 콘솔) API — {@code /partner/...}.
 *
 * <p>운영자용 {@code /partners}(PRTNR_VIEW/PRTNR_APRV)와 <b>분리</b>한 채널. 권한은 {@code HOUSE_MNG}(PARTNER 전용)로,
 * 운영자/회원은 접근 불가. 본인 소유권은 <b>토큰 userSn 으로만</b> 해석하며 prtnrSn 을 파라미터로 받지 않는다(IDOR 차단).
 */
@Tag(name = "partner-self", description = "파트너 본인 (PTNS 콘솔 — 내 사업자 정보)")
@RestController
@RequestMapping("/partner")
@RequiredArgsConstructor
public class PartnerSelfController {

	private final PartnerService partnerService;

	@Operation(summary = "내 파트너 정보 조회",
			description = "APPROVED+ACTIVE 파트너 본인. 운영자/회원 접근 불가(HOUSE_MNG). 담당자 연락처는 마스킹값만 반환. "
					+ "비승인/정지/해석실패는 403, 미인증은 401.")
	@PreAuthorize("hasAuthority('HOUSE_MNG')")
	@GetMapping("/me")
	public ApiResponse<PartnerSelfResponse> me(@AuthenticationPrincipal AuthUser user) {
		if (user == null) {
			throw BusinessException.unauthorized();
		}
		return ApiResponse.ok(partnerService.me(user.userSn()));
	}
}
