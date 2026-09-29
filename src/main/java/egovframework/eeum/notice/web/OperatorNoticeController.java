package egovframework.eeum.notice.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import egovframework.eeum.auth.vo.AuthUser;
import egovframework.eeum.base.response.ApiResponse;
import egovframework.eeum.base.response.PageResponse;
import egovframework.eeum.notice.service.notice.NoticeService;
import egovframework.eeum.notice.web.dto.NoticeCreateRequest;
import egovframework.eeum.notice.web.dto.NoticeResponse;
import egovframework.eeum.notice.web.dto.NoticeSearchRequest;
import egovframework.eeum.notice.web.dto.NoticeUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 운영자 공지사항 — 조회=NOTICE_VIEW, 작성/수정/삭제/종료=NOTICE_MNG.
 *
 * <p>상태 라이프사이클(DRAFT/SCHEDULED/PUBLISHED_ONGOING/CLOSED). 예약→게시중 자동전이는 서버 cron.
 */
@Tag(name = "operator-notice", description = "운영자 공지사항 조회/작성/수정/삭제/종료")
@RestController
@RequestMapping("/operator/notices")
@RequiredArgsConstructor
public class OperatorNoticeController {

	private final NoticeService noticeService;

	@Operation(summary = "공지 목록(운영자)", description = "권한 NOTICE_VIEW. 상태/대상/알림/제목 필터 + 페이지네이션(기본 10).")
	@PreAuthorize("hasAuthority('NOTICE_VIEW')")
	@GetMapping
	public ApiResponse<PageResponse<NoticeResponse>> list(@Valid NoticeSearchRequest request) {
		return ApiResponse.ok(noticeService.list(request));
	}

	@Operation(summary = "공지 상세(운영자)", description = "권한 NOTICE_VIEW. 없으면 404.")
	@PreAuthorize("hasAuthority('NOTICE_VIEW')")
	@GetMapping("/{noticeId}")
	public ApiResponse<NoticeResponse> detail(@PathVariable("noticeId") Long noticeId) {
		return ApiResponse.ok(noticeService.get(noticeId));
	}

	@Operation(summary = "공지 작성(운영자)", description = "권한 NOTICE_MNG. saveAsDraft/sendTiming 으로 상태 산정. 즉시발행 시 알림(스텁).")
	@PreAuthorize("hasAuthority('NOTICE_MNG')")
	@PostMapping
	public ResponseEntity<ApiResponse<NoticeResponse>> create(
			@Valid @RequestBody NoticeCreateRequest request, @AuthenticationPrincipal AuthUser user) {
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(noticeService.create(request, user)));
	}

	@Operation(summary = "공지 수정(운영자)", description = "권한 NOTICE_MNG. 제목/내용/대상/발송시점/예약일시 편집(상태 보존). 없으면 404.")
	@PreAuthorize("hasAuthority('NOTICE_MNG')")
	@PutMapping("/{noticeId}")
	public ApiResponse<NoticeResponse> update(@PathVariable("noticeId") Long noticeId,
			@Valid @RequestBody NoticeUpdateRequest request, @AuthenticationPrincipal AuthUser user) {
		return ApiResponse.ok(noticeService.update(noticeId, request, user));
	}

	@Operation(summary = "공지 종료(운영자)", description = "권한 NOTICE_MNG. 게시중→종료. 없으면 404, 게시중 아니면 409.")
	@PreAuthorize("hasAuthority('NOTICE_MNG')")
	@PostMapping("/{noticeId}/close")
	public ApiResponse<NoticeResponse> close(@PathVariable("noticeId") Long noticeId,
			@AuthenticationPrincipal AuthUser user) {
		return ApiResponse.ok(noticeService.close(noticeId, user));
	}

	@Operation(summary = "공지 삭제(운영자)", description = "권한 NOTICE_MNG. soft delete(DEL_YN='Y'). 없으면 404.")
	@PreAuthorize("hasAuthority('NOTICE_MNG')")
	@DeleteMapping("/{noticeId}")
	public ApiResponse<Void> delete(@PathVariable("noticeId") Long noticeId, @AuthenticationPrincipal AuthUser user) {
		noticeService.delete(noticeId, user);
		return ApiResponse.ok();
	}
}
