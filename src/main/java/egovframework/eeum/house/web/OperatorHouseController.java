package egovframework.eeum.house.web;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
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

import egovframework.eeum.auth.vo.AuthUser;
import egovframework.eeum.base.response.ApiResponse;
import egovframework.eeum.base.response.PageResponse;
import egovframework.eeum.house.service.export.HouseExcelExportService;
import egovframework.eeum.house.service.house.OperatorHouseService;
import egovframework.eeum.house.web.dto.HouseDailyRateSetRequest;
import egovframework.eeum.house.web.dto.HouseDeleteRequest;
import egovframework.eeum.house.web.dto.HouseDetailResponse;
import egovframework.eeum.house.web.dto.HousePayMethodSetRequest;
import egovframework.eeum.house.web.dto.HousePolicyUpdateRequest;
import egovframework.eeum.house.web.dto.HouseRegularClosureSetRequest;
import egovframework.eeum.house.web.dto.HouseReleaseRequest;
import egovframework.eeum.house.web.dto.HouseReviewRejectRequest;
import egovframework.eeum.house.web.dto.HouseReviewRequest;
import egovframework.eeum.house.web.dto.HouseStatusChangeRequest;
import egovframework.eeum.house.web.dto.HouseSuspendRequest;
import egovframework.eeum.house.web.dto.HouseUpdateRequest;
import egovframework.eeum.house.web.dto.OperatorHouseDetailDrawerResponse;
import egovframework.eeum.house.web.dto.OperatorHouseFilterOptionsResponse;
import egovframework.eeum.house.web.dto.OperatorHouseInsertRequest;
import egovframework.eeum.house.web.dto.OperatorHouseListItemResponse;
import egovframework.eeum.house.web.dto.OperatorHouseSearchRequest;
import egovframework.eeum.house.web.dto.PublishCheckResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 운영자 상품(고택) API — 조회(HOUSE_VIEW) + 대행 관리(HOUSE_PROXY_MNG) + 임시조치(HOUSE_MODER).
 *
 * <p>운영자는 {@code HOUSE_PROXY_MNG} 권한으로 파트너 대신 고택/객실을 대행 등록·수정·삭제·상태변경하고
 * 정책·결제수단·이미지를 설정할 수 있다(소유 파트너는 대상에서 서버 해석, 행위자=운영자 — body 의 prtnrSn 불신).
 * 조회는 {@code HOUSE_VIEW}(소유권 무관 전 판매자), 임시조치({@code HOUSE_MODER})는 강제 게시중지(SUSPENDED — 판매자
 * 자가복구 불가)와 해제(SUSPENDED→HIDDEN)다.
 */
@Tag(name = "operator-house", description = "운영자 상품(고택) 조회/대행관리/임시조치")
@RestController
@RequestMapping("/operator/houses")
@RequiredArgsConstructor
public class OperatorHouseController {

	private static final DateTimeFormatter EXPORT_FILE_TS = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

	private final OperatorHouseService operatorHouseService;
	private final HouseExcelExportService houseExcelExportService;

	@Operation(summary = "고택 대행 등록(운영자)", description = "권한 HOUSE_PROXY_MNG. 운영자가 선택 파트너(승인) 대신 등록. DRAFT 생성(소유자=파트너, 행위자=운영자).")
	@PreAuthorize("hasAuthority('HOUSE_PROXY_MNG')")
	@PostMapping("/insert")
	public ResponseEntity<ApiResponse<HouseDetailResponse>> insert(
			@Valid @RequestBody OperatorHouseInsertRequest request, @AuthenticationPrincipal AuthUser user) {
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(operatorHouseService.insert(request, user)));
	}

	@Operation(summary = "고택 목록(운영자)",
			description = "권한 HOUSE_VIEW. 전 판매자. 필터 status/keyword(고택명·상호)/prtnrSn/sidoRegionCd/sigunguRegionCd(조합 AND).")
	@PreAuthorize("hasAuthority('HOUSE_VIEW')")
	@GetMapping
	public ApiResponse<PageResponse<OperatorHouseListItemResponse>> list(@Valid OperatorHouseSearchRequest request) {
		return ApiResponse.ok(operatorHouseService.list(request));
	}

	@Operation(summary = "상품 목록 필터 옵션(운영자)",
			description = "권한 HOUSE_VIEW. 상태(실제 HOUSE_STTS_CD)·지역(시도)·검색 메타 계약 제공 — 프론트 드롭다운 수동 하드코딩 제거용. "
					+ "검수요청/검수중 상태는 미지원(reviewStatus). regionFilter=false(지역필터 기능 구현됨). 지역 미매핑 고택 수는 regionUnmappedCount(UX 안내용, 기능과 무관). '전체'는 프론트가 prepend.")
	@PreAuthorize("hasAuthority('HOUSE_VIEW')")
	@GetMapping("/filter-options")
	public ApiResponse<OperatorHouseFilterOptionsResponse> filterOptions() {
		return ApiResponse.ok(operatorHouseService.filterOptions());
	}

	@Operation(summary = "상세 drawer 조회(운영자)",
			description = "권한 HOUSE_VIEW. 헤더/수용인원/요일요금/객실(모달: 침구·편의시설·이미지)/휴관일/판매자를 aggregate 1회 제공 "
					+ "— 조회전용 운영자가 HOUSE_PROXY_MNG 룸 API 없이 drawer+모달 렌더. from/to 미지정 시 [오늘(KST), 오늘+90), 최대 92일. "
					+ "성수기·반복휴관은 미지원(unsupported), 담당자 연락처 제외.")
	@PreAuthorize("hasAuthority('HOUSE_VIEW')")
	@GetMapping("/detail-drawer")
	public ApiResponse<OperatorHouseDetailDrawerResponse> detailDrawer(
			@RequestParam("houseSn") Long houseSn,
			@RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
		return ApiResponse.ok(operatorHouseService.detailDrawer(houseSn, from, to));
	}

	@Operation(summary = "고택목록 엑셀 내보내기(운영자)",
			description = "권한 HOUSE_EXPORT. 목록 검색조건(status/keyword/prtnrSn) 재사용, 페이징 무시 전체결과(최대 500건). 대표이미지 썸네일 포함.")
	@PreAuthorize("hasAuthority('HOUSE_EXPORT')")
	@GetMapping("/export")
	public ResponseEntity<byte[]> export(@Valid OperatorHouseSearchRequest request,
			@AuthenticationPrincipal AuthUser user) {
		byte[] body = houseExcelExportService.exportOperatorHouses(request, user);
		String filename = "houses_" + LocalDateTime.now().format(EXPORT_FILE_TS) + ".xlsx";
		return ResponseEntity.ok()
				.header(HttpHeaders.CONTENT_TYPE,
						"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
				.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
				.body(body);
	}

	@Operation(summary = "고택 상세(운영자)", description = "권한 HOUSE_VIEW. 소유권 무관.")
	@PreAuthorize("hasAuthority('HOUSE_VIEW')")
	@GetMapping("/detail")
	public ApiResponse<HouseDetailResponse> detail(@RequestParam("houseSn") Long houseSn) {
		return ApiResponse.ok(operatorHouseService.detail(houseSn));
	}

	@Operation(summary = "운영개시 사전점검(운영자)",
			description = "권한 HOUSE_PROXY_MNG. 상태변경 없이 발행 9조건 충족 여부 read-only 점검 — "
					+ "publishable + failedConditions(code/message/fixableOn). 소유자=대상 고택 서버해석(타인/삭제는 404).")
	@PreAuthorize("hasAuthority('HOUSE_PROXY_MNG')")
	@GetMapping("/publish-check")
	public ApiResponse<PublishCheckResponse> publishCheck(@RequestParam("houseSn") Long houseSn,
			@AuthenticationPrincipal AuthUser user) {
		return ApiResponse.ok(operatorHouseService.publishCheck(houseSn, user));
	}

	@Operation(summary = "고택 수정(운영자 대행)", description = "권한 HOUSE_PROXY_MNG. 대상 고택 owner 서버 해석.")
	@PreAuthorize("hasAuthority('HOUSE_PROXY_MNG')")
	@PostMapping("/update")
	public ApiResponse<Void> update(@Valid @RequestBody HouseUpdateRequest request,
			@AuthenticationPrincipal AuthUser user) {
		operatorHouseService.update(request, user);
		return ApiResponse.ok();
	}

	@Operation(summary = "고택 상태변경(운영자 대행)", description = "권한 HOUSE_PROXY_MNG. DRAFT/ACTIVE/HIDDEN. ACTIVE 전이 시 발행검증.")
	@PreAuthorize("hasAuthority('HOUSE_PROXY_MNG')")
	@PostMapping("/status")
	public ApiResponse<Void> changeStatus(@Valid @RequestBody HouseStatusChangeRequest request,
			@AuthenticationPrincipal AuthUser user) {
		operatorHouseService.changeStatus(request, user);
		return ApiResponse.ok();
	}

	@Operation(summary = "고택 정책 설정(운영자 대행)", description = "권한 HOUSE_PROXY_MNG. 위저드 Step2 전체저장.")
	@PreAuthorize("hasAuthority('HOUSE_PROXY_MNG')")
	@PostMapping("/policy")
	public ApiResponse<Void> updatePolicy(@Valid @RequestBody HousePolicyUpdateRequest request,
			@AuthenticationPrincipal AuthUser user) {
		operatorHouseService.updatePolicy(request, user);
		return ApiResponse.ok();
	}

	@Operation(summary = "고택 결제수단 설정(운영자 대행)", description = "권한 HOUSE_PROXY_MNG. 선택 집합 전체 교체.")
	@PreAuthorize("hasAuthority('HOUSE_PROXY_MNG')")
	@PostMapping("/pay-methods/set")
	public ApiResponse<Void> setPayMethods(@Valid @RequestBody HousePayMethodSetRequest request,
			@AuthenticationPrincipal AuthUser user) {
		operatorHouseService.setPayMethods(request, user);
		return ApiResponse.ok();
	}

	@Operation(summary = "고택 일자별 대표요금 설정(운영자 대행)", description = "권한 HOUSE_PROXY_MNG. 주중/금/토/성수기 4구간 전체 교체. owner 서버해석.")
	@PreAuthorize("hasAuthority('HOUSE_PROXY_MNG')")
	@PostMapping("/daily-rate/set")
	public ApiResponse<Void> setDailyRate(@Valid @RequestBody HouseDailyRateSetRequest request,
			@AuthenticationPrincipal AuthUser user) {
		operatorHouseService.setDailyRate(request, user);
		return ApiResponse.ok();
	}

	@Operation(summary = "고택 정기휴관 설정(운영자 대행)", description = "권한 HOUSE_PROXY_MNG. 주간휴관요일·공휴일·명절 휴무 전체 교체. owner 서버해석.")
	@PreAuthorize("hasAuthority('HOUSE_PROXY_MNG')")
	@PostMapping("/regular-closure/set")
	public ApiResponse<Void> setRegularClosure(@Valid @RequestBody HouseRegularClosureSetRequest request,
			@AuthenticationPrincipal AuthUser user) {
		operatorHouseService.setRegularClosure(request, user);
		return ApiResponse.ok();
	}

	@Operation(summary = "고택 삭제(운영자 대행)", description = "권한 HOUSE_PROXY_MNG. soft delete + 하위 객실 동반. owner 서버해석.")
	@PreAuthorize("hasAuthority('HOUSE_PROXY_MNG')")
	@PostMapping("/delete")
	public ApiResponse<Void> delete(@Valid @RequestBody HouseDeleteRequest request,
			@AuthenticationPrincipal AuthUser user) {
		operatorHouseService.delete(request, user);
		return ApiResponse.ok();
	}

	@Operation(summary = "고택 강제 게시중지(운영자급)", description = "권한 HOUSE_MODER. SUSPENDED. 판매자 자가복구 불가. 사유 감사기록.")
	@PreAuthorize("hasAuthority('HOUSE_MODER')")
	@PostMapping("/suspend")
	public ApiResponse<Void> suspend(@Valid @RequestBody HouseSuspendRequest request,
			@AuthenticationPrincipal AuthUser user) {
		operatorHouseService.suspend(request, user);
		return ApiResponse.ok();
	}

	@Operation(summary = "고택 임시조치 해제(운영자급)", description = "권한 HOUSE_MODER. SUSPENDED→HIDDEN(판매자 재게시).")
	@PreAuthorize("hasAuthority('HOUSE_MODER')")
	@PostMapping("/release")
	public ApiResponse<Void> release(@Valid @RequestBody HouseReleaseRequest request,
			@AuthenticationPrincipal AuthUser user) {
		operatorHouseService.release(request, user);
		return ApiResponse.ok();
	}

	@Operation(summary = "고택 검수요청(운영자)",
			description = "권한 HOUSE_PROXY_MNG. DRAFT/HIDDEN→REVIEW_REQUESTED. 발행 9조건 통과 필수(미충족=409+failedConditions). "
					+ "상태 불일치=409 HOUSE_REVIEW_STATE_INVALID. /status 와 분리된 전용 전이.")
	@PreAuthorize("hasAuthority('HOUSE_PROXY_MNG')")
	@PostMapping("/review-request")
	public ApiResponse<Void> reviewRequest(@Valid @RequestBody HouseReviewRequest request,
			@AuthenticationPrincipal AuthUser user) {
		operatorHouseService.reviewRequest(request, user);
		return ApiResponse.ok();
	}

	@Operation(summary = "고택 검수 승인(운영자급)",
			description = "권한 HOUSE_REVIEW(SUPERVISOR). REVIEW_REQUESTED→ACTIVE. 승인 시점 발행 9조건 재검증(미충족=409+failedConditions). "
					+ "상태 불일치=409.")
	@PreAuthorize("hasAuthority('HOUSE_REVIEW')")
	@PostMapping("/review-approve")
	public ApiResponse<Void> reviewApprove(@Valid @RequestBody HouseReviewRequest request,
			@AuthenticationPrincipal AuthUser user) {
		operatorHouseService.reviewApprove(request, user);
		return ApiResponse.ok();
	}

	@Operation(summary = "고택 검수 반려(운영자급)",
			description = "권한 HOUSE_REVIEW(SUPERVISOR). REVIEW_REQUESTED→DRAFT. 반려사유 필수(1~500자, 이력 저장). 상태 불일치=409.")
	@PreAuthorize("hasAuthority('HOUSE_REVIEW')")
	@PostMapping("/review-reject")
	public ApiResponse<Void> reviewReject(@Valid @RequestBody HouseReviewRejectRequest request,
			@AuthenticationPrincipal AuthUser user) {
		operatorHouseService.reviewReject(request, user);
		return ApiResponse.ok();
	}
}
