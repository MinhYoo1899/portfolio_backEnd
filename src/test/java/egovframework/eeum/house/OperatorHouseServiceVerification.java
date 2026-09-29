package egovframework.eeum.house;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import egovframework.eeum.account.service.partner.PartnerService;
import egovframework.eeum.account.web.dto.PartnerDetailResponse;
import egovframework.eeum.auth.vo.AuthUser;
import egovframework.eeum.base.audit.AuditRecorder;
import egovframework.eeum.base.exception.BusinessException;
import egovframework.eeum.code.service.code.CodeService;
import egovframework.eeum.code.web.dto.CodeItemResponse;
import egovframework.eeum.house.exception.HouseErrorCode;
import egovframework.eeum.house.service.house.HouseProxyOwnerResolver;
import egovframework.eeum.house.service.house.HousePublishGuard;
import egovframework.eeum.house.service.house.HousePublishValidator;
import egovframework.eeum.house.service.house.HouseReviewGuard;
import egovframework.eeum.house.service.house.ReadinessSnapshot;
import egovframework.eeum.house.service.house.dao.HouseDrawerMapper;
import egovframework.eeum.house.service.house.dao.HouseMapper;
import egovframework.eeum.house.service.house.dao.HousePayMethodMapper;
import egovframework.eeum.house.service.house.impl.OperatorHouseServiceImpl;
import egovframework.eeum.house.service.room.dao.RoomMapper;
import egovframework.eeum.house.vo.DrawerPriceBucketVO;
import egovframework.eeum.house.vo.DrawerRoomAmenityVO;
import egovframework.eeum.house.vo.DrawerRoomBeddingVO;
import egovframework.eeum.house.vo.DrawerRoomImageVO;
import egovframework.eeum.house.vo.DrawerRoomVO;
import egovframework.eeum.house.vo.HouseDrawerVO;
import egovframework.eeum.house.vo.HouseVO;
import egovframework.eeum.house.web.dto.HouseDeleteRequest;
import egovframework.eeum.house.web.dto.HousePayMethodSetRequest;
import egovframework.eeum.house.web.dto.HouseDailyRateSetRequest;
import egovframework.eeum.house.web.dto.HousePolicyUpdateRequest;
import egovframework.eeum.house.web.dto.HouseRegularClosureSetRequest;
import egovframework.eeum.house.web.dto.HouseReleaseRequest;
import egovframework.eeum.house.web.dto.HouseReviewRejectRequest;
import egovframework.eeum.house.web.dto.HouseReviewRequest;
import egovframework.eeum.house.web.dto.HouseStatusChangeRequest;
import egovframework.eeum.house.web.dto.HouseSuspendRequest;
import egovframework.eeum.house.web.dto.HouseUpdateRequest;
import egovframework.eeum.house.exception.PublishCondition;
import egovframework.eeum.house.web.dto.OperatorHouseDetailDrawerResponse;
import egovframework.eeum.house.web.dto.OperatorHouseFilterOptionsResponse;
import egovframework.eeum.house.web.dto.OperatorHouseInsertRequest;
import egovframework.eeum.house.web.dto.OperatorHouseSearchRequest;
import egovframework.eeum.house.web.dto.PublishCheckResponse;
import egovframework.eeum.region.service.region.RegionService;
import egovframework.eeum.region.vo.RegionVO;

/**
 * 운영자 상품 서비스 검증 — 조회/강제중지(멱등·사유기록)/해제(상태머신)(DB 없이 mock).
 */
class OperatorHouseServiceVerification {

	private final HouseMapper houseMapper = mock(HouseMapper.class);
	private final RoomMapper roomMapper = mock(RoomMapper.class);
	private final AuditRecorder auditRecorder = mock(AuditRecorder.class);
	private final PartnerService partnerService = mock(PartnerService.class);
	private final HouseProxyOwnerResolver proxyResolver = mock(HouseProxyOwnerResolver.class);
	private final HousePayMethodMapper payMethodMapper = mock(HousePayMethodMapper.class);
	private final CodeService codeService = mock(CodeService.class);
	private final HousePublishGuard publishGuard = mock(HousePublishGuard.class);
	private final HousePublishValidator publishValidator = mock(HousePublishValidator.class);
	private final RegionService regionService = mock(RegionService.class);
	private final HouseDrawerMapper houseDrawerMapper = mock(HouseDrawerMapper.class);
	private final HouseReviewGuard reviewGuard = mock(HouseReviewGuard.class);
	private final OperatorHouseServiceImpl service = new OperatorHouseServiceImpl(
			houseMapper, roomMapper, auditRecorder, partnerService, proxyResolver, payMethodMapper, codeService,
			publishGuard, publishValidator, regionService, houseDrawerMapper, reviewGuard, mock(egovframework.eeum.notification.service.notification.NotificationEmitter.class));

	private static AuthUser operator(long sn) {
		return new AuthUser(sn, "op@eeum.kr", "운영자", "OPERATOR", "ACTIVE", List.of("HOUSE_PROXY_MNG"));
	}

	private static HouseVO house(String stts, String delYn) {
		return HouseVO.builder().houseSn(5L).houseSttsCd(stts).delYn(delYn).build();
	}

	/** 소유자(PRTNR_SN) 가 해석된 owner 와 일치하는 활성 고택. */
	private static HouseVO houseOwned(long ownerPrtnrSn, String stts) {
		return HouseVO.builder().houseSn(5L).prtnrSn(ownerPrtnrSn).houseSttsCd(stts).delYn("N").build();
	}

	private static CodeItemResponse code(String group, String cd) {
		return new CodeItemResponse(group, cd, cd, 1);
	}

	private static PartnerDetailResponse partner(String aprvStts, String acntStts) {
		return new PartnerDetailResponse("1", "7", "p@eeum.kr", "파트너", acntStts, "고즈넉", "9990001111",
				null, null, null, null, null, null, aprvStts, null, null, null, null, null);
	}

	private static OperatorHouseInsertRequest proxyReq() {
		return new OperatorHouseInsertRequest(1L, "신규고택", null, null, null, null, null, null);
	}

	@Test
	@DisplayName("대행등록 — 승인됐어도 계정 정지(SUSPENDED) 파트너면 차단, insertHouse 호출 안 함")
	void proxyInsertSuspendedPartnerBlocked() {
		when(partnerService.detail(1L)).thenReturn(partner("APPROVED", "SUSPENDED"));
		assertThrows(BusinessException.class, () -> service.insert(proxyReq(), operator(9)));
		verify(houseMapper, never()).insertHouse(any());
	}

	@Test
	@DisplayName("대행등록 — 반려(REJECTED) 파트너면 차단, insertHouse 호출 안 함")
	void proxyInsertRejectedPartnerBlocked() {
		when(partnerService.detail(1L)).thenReturn(partner("REJECTED", "ACTIVE"));
		assertThrows(BusinessException.class, () -> service.insert(proxyReq(), operator(9)));
		verify(houseMapper, never()).insertHouse(any());
	}

	@Test
	@DisplayName("목록 — 필터 없이 조회")
	void listNoFilter() {
		when(houseMapper.countHouseListForOperator(null, null, null, null, null)).thenReturn(0L);
		when(houseMapper.selectHouseListForOperator(null, null, null, null, null, 20, 0)).thenReturn(List.of());
		service.list(new OperatorHouseSearchRequest(null, null, null, null, null, null, null));
		verify(houseMapper).selectHouseListForOperator(null, null, null, null, null, 20, 0);
	}

	@Test
	@DisplayName("목록 — 지역 필터(시도·시군구)가 매퍼에 그대로 전달")
	void listRegionFilterPassed() {
		when(houseMapper.countHouseListForOperator(null, null, null, "47", "47170")).thenReturn(0L);
		when(houseMapper.selectHouseListForOperator(null, null, null, "47", "47170", 20, 0)).thenReturn(List.of());
		service.list(new OperatorHouseSearchRequest(null, null, null, null, null, "47", "47170"));
		verify(houseMapper).countHouseListForOperator(null, null, null, "47", "47170");
		verify(houseMapper).selectHouseListForOperator(null, null, null, "47", "47170", 20, 0);
	}

	@Test
	@DisplayName("필터옵션 — 실제 HOUSE_STTS_CD 5개(검수요청 포함) + 시도 라벨(짧은명 우선) + 검색메타/미지원 플래그")
	void filterOptionsContract() {
		when(regionService.sidoRegions()).thenReturn(List.of(
				RegionVO.builder().regionCd("11").regionNm("서울특별시").regionShortNm("서울").sortSeq(1).build(),
				RegionVO.builder().regionCd("47").regionNm("경상북도").regionShortNm(null).sortSeq(15).build()));
		when(houseMapper.countHousesWithoutRegion()).thenReturn(3L); // 미매핑 데이터 3건 → regionUnmappedCount=3 (regionFilter 기능 플래그와 무관)

		OperatorHouseFilterOptionsResponse res = service.filterOptions();

		// 상태 = 실제 5개(DRAFT/REVIEW_REQUESTED/ACTIVE/HIDDEN/SUSPENDED 순, 검수요청 도입됨)
		List<String> statusValues = res.statuses().stream()
				.map(OperatorHouseFilterOptionsResponse.StatusOption::value).toList();
		assertEquals(List.of("DRAFT", "REVIEW_REQUESTED", "ACTIVE", "HIDDEN", "SUSPENDED"), statusValues);
		assertEquals("임시저장", res.statuses().get(0).label());
		assertEquals("검수요청", res.statuses().get(1).label());
		assertEquals("게재 중지", res.statuses().get(4).label()); // SUSPENDED 라벨 계약(퍼블 '게재 중지' 정합) 회귀고정

		// 지역 — value=코드, label=짧은명 우선(없으면 공식명 fallback)
		assertEquals("11", res.regions().get(0).value());
		assertEquals("서울", res.regions().get(0).label());
		assertEquals("경상북도", res.regions().get(1).label());

		// 검색 메타 = 실제 범위(고택명+파트너명)
		assertEquals("고택명, 파트너명으로 검색", res.search().placeholder());
		assertTrue(res.search().keywordTargets().contains("houseName"));
		assertTrue(res.search().keywordTargets().contains("partnerName"));

		// 미지원 플래그 — 검수요청 도입됨(reviewStatus=false), 파트너명 검색 지원, regionFilter=false(필터 기능 구현됨)
		assertFalse(res.unsupported().reviewStatus());
		assertFalse(res.unsupported().partnerKeyword());
		assertFalse(res.unsupported().regionFilter());
		assertEquals(3L, res.regionUnmappedCount()); // 미매핑 건수는 별도 필드(기능과 무관)
	}

	@Test
	@DisplayName("필터옵션 — 지역필터 기능은 항상 지원(regionFilter=false), 미매핑 건수는 regionUnmappedCount 로 분리")
	void filterOptionsRegionUnmappedSeparate() {
		when(regionService.sidoRegions()).thenReturn(List.of(
				RegionVO.builder().regionCd("11").regionNm("서울특별시").regionShortNm("서울").sortSeq(1).build()));
		when(houseMapper.countHousesWithoutRegion()).thenReturn(0L);

		OperatorHouseFilterOptionsResponse res = service.filterOptions();

		assertFalse(res.unsupported().regionFilter()); // 기능 지원(WHERE 구현)
		assertEquals(0L, res.regionUnmappedCount());   // 미매핑 0
	}

	@Test
	@DisplayName("필터옵션 직렬화 — statuses/regions/search/unsupported JSON 계약 + 검수요청 상태 노출")
	void filterOptionsSerialization() throws Exception {
		when(regionService.sidoRegions()).thenReturn(List.of(
				RegionVO.builder().regionCd("11").regionNm("서울특별시").regionShortNm("서울").sortSeq(1).build()));
		when(houseMapper.countHousesWithoutRegion()).thenReturn(2L); // 미매핑 데이터 2건 → regionUnmappedCount=2 (regionFilter 기능 플래그와 무관)
		String json = new ObjectMapper().writeValueAsString(service.filterOptions());
		assertTrue(json.contains("\"value\":\"ACTIVE\""));
		assertTrue(json.contains("\"label\":\"게재중\""));
		assertTrue(json.contains("\"reviewStatus\":false"));
		assertTrue(json.contains("\"partnerKeyword\":false"));
		assertTrue(json.contains("\"regionFilter\":false"));
		assertTrue(json.contains("\"regionUnmappedCount\":2"));
		assertTrue(json.contains("\"placeholder\":\"고택명, 파트너명으로 검색\""));
		assertTrue(json.contains("REVIEW_REQUESTED")); // 검수요청 상태 노출(도입됨)
		assertTrue(json.contains("검수요청")); // 검수요청 라벨 노출
	}

	// ===== 검수 전이(REVIEW_REQUESTED) =====

	private void lockedHouse(long houseSn, long ownerPrtnrSn, String stts) {
		when(houseMapper.selectHouseForUpdate(houseSn))
				.thenReturn(HouseVO.builder().houseSn(houseSn).prtnrSn(ownerPrtnrSn).houseSttsCd(stts).delYn("N").build());
	}

	@Test
	@DisplayName("검수요청 — DRAFT→REVIEW_REQUESTED, 발행검증 통과 후 전이+REQUEST 이력")
	void reviewRequestFromDraft() {
		lockedHouse(5L, 7L, "DRAFT");
		when(houseMapper.transitionHouseReviewStatus(5L, "DRAFT", "REVIEW_REQUESTED", 9L)).thenReturn(1);
		service.reviewRequest(new HouseReviewRequest(5L), operator(9));
		verify(publishValidator).validate(5L, 7L);
		verify(houseMapper).transitionHouseReviewStatus(5L, "DRAFT", "REVIEW_REQUESTED", 9L);
		verify(houseMapper).insertHouseReviewHstry(any());
	}

	@Test
	@DisplayName("검수요청 — HIDDEN 에서도 가능")
	void reviewRequestFromHidden() {
		lockedHouse(5L, 7L, "HIDDEN");
		when(houseMapper.transitionHouseReviewStatus(5L, "HIDDEN", "REVIEW_REQUESTED", 9L)).thenReturn(1);
		service.reviewRequest(new HouseReviewRequest(5L), operator(9));
		verify(houseMapper).transitionHouseReviewStatus(5L, "HIDDEN", "REVIEW_REQUESTED", 9L);
	}

	@Test
	@DisplayName("검수요청 — ACTIVE 소스는 409, 전이/검증 안 함")
	void reviewRequestFromActiveRejected() {
		lockedHouse(5L, 7L, "ACTIVE");
		BusinessException e = assertThrows(BusinessException.class,
				() -> service.reviewRequest(new HouseReviewRequest(5L), operator(9)));
		assertEquals(HouseErrorCode.HOUSE_REVIEW_STATE_INVALID, e.getErrorCode());
		verify(houseMapper, never()).transitionHouseReviewStatus(anyLong(), any(), any(), anyLong());
		verifyNoInteractions(publishValidator);
	}

	@Test
	@DisplayName("검수요청 — 이미 REVIEW_REQUESTED 재요청은 409")
	void reviewRequestAlreadyRequestedRejected() {
		lockedHouse(5L, 7L, "REVIEW_REQUESTED");
		BusinessException e = assertThrows(BusinessException.class,
				() -> service.reviewRequest(new HouseReviewRequest(5L), operator(9)));
		assertEquals(HouseErrorCode.HOUSE_REVIEW_STATE_INVALID, e.getErrorCode());
	}

	@Test
	@DisplayName("검수요청 — 발행 9조건 미충족이면 전이 안 함(검증 예외 전파)")
	void reviewRequestPublishFail() {
		lockedHouse(5L, 7L, "DRAFT");
		org.mockito.Mockito.doThrow(new egovframework.eeum.house.exception.HouseNotPublishableException(
				List.of(PublishCondition.PAYMENT_METHOD_MISSING))).when(publishValidator).validate(5L, 7L);
		assertThrows(BusinessException.class, () -> service.reviewRequest(new HouseReviewRequest(5L), operator(9)));
		verify(houseMapper, never()).transitionHouseReviewStatus(anyLong(), any(), any(), anyLong());
	}

	@Test
	@DisplayName("검수승인 — REVIEW_REQUESTED→ACTIVE, 승인시점 재검증 후 전이+APPROVE 이력")
	void reviewApprove() {
		lockedHouse(5L, 7L, "REVIEW_REQUESTED");
		when(houseMapper.transitionHouseReviewStatus(5L, "REVIEW_REQUESTED", "ACTIVE", 9L)).thenReturn(1);
		service.reviewApprove(new HouseReviewRequest(5L), operator(9));
		verify(publishValidator).validate(5L, 7L);
		verify(houseMapper).transitionHouseReviewStatus(5L, "REVIEW_REQUESTED", "ACTIVE", 9L);
		verify(houseMapper).insertHouseReviewHstry(any());
	}

	@Test
	@DisplayName("검수승인 — 소스가 REVIEW_REQUESTED 아니면 409")
	void reviewApproveWrongSource() {
		lockedHouse(5L, 7L, "DRAFT");
		BusinessException e = assertThrows(BusinessException.class,
				() -> service.reviewApprove(new HouseReviewRequest(5L), operator(9)));
		assertEquals(HouseErrorCode.HOUSE_REVIEW_STATE_INVALID, e.getErrorCode());
		verify(houseMapper, never()).transitionHouseReviewStatus(anyLong(), any(), any(), anyLong());
	}

	@Test
	@DisplayName("검수반려 — REVIEW_REQUESTED→DRAFT, 반려사유 이력 저장")
	void reviewReject() {
		lockedHouse(5L, 7L, "REVIEW_REQUESTED");
		when(houseMapper.transitionHouseReviewStatus(5L, "REVIEW_REQUESTED", "DRAFT", 9L)).thenReturn(1);
		service.reviewReject(new HouseReviewRejectRequest(5L, "사진 부적합"), operator(9));
		verify(houseMapper).transitionHouseReviewStatus(5L, "REVIEW_REQUESTED", "DRAFT", 9L);
		verify(houseMapper).insertHouseReviewHstry(any());
	}

	@Test
	@DisplayName("검수반려 — 소스가 REVIEW_REQUESTED 아니면 409")
	void reviewRejectWrongSource() {
		lockedHouse(5L, 7L, "ACTIVE");
		BusinessException e = assertThrows(BusinessException.class,
				() -> service.reviewReject(new HouseReviewRejectRequest(5L, "사유"), operator(9)));
		assertEquals(HouseErrorCode.HOUSE_REVIEW_STATE_INVALID, e.getErrorCode());
	}

	@Test
	@DisplayName("게이트 무결성 — REVIEW_REQUESTED 고택은 /status 로 전환 불가(409, 승인/반려 전용), updateHouseStatus 호출 안 함")
	void changeStatusOnReviewRequestedRejected() {
		when(proxyResolver.resolveOwnerForHouse(eq(5L), any())).thenReturn(7L);
		when(houseMapper.selectHouseForUpdate(5L)).thenReturn(houseOwned(7L, "REVIEW_REQUESTED"));
		BusinessException e = assertThrows(BusinessException.class,
				() -> service.changeStatus(new HouseStatusChangeRequest(5L, "ACTIVE"), operator(9)));
		assertEquals(HouseErrorCode.HOUSE_REVIEW_STATE_INVALID, e.getErrorCode());
		verify(houseMapper, never()).updateHouseStatus(anyLong(), anyLong(), any(), anyLong());
		verifyNoInteractions(publishValidator);
	}

	@Test
	@DisplayName("편집복귀 계약 증명 — REVIEW_REQUESTED 결제수단 설정은 안 막힘(가드=delYn/소유권만), reviewGuard 위임")
	void setPayMethodsUnderReviewNotBlocked() {
		when(proxyResolver.resolveOwnerForHouse(eq(5L), any())).thenReturn(7L);
		when(houseMapper.selectHouseForUpdate(5L)).thenReturn(houseOwned(7L, "REVIEW_REQUESTED"));
		service.setPayMethods(new HousePayMethodSetRequest(5L, List.of()), operator(9));
		verify(payMethodMapper).softDeleteActiveByHouse(5L, 9L); // 편집 코어 진입(=안 막힘)
		verify(reviewGuard).markDirtyIfUnderReview(5L, 9L);       // DRAFT 복귀 위임
	}

	@Test
	@DisplayName("편집복귀 계약 증명 — REVIEW_REQUESTED 정책수정은 안 막힘, reviewGuard 위임")
	void updatePolicyUnderReviewNotBlocked() {
		when(proxyResolver.resolveOwnerForHouse(eq(5L), any())).thenReturn(7L);
		when(houseMapper.selectHouseForUpdate(5L)).thenReturn(houseOwned(7L, "REVIEW_REQUESTED"));
		when(houseMapper.updateHousePolicy(anyLong(), anyLong(), any(), any(), any(), any(), any(), any(), any(),
				any(), any(), any(), anyLong())).thenReturn(1);
		service.updatePolicy(new HousePolicyUpdateRequest(5L, null, null, null, null, null, null, null, null, null,
				null), operator(9));
		verify(reviewGuard).markDirtyIfUnderReview(5L, 9L);
	}

	@Test
	@DisplayName("일자별 대표요금 설정 — 소유 활성 고택 전체 교체 성공 + reviewGuard 위임")
	void setDailyRateSuccess() {
		when(proxyResolver.resolveOwnerForHouse(eq(5L), any())).thenReturn(7L);
		when(houseMapper.selectHouseForUpdate(5L)).thenReturn(houseOwned(7L, "DRAFT"));
		when(houseMapper.updateHouseDailyRate(eq(5L), eq(7L), any(), any(), any(), any(), anyLong())).thenReturn(1);
		service.setDailyRate(new HouseDailyRateSetRequest(5L, 180000, 220000, 250000, null), operator(9));
		verify(houseMapper).updateHouseDailyRate(5L, 7L, 180000, 220000, 250000, null, 9L);
		verify(reviewGuard).markDirtyIfUnderReview(5L, 9L);
	}

	@Test
	@DisplayName("일자별 대표요금 설정 — 대상 없음(영향행 0) → HOUSE_NOT_FOUND")
	void setDailyRateNotFound() {
		when(proxyResolver.resolveOwnerForHouse(eq(5L), any())).thenReturn(7L);
		when(houseMapper.selectHouseForUpdate(5L)).thenReturn(houseOwned(7L, "DRAFT"));
		when(houseMapper.updateHouseDailyRate(anyLong(), anyLong(), any(), any(), any(), any(), anyLong())).thenReturn(0);
		BusinessException e = assertThrows(BusinessException.class,
				() -> service.setDailyRate(new HouseDailyRateSetRequest(5L, 1, 2, 3, 4), operator(9)));
		assertEquals(HouseErrorCode.HOUSE_NOT_FOUND, e.getErrorCode());
	}

	@Test
	@DisplayName("정기휴관 설정 — 유효 요일+플래그(Y/N 변환) 전체 교체 성공 + reviewGuard 위임")
	void setRegularClosureSuccess() {
		when(proxyResolver.resolveOwnerForHouse(eq(5L), any())).thenReturn(7L);
		when(houseMapper.selectHouseForUpdate(5L)).thenReturn(houseOwned(7L, "DRAFT"));
		when(houseMapper.updateHouseRegularClosure(eq(5L), eq(7L), eq("TUE"), eq("Y"), eq("N"), anyLong()))
				.thenReturn(1);
		service.setRegularClosure(new HouseRegularClosureSetRequest(5L, "TUE", true, false), operator(9));
		verify(houseMapper).updateHouseRegularClosure(5L, 7L, "TUE", "Y", "N", 9L);
		verify(reviewGuard).markDirtyIfUnderReview(5L, 9L);
	}

	@Test
	@DisplayName("정기휴관 설정 — 잘못된 요일코드 → HOUSE_CLOSED_WEEKDAY_INVALID, 매퍼 미진입")
	void setRegularClosureInvalidWeekday() {
		when(proxyResolver.resolveOwnerForHouse(eq(5L), any())).thenReturn(7L);
		when(houseMapper.selectHouseForUpdate(5L)).thenReturn(houseOwned(7L, "DRAFT"));
		BusinessException e = assertThrows(BusinessException.class, () -> service.setRegularClosure(
				new HouseRegularClosureSetRequest(5L, "FUNDAY", false, false), operator(9)));
		assertEquals(HouseErrorCode.HOUSE_CLOSED_WEEKDAY_INVALID, e.getErrorCode());
		verify(houseMapper, never()).updateHouseRegularClosure(anyLong(), anyLong(), any(), any(), any(), anyLong());
	}

	// ===== 상세 drawer(HOUSE_VIEW aggregate) =====

	@Test
	@DisplayName("drawer — 기간 역전/92일 초과는 400, 조회 진입 안 함")
	void drawerInvalidRange() {
		BusinessException e1 = assertThrows(BusinessException.class,
				() -> service.detailDrawer(1L, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 1))); // span 0
		assertEquals(HouseErrorCode.HOUSE_DRAWER_RANGE_INVALID, e1.getErrorCode());
		BusinessException e2 = assertThrows(BusinessException.class,
				() -> service.detailDrawer(1L, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 10, 2))); // 93일
		assertEquals(HouseErrorCode.HOUSE_DRAWER_RANGE_INVALID, e2.getErrorCode());
		verify(houseDrawerMapper, never()).selectDrawerHouse(anyLong());
	}

	@Test
	@DisplayName("drawer — 고택 없으면 404")
	void drawerNotFound() {
		when(houseDrawerMapper.selectDrawerHouse(9L)).thenReturn(null);
		BusinessException e = assertThrows(BusinessException.class, () -> service.detailDrawer(9L, null, null));
		assertEquals(HouseErrorCode.HOUSE_NOT_FOUND, e.getErrorCode());
	}

	@Test
	@DisplayName("drawer — 조립: 요일버킷(빈=null/variable)·대표요금·대표이미지·침구·편의시설·휴관일·판매자(연락처X)·미지원, N+1 없음")
	void drawerAssembles() {
		when(houseDrawerMapper.selectDrawerHouse(76L)).thenReturn(HouseDrawerVO.builder()
				.houseSn(76L).houseNm("고운당").prtnrConm("고운재").addr("서울 종로구").houseSttsCd("ACTIVE")
				.houseDc("소개").chkinTime(LocalTime.of(15, 0)).chkoutTime(LocalTime.of(11, 0))
				.noticeCn("공지").cnclRfndPlcyCn("환불").repImgFileSn(66L)
				.rprsvNm("홍길동").brno("1234567890").mailOrderSalesNo("2026-1").bizAddr("서울 종로구 1 2층").build());
		when(houseDrawerMapper.selectDrawerRooms(eq(76L), any(), any())).thenReturn(List.of(
				DrawerRoomVO.builder().roomSn(11L).roomNm("사랑채").roomSttsCd("ACTIVE").bscPrsnCnt(2).maxPrsnCnt(4)
						.roomDc("온돌방").areaSqm(new BigDecimal("24.0")).roomCnt(1).bathrmCnt(1).repPriceFrom(180000).build(),
				DrawerRoomVO.builder().roomSn(12L).roomNm("안채").roomSttsCd("ACTIVE").bscPrsnCnt(4).maxPrsnCnt(6)
						.roomDc("안채").areaSqm(new BigDecimal("36.0")).roomCnt(2).bathrmCnt(1).repPriceFrom(null).build()));
		when(houseDrawerMapper.selectDrawerBeddings(any())).thenReturn(List.of(
				DrawerRoomBeddingVO.builder().roomSn(11L).beddingTypeCd("DOUBLE_BED").beddingTypeNm("더블 베드").bedCnt(1).build()));
		when(houseDrawerMapper.selectDrawerAmenities(any())).thenReturn(List.of(
				DrawerRoomAmenityVO.builder().roomSn(11L).amenitySn(5L).amenityNm("온돌")
						.amenityCtgryCd("FACILITY").amenityCtgryNm("시설").build()));
		when(houseDrawerMapper.selectDrawerImages(any())).thenReturn(List.of(
				DrawerRoomImageVO.builder().roomSn(11L).atchFileSn(51L).rprsYn("Y").sortSeq(1).build(),
				DrawerRoomImageVO.builder().roomSn(11L).atchFileSn(52L).rprsYn("N").sortSeq(2).build()));
		when(houseDrawerMapper.selectDrawerPriceBuckets(eq(76L), any(), any())).thenReturn(List.of(
				DrawerPriceBucketVO.builder().bucket("WEEKDAY").minPrice(120000).maxPrice(150000).build(),
				DrawerPriceBucketVO.builder().bucket("SAT").minPrice(200000).maxPrice(200000).build()));
		when(houseDrawerMapper.countDrawerCalendarRooms(eq(76L), any(), any())).thenReturn(2);
		when(houseDrawerMapper.selectDrawerHouseClosedDates(eq(76L), any(), any(), eq(2)))
				.thenReturn(List.of(LocalDate.of(2026, 7, 9)));

		OperatorHouseDetailDrawerResponse res =
				service.detailDrawer(76L, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31));

		// house + capacity
		assertEquals("고운재", res.house().prtnrConm());
		assertEquals("/files/download?atchFileSn=66", res.house().repImgUrl());
		assertEquals("15:00", res.house().chkinTime());
		assertEquals(6, res.capacity().bscPrsnTotal());
		assertEquals(10, res.capacity().maxPrsnTotal());
		// priceSummary — 빈 버킷 null, variable, 성수기 항상 null
		assertEquals(120000, res.priceSummary().weekday().minPrice());
		assertTrue(res.priceSummary().weekday().variable());
		assertNull(res.priceSummary().friday());
		assertFalse(res.priceSummary().saturday().variable());
		assertNull(res.priceSummary().peakSeason());
		// rooms — 대표요금/대표이미지/침구/편의시설 그룹핑
		assertEquals(180000, res.rooms().get(0).repPriceFrom());
		assertEquals("/files/download?atchFileSn=51", res.rooms().get(0).repImgUrl());
		assertEquals(2, res.rooms().get(0).images().size());
		assertEquals("더블 베드", res.rooms().get(0).beddings().get(0).beddingTypeNm());
		assertEquals("시설", res.rooms().get(0).amenities().get(0).amenityCtgryNm());
		assertNull(res.rooms().get(1).repPriceFrom());
		assertNull(res.rooms().get(1).repImgUrl());
		assertTrue(res.rooms().get(1).beddings().isEmpty());
		// 휴관일 / 판매자(연락처 필드 없음, bizAddr 포함) / 미지원
		assertEquals(List.of(LocalDate.of(2026, 7, 9)), res.houseClosedDates());
		assertEquals("서울 종로구 1 2층", res.seller().bizAddr());
		assertEquals("1234567890", res.seller().brno());
		assertTrue(res.unsupported().peakSeasonPrice());
		assertTrue(res.unsupported().recurringClosure());
		// N+1 없음 — 각 배치/집계 매퍼 1회
		verify(houseDrawerMapper).selectDrawerBeddings(any());
		verify(houseDrawerMapper).selectDrawerAmenities(any());
		verify(houseDrawerMapper).selectDrawerImages(any());
		verify(houseDrawerMapper).selectDrawerPriceBuckets(eq(76L), any(), any());
		verify(houseDrawerMapper).countDrawerCalendarRooms(eq(76L), any(), any());
		verify(houseDrawerMapper).selectDrawerHouseClosedDates(eq(76L), any(), any(), eq(2));
	}

	@Test
	@DisplayName("drawer — 등록 객실 0개(미삭제)면 배치/휴관 분모·휴관 쿼리 생략, priceSummary 전 버킷 null, capacity 0")
	void drawerNoRegisteredRooms() {
		when(houseDrawerMapper.selectDrawerHouse(76L)).thenReturn(
				HouseDrawerVO.builder().houseSn(76L).houseNm("빈고택").prtnrConm("x").build());
		when(houseDrawerMapper.selectDrawerRooms(eq(76L), any(), any())).thenReturn(List.of());
		when(houseDrawerMapper.selectDrawerPriceBuckets(eq(76L), any(), any())).thenReturn(List.of());

		OperatorHouseDetailDrawerResponse res = service.detailDrawer(76L, null, null); // 기본 창

		assertTrue(res.rooms().isEmpty());
		assertEquals(0, res.capacity().bscPrsnTotal());
		assertNull(res.priceSummary().weekday());
		assertNull(res.priceSummary().peakSeason());
		assertTrue(res.houseClosedDates().isEmpty());
		verify(houseDrawerMapper, never()).selectDrawerBeddings(any());
		verify(houseDrawerMapper, never()).selectDrawerAmenities(any());
		verify(houseDrawerMapper, never()).selectDrawerImages(any());
		verify(houseDrawerMapper, never()).countDrawerCalendarRooms(anyLong(), any(), any());
		verify(houseDrawerMapper, never()).selectDrawerHouseClosedDates(anyLong(), any(), any(), anyInt());
	}

	@Test
	@DisplayName("drawer(A안) — DRAFT 객실만 2개인 고택도 drawer 에 2건·수용인원 합산(목록 roomCnt=2 와 동일 기준)")
	void drawerDraftOnlyRoomsCounted() {
		when(houseDrawerMapper.selectDrawerHouse(33L)).thenReturn(
				HouseDrawerVO.builder().houseSn(33L).houseNm("qweqwe").prtnrConm("개발").houseSttsCd("DRAFT").build());
		when(houseDrawerMapper.selectDrawerRooms(eq(33L), any(), any())).thenReturn(List.of(
				DrawerRoomVO.builder().roomSn(21L).roomNm("기본 객실").roomSttsCd("DRAFT").bscPrsnCnt(2).maxPrsnCnt(4).build(),
				DrawerRoomVO.builder().roomSn(22L).roomNm("123").roomSttsCd("DRAFT").bscPrsnCnt(3).maxPrsnCnt(5).build()));
		when(houseDrawerMapper.selectDrawerBeddings(any())).thenReturn(List.of());
		when(houseDrawerMapper.selectDrawerAmenities(any())).thenReturn(List.of());
		when(houseDrawerMapper.selectDrawerImages(any())).thenReturn(List.of());
		when(houseDrawerMapper.selectDrawerPriceBuckets(eq(33L), any(), any())).thenReturn(List.of());
		when(houseDrawerMapper.countDrawerCalendarRooms(eq(33L), any(), any())).thenReturn(0); // 캘린더 없음

		OperatorHouseDetailDrawerResponse res = service.detailDrawer(33L, null, null);

		assertEquals(2, res.rooms().size());
		assertEquals("DRAFT", res.rooms().get(0).roomSttsCd());
		assertEquals(5, res.capacity().bscPrsnTotal());  // 2 + 3
		assertEquals(9, res.capacity().maxPrsnTotal());  // 4 + 5
		assertTrue(res.houseClosedDates().isEmpty());
		// 캘린더 참여 객실 0 → 휴관일 쿼리 스킵
		verify(houseDrawerMapper).countDrawerCalendarRooms(eq(33L), any(), any());
		verify(houseDrawerMapper, never()).selectDrawerHouseClosedDates(anyLong(), any(), any(), anyInt());
	}

	@Test
	@DisplayName("drawer(A안) — ACTIVE 1 + DRAFT 1 혼합: drawer 2건·수용인원 2개 합산")
	void drawerMixedActiveDraftCounted() {
		when(houseDrawerMapper.selectDrawerHouse(50L)).thenReturn(
				HouseDrawerVO.builder().houseSn(50L).houseNm("혼합고택").prtnrConm("x").houseSttsCd("REVIEW_REQUESTED").build());
		when(houseDrawerMapper.selectDrawerRooms(eq(50L), any(), any())).thenReturn(List.of(
				DrawerRoomVO.builder().roomSn(1L).roomNm("사랑채").roomSttsCd("ACTIVE").bscPrsnCnt(2).maxPrsnCnt(4).build(),
				DrawerRoomVO.builder().roomSn(2L).roomNm("신규채").roomSttsCd("DRAFT").bscPrsnCnt(1).maxPrsnCnt(2).build()));
		when(houseDrawerMapper.selectDrawerBeddings(any())).thenReturn(List.of());
		when(houseDrawerMapper.selectDrawerAmenities(any())).thenReturn(List.of());
		when(houseDrawerMapper.selectDrawerImages(any())).thenReturn(List.of());
		when(houseDrawerMapper.selectDrawerPriceBuckets(eq(50L), any(), any())).thenReturn(List.of());
		when(houseDrawerMapper.countDrawerCalendarRooms(eq(50L), any(), any())).thenReturn(1);
		when(houseDrawerMapper.selectDrawerHouseClosedDates(eq(50L), any(), any(), eq(1))).thenReturn(List.of());

		OperatorHouseDetailDrawerResponse res = service.detailDrawer(50L, null, null);

		assertEquals(2, res.rooms().size());
		assertEquals(3, res.capacity().bscPrsnTotal());  // 2 + 1
		assertEquals(6, res.capacity().maxPrsnTotal());  // 4 + 2
	}

	@Test
	@DisplayName("drawer(A안 회귀방지) — ACTIVE 캘린더만·DRAFT 캘린더 없음: 휴관일 분모=calendarRoomCount(1)로 기존 ACTIVE 휴관일 유지")
	void drawerClosedDatesNotSwallowedByCalendarlessDraft() {
		when(houseDrawerMapper.selectDrawerHouse(51L)).thenReturn(
				HouseDrawerVO.builder().houseSn(51L).houseNm("혼합고택2").prtnrConm("x").houseSttsCd("ACTIVE").build());
		when(houseDrawerMapper.selectDrawerRooms(eq(51L), any(), any())).thenReturn(List.of(
				DrawerRoomVO.builder().roomSn(1L).roomNm("본채").roomSttsCd("ACTIVE").bscPrsnCnt(2).maxPrsnCnt(4).build(),
				DrawerRoomVO.builder().roomSn(2L).roomNm("초안채").roomSttsCd("DRAFT").bscPrsnCnt(2).maxPrsnCnt(2).build()));
		when(houseDrawerMapper.selectDrawerBeddings(any())).thenReturn(List.of());
		when(houseDrawerMapper.selectDrawerAmenities(any())).thenReturn(List.of());
		when(houseDrawerMapper.selectDrawerImages(any())).thenReturn(List.of());
		when(houseDrawerMapper.selectDrawerPriceBuckets(eq(51L), any(), any())).thenReturn(List.of());
		// 미삭제 객실은 2개지만 캘린더 보유 객실은 ACTIVE 1개뿐 → 분모 1
		when(houseDrawerMapper.countDrawerCalendarRooms(eq(51L), any(), any())).thenReturn(1);
		when(houseDrawerMapper.selectDrawerHouseClosedDates(eq(51L), any(), any(), eq(1)))
				.thenReturn(List.of(LocalDate.of(2026, 7, 14)));

		OperatorHouseDetailDrawerResponse res = service.detailDrawer(51L, null, null);

		// DRAFT 객실(캘린더 없음) 때문에 분모가 2로 부풀지 않아 기존 ACTIVE 휴관일이 유지됨
		assertEquals(List.of(LocalDate.of(2026, 7, 14)), res.houseClosedDates());
		verify(houseDrawerMapper).selectDrawerHouseClosedDates(eq(51L), any(), any(), eq(1));
		verify(houseDrawerMapper, never()).selectDrawerHouseClosedDates(anyLong(), any(), any(), eq(2));
	}

	@Test
	@DisplayName("상세 — 없으면 404")
	void detailNotFound() {
		when(houseMapper.selectHouseDetail(5L)).thenReturn(null);
		BusinessException e = assertThrows(BusinessException.class, () -> service.detail(5L));
		assertEquals(HouseErrorCode.HOUSE_NOT_FOUND, e.getErrorCode());
	}

	// ===== 운영개시 사전점검(publishCheck) — read-only, 점검 전용 resolver(승인게이트 미적용) + validator.evaluate 재사용 =====

	@Test
	@DisplayName("사전점검 — 전조건 충족: publishable=true + 빈 failedConditions + write 없음(read-only)")
	void publishCheckPublishable() {
		when(proxyResolver.resolveOwnerForHouseForPublishCheck(eq(5L), any())).thenReturn(100L);
		when(publishValidator.evaluate(5L, 100L)).thenReturn(List.of());

		PublishCheckResponse res = service.publishCheck(5L, operator(2));

		assertTrue(res.publishable());
		assertTrue(res.failedConditions().isEmpty());
		verifyNoInteractions(houseMapper, roomMapper, payMethodMapper, auditRecorder); // 상태변경/write 없음
	}

	@Test
	@DisplayName("사전점검 — 누락 있으면 publishable=false + failedConditions(code/fixableOn)")
	void publishCheckNotPublishable() {
		when(proxyResolver.resolveOwnerForHouseForPublishCheck(eq(5L), any())).thenReturn(100L);
		when(publishValidator.evaluate(5L, 100L)).thenReturn(List.of(PublishCondition.REPRESENTATIVE_IMAGE_MISSING));

		PublishCheckResponse res = service.publishCheck(5L, operator(2));

		assertFalse(res.publishable());
		assertEquals(1, res.failedConditions().size());
		assertEquals("REPRESENTATIVE_IMAGE_MISSING", res.failedConditions().get(0).code());
		assertEquals("HOUSE", res.failedConditions().get(0).fixableOn());
	}

	@Test
	@DisplayName("사전점검(★Blocking 회귀) — 파트너 미승인/비활성도 200 + publishable=false + PARTNER_NOT_APPROVED")
	void publishCheckPartnerNotApproved() {
		// 점검 전용 resolver 는 승인게이트를 안 타므로 owner 가 정상 반환되고, evaluate 가 PARTNER_NOT_APPROVED 를 수집한다.
		when(proxyResolver.resolveOwnerForHouseForPublishCheck(eq(5L), any())).thenReturn(100L);
		when(publishValidator.evaluate(5L, 100L)).thenReturn(List.of(PublishCondition.PARTNER_NOT_APPROVED));

		PublishCheckResponse res = service.publishCheck(5L, operator(2));

		assertFalse(res.publishable());
		assertEquals("PARTNER_NOT_APPROVED", res.failedConditions().get(0).code());
		assertEquals("PARTNER", res.failedConditions().get(0).fixableOn());
	}

	@Test
	@DisplayName("사전점검 — 타소유/삭제/없음: 점검 resolver가 404(evaluate 미진입)")
	void publishCheckForeignBlocked() {
		when(proxyResolver.resolveOwnerForHouseForPublishCheck(eq(5L), any()))
				.thenThrow(new BusinessException(HouseErrorCode.HOUSE_NOT_FOUND));
		BusinessException e = assertThrows(BusinessException.class, () -> service.publishCheck(5L, operator(2)));
		assertEquals(HouseErrorCode.HOUSE_NOT_FOUND, e.getErrorCode());
		verify(publishValidator, never()).evaluate(anyLong(), anyLong());
	}

	@Test
	@DisplayName("PublishCheckResponse 직렬화 — publishable + failedConditions[].code/message/fixableOn JSON 계약")
	void publishCheckResponseSerialization() throws Exception {
		PublishCheckResponse res = PublishCheckResponse.from(List.of(PublishCondition.REPRESENTATIVE_IMAGE_MISSING));
		String json = new ObjectMapper().writeValueAsString(res);
		assertTrue(json.contains("\"publishable\":false"));
		assertTrue(json.contains("\"failedConditions\""));
		assertTrue(json.contains("\"code\":\"REPRESENTATIVE_IMAGE_MISSING\""));
		assertTrue(json.contains("\"fixableOn\":\"HOUSE\""));
		assertTrue(json.contains("\"message\":\"대표이미지가 1개 이상 등록되어야 합니다.\""));
	}

	@Test
	@DisplayName("강제중지 — 활성 고택이면 SUSPENDED + 사유 감사기록")
	void suspendSuccess() {
		when(houseMapper.selectHouseForUpdate(5L)).thenReturn(house("ACTIVE", "N"));
		service.suspend(new HouseSuspendRequest(5L, "부적절한 사진"), operator(2));
		verify(houseMapper).forceSuspendHouse(5L, 2L);
		verify(auditRecorder).recordSuccess(any(), eq("HOUSE"), eq("5"));
	}

	@Test
	@DisplayName("강제중지 — DRAFT(미게시) 고택은 409(임시조치 대상 아님)")
	void suspendDraftRejected() {
		when(houseMapper.selectHouseForUpdate(5L)).thenReturn(house("DRAFT", "N"));
		assertThrows(BusinessException.class, () -> service.suspend(new HouseSuspendRequest(5L, null), operator(2)));
		verify(houseMapper, never()).forceSuspendHouse(anyLong(), anyLong());
	}

	@Test
	@DisplayName("강제중지 — 없으면 404, 갱신 진입 안 함")
	void suspendNotFound() {
		when(houseMapper.selectHouseForUpdate(5L)).thenReturn(null);
		BusinessException e = assertThrows(BusinessException.class,
				() -> service.suspend(new HouseSuspendRequest(5L, null), operator(2)));
		assertEquals(HouseErrorCode.HOUSE_NOT_FOUND, e.getErrorCode());
		verify(houseMapper, never()).forceSuspendHouse(anyLong(), anyLong());
	}

	@Test
	@DisplayName("해제 — SUSPENDED가 아니면 409")
	void releaseNotSuspended() {
		when(houseMapper.selectHouseForUpdate(5L)).thenReturn(house("ACTIVE", "N"));
		assertThrows(BusinessException.class, () -> service.release(new HouseReleaseRequest(5L), operator(2)));
		verify(houseMapper, never()).releaseHouse(anyLong(), anyLong());
	}

	@Test
	@DisplayName("해제 — SUSPENDED면 HIDDEN으로 해제")
	void releaseSuccess() {
		when(houseMapper.selectHouseForUpdate(5L)).thenReturn(house("SUSPENDED", "N"));
		service.release(new HouseReleaseRequest(5L), operator(2));
		verify(houseMapper).releaseHouse(5L, 2L);
		verify(auditRecorder).recordSuccess(any(), eq("HOUSE"), eq("5"));
	}

	// ===== 대행 정책/결제/발행(HOUSE_PROXY_MNG) — owner 서버해석, prtnrSn body 미수신 =====

	@Test
	@DisplayName("대행 정책 — owner 서버해석값으로 갱신(요청 body에 prtnrSn 없음)")
	void proxyUpdatePolicyUsesResolvedOwner() {
		when(proxyResolver.resolveOwnerForHouse(5L, operator(9))).thenReturn(1L);
		when(houseMapper.selectHouseForUpdate(5L)).thenReturn(houseOwned(1L, "DRAFT"));
		when(codeService.list("RSVT_CNFIRM")).thenReturn(List.of(code("RSVT_CNFIRM", "AUTO")));
		when(houseMapper.updateHousePolicy(anyLong(), anyLong(), any(), any(), any(), any(), any(), any(),
				any(), any(), any(), any(), anyLong())).thenReturn(1);
		ReadinessSnapshot snap = mock(ReadinessSnapshot.class);
		when(publishGuard.snapshotIfActive(5L, 1L)).thenReturn(snap);

		service.updatePolicy(
				new HousePolicyUpdateRequest(5L, 0, 0, 0, "15:00", "11:00", "13:00", "AUTO", "환불규정", null, "예약공지"),
				operator(9));

		// snapshotIfActive → mutation → enforceNoRegression(동일 스냅샷) 순서(owner=해석된 1L, 행위자=9L)
		InOrder ord = inOrder(publishGuard, houseMapper);
		ord.verify(publishGuard).snapshotIfActive(5L, 1L);
		ord.verify(houseMapper).updateHousePolicy(eq(5L), eq(1L), any(), any(), any(), any(), any(), any(),
				eq("AUTO"), any(), any(), eq("예약공지"), eq(9L));
		ord.verify(publishGuard).enforceNoRegression(5L, 1L, snap);
	}

	@Test
	@DisplayName("대행 정책 — 예약확정방식 코드 무효면 400, 갱신 진입 안 함")
	void proxyUpdatePolicyInvalidRsvtCnfirm() {
		when(proxyResolver.resolveOwnerForHouse(5L, operator(9))).thenReturn(1L);
		when(houseMapper.selectHouseForUpdate(5L)).thenReturn(houseOwned(1L, "DRAFT"));
		when(codeService.list("RSVT_CNFIRM")).thenReturn(List.of(code("RSVT_CNFIRM", "AUTO")));

		BusinessException e = assertThrows(BusinessException.class, () -> service.updatePolicy(
				new HousePolicyUpdateRequest(5L, null, null, null, null, null, null, "NOPE", null, null, null),
				operator(9)));
		assertEquals(HouseErrorCode.HOUSE_RSVT_CNFIRM_INVALID, e.getErrorCode());
		verify(houseMapper, never()).updateHousePolicy(anyLong(), anyLong(), any(), any(), any(), any(), any(), any(),
				any(), any(), any(), any(), anyLong());
	}

	@Test
	@DisplayName("대행 결제수단 — 전체교체(soft delete 후 재삽입), owner 서버해석")
	void proxySetPayMethodsReplaces() {
		when(proxyResolver.resolveOwnerForHouse(5L, operator(9))).thenReturn(1L);
		when(houseMapper.selectHouseForUpdate(5L)).thenReturn(houseOwned(1L, "DRAFT"));
		when(codeService.list("PAY_MTHD"))
				.thenReturn(List.of(code("PAY_MTHD", "CARD"), code("PAY_MTHD", "BANK_TRANSFER")));
		ReadinessSnapshot snap = mock(ReadinessSnapshot.class);
		when(publishGuard.snapshotIfActive(5L, 1L)).thenReturn(snap);

		service.setPayMethods(new HousePayMethodSetRequest(5L, List.of("CARD")), operator(9));

		verify(payMethodMapper).insertPayMethod(any());
		// snapshotIfActive → mutation(softDelete) → enforceNoRegression(동일 스냅샷) 순서
		InOrder ord = inOrder(publishGuard, payMethodMapper);
		ord.verify(publishGuard).snapshotIfActive(5L, 1L);
		ord.verify(payMethodMapper).softDeleteActiveByHouse(5L, 9L);
		ord.verify(publishGuard).enforceNoRegression(5L, 1L, snap);
	}

	@Test
	@DisplayName("대행 결제수단 — 코드 무효면 400, 비우기/삽입 진입 안 함")
	void proxySetPayMethodsInvalidCode() {
		when(proxyResolver.resolveOwnerForHouse(5L, operator(9))).thenReturn(1L);
		when(houseMapper.selectHouseForUpdate(5L)).thenReturn(houseOwned(1L, "DRAFT"));
		when(codeService.list("PAY_MTHD")).thenReturn(List.of(code("PAY_MTHD", "CARD")));

		BusinessException e = assertThrows(BusinessException.class, () -> service.setPayMethods(
				new HousePayMethodSetRequest(5L, List.of("NOPE")), operator(9)));
		assertEquals(HouseErrorCode.HOUSE_PAY_METHOD_INVALID, e.getErrorCode());
		verify(payMethodMapper, never()).softDeleteActiveByHouse(anyLong(), anyLong());
		verify(payMethodMapper, never()).insertPayMethod(any());
	}

	@Test
	@DisplayName("대행 상태변경 — ACTIVE 전이 시 발행검증 호출 후 갱신")
	void proxyChangeStatusActiveValidatesPublish() {
		when(proxyResolver.resolveOwnerForHouse(5L, operator(9))).thenReturn(1L);
		when(houseMapper.selectHouseForUpdate(5L)).thenReturn(houseOwned(1L, "DRAFT"));
		when(houseMapper.updateHouseStatus(5L, 1L, "ACTIVE", 9L)).thenReturn(1);

		service.changeStatus(new HouseStatusChangeRequest(5L, "ACTIVE"), operator(9));

		verify(publishValidator).validate(5L, 1L);
		verify(houseMapper).updateHouseStatus(5L, 1L, "ACTIVE", 9L);
	}

	@Test
	@DisplayName("대행 상태변경 — 현재 SUSPENDED면 409(해제는 release 전용), 발행검증·갱신 진입 안 함")
	void proxyChangeStatusCurrentSuspendedBlocked() {
		when(proxyResolver.resolveOwnerForHouse(5L, operator(9))).thenReturn(1L);
		when(houseMapper.selectHouseForUpdate(5L)).thenReturn(houseOwned(1L, "SUSPENDED"));

		assertThrows(BusinessException.class,
				() -> service.changeStatus(new HouseStatusChangeRequest(5L, "ACTIVE"), operator(9)));
		verify(publishValidator, never()).validate(anyLong(), anyLong());
		verify(houseMapper, never()).updateHouseStatus(anyLong(), anyLong(), any(), anyLong());
	}

	@Test
	@DisplayName("대행 수정 — owner 서버해석값으로 갱신(요청 body 불신)")
	void proxyUpdateUsesResolvedOwner() {
		when(proxyResolver.resolveOwnerForHouse(5L, operator(9))).thenReturn(1L);
		when(regionService.isValidHouseRegion(any(), any())).thenReturn(true);
		when(houseMapper.updateHouse(5L, 1L, "수정", null, null, null, null, null, null, 9L)).thenReturn(1);
		ReadinessSnapshot snap = mock(ReadinessSnapshot.class);
		when(publishGuard.snapshotIfActive(5L, 1L)).thenReturn(snap);

		service.update(new HouseUpdateRequest(5L, "수정", null, null, null, null, null, null), operator(9));

		// snapshotIfActive → mutation → enforceNoRegression(동일 스냅샷) 순서
		InOrder ord = inOrder(publishGuard, houseMapper);
		ord.verify(publishGuard).snapshotIfActive(5L, 1L);
		ord.verify(houseMapper).updateHouse(5L, 1L, "수정", null, null, null, null, null, null, 9L);
		ord.verify(publishGuard).enforceNoRegression(5L, 1L, snap);
	}

	@Test
	@DisplayName("대행 삭제 — soft delete + 하위 객실 동반(owner 서버해석, 행위자=운영자)")
	void proxyDeleteSoftDeletesHouseAndRooms() {
		when(proxyResolver.resolveOwnerForHouse(5L, operator(9))).thenReturn(1L);
		when(houseMapper.selectHouseForUpdate(5L)).thenReturn(houseOwned(1L, "DRAFT"));
		when(houseMapper.softDeleteHouse(5L, 1L, 9L)).thenReturn(1);

		service.delete(new HouseDeleteRequest(5L), operator(9));

		verify(roomMapper).softDeleteRoomsByHouse(5L, 1L, 9L);
		verify(houseMapper).softDeleteHouse(5L, 1L, 9L);
	}

	@Test
	@DisplayName("대행 삭제 — 소유 불일치(타 파트너)면 404, softDeleteHouse 진입 안 함")
	void proxyDeleteForeignOrMissing404() {
		when(proxyResolver.resolveOwnerForHouse(5L, operator(9))).thenReturn(1L);
		when(houseMapper.selectHouseForUpdate(5L)).thenReturn(houseOwned(999L, "DRAFT")); // owner 불일치

		BusinessException e = assertThrows(BusinessException.class,
				() -> service.delete(new HouseDeleteRequest(5L), operator(9)));
		assertEquals(HouseErrorCode.HOUSE_NOT_FOUND, e.getErrorCode());
		verify(houseMapper, never()).softDeleteHouse(anyLong(), anyLong(), anyLong());
	}
}
