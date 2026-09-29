package egovframework.eeum.code.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import egovframework.eeum.base.response.ApiResponse;
import egovframework.eeum.code.service.code.CodeService;
import egovframework.eeum.code.web.dto.CodeListResponse;
import egovframework.eeum.code.web.dto.CodeSearchRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 공통코드 API — 상태/유형 코드 조회 (프론트 드롭다운·라벨 등). 비로그인 호출 가능(비민감 라벨).
 */
@Tag(name = "code", description = "공통코드 (상태/유형 코드 조회)")
@RestController
@RequestMapping("/codes")
@RequiredArgsConstructor
public class CodeController {

	private final CodeService codeService;

	@Operation(summary = "공통코드 목록", description = "그룹코드의 노출 가능 코드 목록(useYn='Y', sortSeq 순). 값 비교=cd, 표시=cdNm. 미존재 그룹은 빈 목록.")
	@GetMapping("/list")
	public ApiResponse<CodeListResponse> list(@Valid CodeSearchRequest request) {
		return ApiResponse.ok(new CodeListResponse(codeService.list(request.getGroupCd())));
	}
}
