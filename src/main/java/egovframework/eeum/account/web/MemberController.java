package egovframework.eeum.account.web;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import egovframework.eeum.account.service.member.MemberService;
import egovframework.eeum.account.web.dto.MemberDetailResponse;
import egovframework.eeum.account.web.dto.MemberListItemResponse;
import egovframework.eeum.account.web.dto.MemberSearchRequest;
import egovframework.eeum.account.web.dto.MemberStatusChangeRequest;
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
 * 일반회원 관리 API(운영자 콘솔). 대상은 USER_TYPE_CD='MEMBER' 고정(서비스/매퍼 격리).
 *
 * <p>조회=MEMBER_VIEW, 상태관리=MEMBER_MNG. 목록은 개인정보 마스킹, 상세는 전체노출(조회 감사).
 */
@Tag(name = "member", description = "운영자 — 일반회원 조회/상태관리")
@RestController
@RequestMapping("/members")
@RequiredArgsConstructor
public class MemberController {

	private final MemberService memberService;

	@Operation(summary = "회원 목록(운영자)", description = "권한 MEMBER_VIEW. 상태/검색어 필터 + 서버사이드 페이징. 이메일·휴대폰 마스킹.")
	@PreAuthorize("hasAuthority('MEMBER_VIEW')")
	@GetMapping
	public ApiResponse<PageResponse<MemberListItemResponse>> list(@Valid MemberSearchRequest request) {
		return ApiResponse.ok(memberService.list(request));
	}

	@Operation(summary = "회원 상세(운영자)", description = "권한 MEMBER_VIEW. 개인정보 전체 노출 — 조회 감사기록 대상.")
	@PreAuthorize("hasAuthority('MEMBER_VIEW')")
	@GetMapping("/detail")
	public ApiResponse<MemberDetailResponse> detail(@RequestParam("userSn") String userSn) {
		return ApiResponse.ok(memberService.detail(parseSn(userSn)));
	}

	@Operation(summary = "회원 상태변경(운영자)", description = "권한 MEMBER_MNG. 정지/해제/탈퇴처리. 사유 필수, 상태머신 강제.")
	@PreAuthorize("hasAuthority('MEMBER_MNG')")
	@PostMapping("/status")
	public ApiResponse<Void> changeStatus(@AuthenticationPrincipal AuthUser user,
			@Valid @RequestBody MemberStatusChangeRequest request) {
		memberService.changeStatus(request, user);
		return ApiResponse.ok();
	}

	private static long parseSn(String sn) {
		try {
			return Long.parseLong(sn);
		} catch (NumberFormatException e) {
			throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, "식별자 형식이 올바르지 않습니다.");
		}
	}
}
