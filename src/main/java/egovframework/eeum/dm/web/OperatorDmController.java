package egovframework.eeum.dm.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import egovframework.eeum.auth.vo.AuthUser;
import egovframework.eeum.base.response.ApiResponse;
import egovframework.eeum.base.response.PageResponse;
import egovframework.eeum.dm.service.dm.DmService;
import egovframework.eeum.dm.web.dto.DmCreateRequest;
import egovframework.eeum.dm.web.dto.DmResponse;
import egovframework.eeum.dm.web.dto.DmSearchRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 운영자 DM 발송 — 조회=DM_VIEW, 발송/예약/임시저장/삭제=DM_MNG.
 *
 * <p>채널 알림톡/문자. 상태 라이프사이클(SENT/SCHEDULED/DRAFT/FAILED). 예약→발송 자동전이는 서버 cron. 실 전송은 스텁.
 */
@Tag(name = "operator-dm", description = "운영자 DM 발송 조회/발송/삭제")
@RestController
@RequestMapping("/operator/dm")
@RequiredArgsConstructor
public class OperatorDmController {

	private final DmService dmService;

	@Operation(summary = "DM 목록(운영자)", description = "권한 DM_VIEW. 상태/채널/제목 필터 + 페이지네이션(기본 10).")
	@PreAuthorize("hasAuthority('DM_VIEW')")
	@GetMapping
	public ApiResponse<PageResponse<DmResponse>> list(@Valid DmSearchRequest request) {
		return ApiResponse.ok(dmService.list(request));
	}

	@Operation(summary = "DM 상세(운영자)", description = "권한 DM_VIEW. 없으면 404.")
	@PreAuthorize("hasAuthority('DM_VIEW')")
	@GetMapping("/{dmId}")
	public ApiResponse<DmResponse> detail(@PathVariable("dmId") Long dmId) {
		return ApiResponse.ok(dmService.get(dmId));
	}

	@Operation(summary = "DM 발송/예약/임시저장(운영자)", description = "권한 DM_MNG. saveAsDraft/sendTiming 으로 상태 산정. 즉시발송 시 발송(스텁).")
	@PreAuthorize("hasAuthority('DM_MNG')")
	@PostMapping
	public ResponseEntity<ApiResponse<DmResponse>> create(
			@Valid @RequestBody DmCreateRequest request, @AuthenticationPrincipal AuthUser user) {
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(dmService.create(request, user)));
	}

	@Operation(summary = "DM 삭제(운영자)", description = "권한 DM_MNG. soft delete(DEL_YN='Y'). 없으면 404.")
	@PreAuthorize("hasAuthority('DM_MNG')")
	@DeleteMapping("/{dmId}")
	public ApiResponse<Void> delete(@PathVariable("dmId") Long dmId, @AuthenticationPrincipal AuthUser user) {
		dmService.delete(dmId, user);
		return ApiResponse.ok();
	}
}
