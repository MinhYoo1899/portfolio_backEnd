package egovframework.eeum.terms.web;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import egovframework.eeum.base.response.ApiResponse;
import egovframework.eeum.terms.service.terms.TermsService;
import egovframework.eeum.terms.web.dto.TermsDetailRequest;
import egovframework.eeum.terms.web.dto.TermsDetailResponse;
import egovframework.eeum.terms.web.dto.TermsListItem;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 약관 API — 시행 중 약관 목록/상세 조회. 비로그인 호출 가능(가입 등 비로그인 화면에서도 필요).
 */
@Tag(name = "terms", description = "약관 (시행 중 약관 목록/상세 조회)")
@RestController
@RequestMapping("/terms")
@RequiredArgsConstructor
public class TermsController {

	private final TermsService termsService;

	@Operation(summary = "약관 목록", description = "시행 중 약관 목록(유형당 최신 1건, 본문 미포함). 본문은 detail 조회.")
	@GetMapping("/list")
	public ApiResponse<List<TermsListItem>> list() {
		List<TermsListItem> items = termsService.getEffectiveTerms().stream()
				.map(TermsListItem::from)
				.toList();
		return ApiResponse.ok(items);
	}

	@Operation(summary = "약관 상세", description = "시행 중 약관 상세(본문 포함). 미존재/미시행이면 404(TERMS_NOT_FOUND).")
	@GetMapping("/detail")
	public ApiResponse<TermsDetailResponse> detail(@Valid TermsDetailRequest request) {
		return ApiResponse.ok(TermsDetailResponse.from(termsService.getTermsDetail(request.trmsSn())));
	}
}
