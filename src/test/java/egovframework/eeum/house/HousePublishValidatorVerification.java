package egovframework.eeum.house;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalTime;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import egovframework.eeum.account.service.partner.PartnerService;
import egovframework.eeum.account.web.dto.PartnerDetailResponse;
import egovframework.eeum.base.exception.BusinessException;
import egovframework.eeum.base.exception.GlobalExceptionHandler;
import egovframework.eeum.base.response.ApiResponse;
import egovframework.eeum.house.exception.HouseErrorCode;
import egovframework.eeum.house.exception.HouseNotPublishableException;
import egovframework.eeum.house.exception.PublishCondition;
import egovframework.eeum.house.exception.PublishGateConditionFailure;
import egovframework.eeum.house.exception.PublishGateResult;
import egovframework.eeum.house.service.calendar.dao.RoomCalendarMapper;
import egovframework.eeum.house.service.house.HousePublishValidator;
import egovframework.eeum.house.service.house.dao.HouseMapper;
import egovframework.eeum.house.service.house.dao.HousePayMethodMapper;
import egovframework.eeum.house.service.image.dao.HouseImageMapper;
import egovframework.eeum.house.service.image.dao.RoomImageMapper;
import egovframework.eeum.house.service.room.dao.RoomBeddingMapper;
import egovframework.eeum.house.service.room.dao.RoomMapper;
import egovframework.eeum.house.vo.HouseVO;
import egovframework.eeum.house.vo.RoomBeddingVO;
import egovframework.eeum.house.vo.RoomVO;

/**
 * 발행(게시) 게이트 검증 — 모든 필수조건 충족 시 통과, 하나라도 빠지면 차단(DB 없이 mock).
 *
 * <p>codex MUST: 소유권 자체검증(타인 고택 정보누출 차단), 파트너 APPROVED+ACTIVE, 각 조건별 차단.
 */
class HousePublishValidatorVerification {

	private static final long HOUSE_SN = 5L;
	private static final long OWNER = 1L;

	private final HouseMapper houseMapper = mock(HouseMapper.class);
	private final HousePayMethodMapper payMethodMapper = mock(HousePayMethodMapper.class);
	private final HouseImageMapper houseImageMapper = mock(HouseImageMapper.class);
	private final RoomMapper roomMapper = mock(RoomMapper.class);
	private final RoomBeddingMapper roomBeddingMapper = mock(RoomBeddingMapper.class);
	private final RoomImageMapper roomImageMapper = mock(RoomImageMapper.class);
	private final RoomCalendarMapper roomCalendarMapper = mock(RoomCalendarMapper.class);
	private final PartnerService partnerService = mock(PartnerService.class);

	private final HousePublishValidator validator = new HousePublishValidator(
			houseMapper, payMethodMapper, houseImageMapper, roomMapper, roomBeddingMapper,
			roomImageMapper, roomCalendarMapper, partnerService);

	/** 모든 필수조건을 충족하는 기준 시나리오 — 개별 테스트에서 한 조건만 무너뜨린다. */
	@BeforeEach
	void allConditionsMet() {
		when(partnerService.detail(OWNER)).thenReturn(partner("APPROVED", "ACTIVE"));
		when(houseMapper.selectHouseDetail(HOUSE_SN)).thenReturn(house(OWNER));
		when(payMethodMapper.selectActiveCodesByHouse(HOUSE_SN)).thenReturn(List.of("CARD"));
		when(houseImageMapper.countRepresentativeImagesByHouse(HOUSE_SN)).thenReturn(1);
		when(roomMapper.selectRoomsByHouse(HOUSE_SN)).thenReturn(List.of(publishableRoom(10L)));
		when(roomBeddingMapper.selectActiveByRoom(10L)).thenReturn(List.of(new RoomBeddingVO()));
		when(roomImageMapper.countActiveImagesByRoom(10L)).thenReturn(1);
		when(roomCalendarMapper.countFutureSellableByRoom(eq(10L), any())).thenReturn(1);
	}

	@Test
	@DisplayName("모든 조건 충족 — 통과")
	void allMetPasses() {
		assertDoesNotThrow(() -> validator.validate(HOUSE_SN, OWNER));
	}

	@Test
	@DisplayName("통신판매업신고번호(mailOrderSalesNo) null이어도 발행 통과 — C1 미적용 고정(게이트는 기존 9조건만)")
	void mailOrderSalesNoNotRequiredForPublish() {
		// partner() 픽스처의 mailOrderSalesNo=null. 9조건 충족 상태에서 발행이 통과해야 한다(C1 신고번호 필수화 미적용).
		// 향후 실수로 게이트에 신고번호 필수조건이 추가되면 이 테스트가 깨져 분리 약속을 지킨다.
		assertDoesNotThrow(() -> validator.validate(HOUSE_SN, OWNER));
	}

	@Test
	@DisplayName("소유권 불일치 — 타인 고택은 404(발행가능 여부 정보누출 차단)")
	void foreignOwnerNotFound() {
		when(houseMapper.selectHouseDetail(HOUSE_SN)).thenReturn(house(999L));
		BusinessException e = assertThrows(BusinessException.class, () -> validator.validate(HOUSE_SN, OWNER));
		assertEquals(HouseErrorCode.HOUSE_NOT_FOUND, e.getErrorCode());
	}

	@Test
	@DisplayName("파트너 계정 비활성(승인됐어도 SUSPENDED) — 발행 불가")
	void partnerNotActiveFails() {
		when(partnerService.detail(OWNER)).thenReturn(partner("APPROVED", "SUSPENDED"));
		BusinessException e = assertThrows(BusinessException.class, () -> validator.validate(HOUSE_SN, OWNER));
		assertEquals(HouseErrorCode.HOUSE_NOT_PUBLISHABLE, e.getErrorCode());
	}

	@Test
	@DisplayName("파트너 미승인 — 발행 불가")
	void partnerNotApprovedFails() {
		when(partnerService.detail(OWNER)).thenReturn(partner("PENDING", "ACTIVE"));
		assertThrows(BusinessException.class, () -> validator.validate(HOUSE_SN, OWNER));
	}

	@Test
	@DisplayName("정책 미완(취소·환불규정 공백) — 발행 불가")
	void missingPolicyFails() {
		HouseVO h = house(OWNER);
		h.setCnclRfndPlcyCn("  ");
		when(houseMapper.selectHouseDetail(HOUSE_SN)).thenReturn(h);
		assertThrows(BusinessException.class, () -> validator.validate(HOUSE_SN, OWNER));
	}

	@Test
	@DisplayName("결제수단 없음 — 발행 불가")
	void noPayMethodFails() {
		when(payMethodMapper.selectActiveCodesByHouse(HOUSE_SN)).thenReturn(List.of());
		assertThrows(BusinessException.class, () -> validator.validate(HOUSE_SN, OWNER));
	}

	@Test
	@DisplayName("대표이미지 없음 — 발행 불가")
	void noRepImageFails() {
		when(houseImageMapper.countRepresentativeImagesByHouse(HOUSE_SN)).thenReturn(0);
		assertThrows(BusinessException.class, () -> validator.validate(HOUSE_SN, OWNER));
	}

	@Test
	@DisplayName("게시가능 객실 없음(침구 누락) — 발행 불가")
	void noPublishableRoomFails() {
		when(roomBeddingMapper.selectActiveByRoom(10L)).thenReturn(List.of());
		assertThrows(BusinessException.class, () -> validator.validate(HOUSE_SN, OWNER));
	}

	@Test
	@DisplayName("미래 판매가능 캘린더 없음(완비 객실이 캘린더 미보유) — 발행 불가")
	void noFutureCalendarFails() {
		when(roomCalendarMapper.countFutureSellableByRoom(eq(10L), any())).thenReturn(0);
		assertThrows(BusinessException.class, () -> validator.validate(HOUSE_SN, OWNER));
	}

	@Test
	@DisplayName("완비 객실과 캘린더가 다른 객실에 분산 — 발행 불가(객실-결속 우회 차단)")
	void publishableRoomAndCalendarOnDifferentRoomsFails() {
		// A(10): 완비(침구·이미지 OK)지만 미래 판매캘린더 없음 / B(20): 캘린더는 있으나 비완비(침구 누락)
		when(roomMapper.selectRoomsByHouse(HOUSE_SN))
				.thenReturn(List.of(publishableRoom(10L), publishableRoom(20L)));
		when(roomBeddingMapper.selectActiveByRoom(10L)).thenReturn(List.of(new RoomBeddingVO()));
		when(roomImageMapper.countActiveImagesByRoom(10L)).thenReturn(1);
		when(roomCalendarMapper.countFutureSellableByRoom(eq(10L), any())).thenReturn(0);
		when(roomBeddingMapper.selectActiveByRoom(20L)).thenReturn(List.of()); // 비완비
		when(roomCalendarMapper.countFutureSellableByRoom(eq(20L), any())).thenReturn(1);

		BusinessException e = assertThrows(BusinessException.class, () -> validator.validate(HOUSE_SN, OWNER));
		assertEquals(HouseErrorCode.HOUSE_NOT_PUBLISHABLE, e.getErrorCode());
	}

	// ===== 409 구조화 (failedConditions 전체 수집) =====

	@Test
	@DisplayName("다중 누락 — 미충족 조건 전체 수집(조건 순서대로 code 목록)")
	void collectsAllFailedConditions() {
		when(partnerService.detail(OWNER)).thenReturn(partner("APPROVED", "SUSPENDED")); // 조건1
		when(payMethodMapper.selectActiveCodesByHouse(HOUSE_SN)).thenReturn(List.of());  // 조건7
		when(houseImageMapper.countRepresentativeImagesByHouse(HOUSE_SN)).thenReturn(0); // 조건8

		HouseNotPublishableException e = assertThrows(HouseNotPublishableException.class,
				() -> validator.validate(HOUSE_SN, OWNER));
		List<String> codes = e.failedConditions().stream().map(PublishGateConditionFailure::code).toList();
		assertEquals(List.of("PARTNER_NOT_APPROVED", "PAYMENT_METHOD_MISSING", "REPRESENTATIVE_IMAGE_MISSING"), codes);
		assertEquals(HouseErrorCode.HOUSE_NOT_PUBLISHABLE, e.getErrorCode());
	}

	@Test
	@DisplayName("단일 누락 — code + fixableOn 매핑(대표이미지=HOUSE)")
	void singleConditionMapping() {
		when(houseImageMapper.countRepresentativeImagesByHouse(HOUSE_SN)).thenReturn(0);
		HouseNotPublishableException e = assertThrows(HouseNotPublishableException.class,
				() -> validator.validate(HOUSE_SN, OWNER));
		assertEquals(1, e.failedConditions().size());
		PublishGateConditionFailure f = e.failedConditions().get(0);
		assertEquals("REPRESENTATIVE_IMAGE_MISSING", f.code());
		assertEquals("HOUSE", f.fixableOn());
	}

	@Test
	@DisplayName("파트너 조건 — fixableOn=PARTNER(상품관리 화면에서 해결 불가 신호)")
	void partnerConditionFixableOnPartner() {
		when(partnerService.detail(OWNER)).thenReturn(partner("PENDING", "ACTIVE"));
		HouseNotPublishableException e = assertThrows(HouseNotPublishableException.class,
				() -> validator.validate(HOUSE_SN, OWNER));
		assertEquals("PARTNER_NOT_APPROVED", e.failedConditions().get(0).code());
		assertEquals("PARTNER", e.failedConditions().get(0).fixableOn());
	}

	@Test
	@DisplayName("핸들러 직렬화 — 409 + HOUSE_NOT_PUBLISHABLE + result.failedConditions + resultMessage 첫 사유 유지")
	void handlerProducesStructured409() {
		when(houseImageMapper.countRepresentativeImagesByHouse(HOUSE_SN)).thenReturn(0);
		HouseNotPublishableException ex = assertThrows(HouseNotPublishableException.class,
				() -> validator.validate(HOUSE_SN, OWNER));

		GlobalExceptionHandler handler = new GlobalExceptionHandler(new ObjectMapper());
		ResponseEntity<ApiResponse<?>> resp = handler.handleBusiness(ex);

		assertEquals(409, resp.getStatusCode().value());
		ApiResponse<?> body = resp.getBody();
		assertEquals("HOUSE_NOT_PUBLISHABLE", body.resultCode());
		assertTrue(body.result() instanceof PublishGateResult, "result 는 PublishGateResult");
		PublishGateResult result = (PublishGateResult) body.result();
		assertEquals("REPRESENTATIVE_IMAGE_MISSING", result.failedConditions().get(0).code());
		assertTrue(body.resultMessage().contains("대표이미지"), "resultMessage 에 첫 미충족 사유 포함(기존 형식 유지)");
	}

	@Test
	@DisplayName("9조건 동시 누락 — failedConditions 에 9개 전부 순서대로(code), mailOrderSalesNo 미포함")
	void allNineConditionsCollected() {
		// 파트너 미승인 + 사업자정보 공백(조건1·2)
		when(partnerService.detail(OWNER)).thenReturn(new PartnerDetailResponse(
				"1", "7", "p@eeum.kr", "파트너", "ACTIVE",
				null, null, null, null, null, null, null, null, "PENDING", null, null, null, null, null));
		// 소유는 맞지만 기본정보/시각/예약확정/취소환불 전부 공백(조건3~6) — delYn=N, prtnrSn=OWNER 라 404 아님
		when(houseMapper.selectHouseDetail(HOUSE_SN)).thenReturn(
				HouseVO.builder().houseSn(HOUSE_SN).prtnrSn(OWNER).delYn("N").build());
		when(payMethodMapper.selectActiveCodesByHouse(HOUSE_SN)).thenReturn(List.of());     // 조건7
		when(houseImageMapper.countRepresentativeImagesByHouse(HOUSE_SN)).thenReturn(0);    // 조건8
		when(roomMapper.selectRoomsByHouse(HOUSE_SN)).thenReturn(List.of());                // 조건9

		HouseNotPublishableException e = assertThrows(HouseNotPublishableException.class,
				() -> validator.validate(HOUSE_SN, OWNER));
		List<String> codes = e.failedConditions().stream().map(PublishGateConditionFailure::code).toList();
		assertEquals(List.of(
				"PARTNER_NOT_APPROVED", "PARTNER_BUSINESS_INFO_MISSING", "HOUSE_BASIC_INFO_MISSING",
				"CHECKIN_CHECKOUT_TIME_MISSING", "RESERVATION_CONFIRM_TYPE_MISSING", "CANCELLATION_POLICY_MISSING",
				"PAYMENT_METHOD_MISSING", "REPRESENTATIVE_IMAGE_MISSING", "PUBLISHABLE_ROOM_MISSING"), codes);
	}

	@Test
	@DisplayName("핸들러 JSON 직렬화 — result.failedConditions[].code/message/fixableOn 키·값(record 회귀 방어)")
	void serializesStructured409Json() throws Exception {
		when(houseImageMapper.countRepresentativeImagesByHouse(HOUSE_SN)).thenReturn(0);
		HouseNotPublishableException ex = assertThrows(HouseNotPublishableException.class,
				() -> validator.validate(HOUSE_SN, OWNER));

		GlobalExceptionHandler handler = new GlobalExceptionHandler(new ObjectMapper());
		String json = new ObjectMapper().writeValueAsString(handler.handleBusiness(ex).getBody());

		assertTrue(json.contains("\"resultCode\":\"HOUSE_NOT_PUBLISHABLE\""));
		assertTrue(json.contains("\"failedConditions\""));
		assertTrue(json.contains("\"code\":\"REPRESENTATIVE_IMAGE_MISSING\""));
		assertTrue(json.contains("\"fixableOn\":\"HOUSE\""));
		assertTrue(json.contains("\"message\":\"대표이미지가 1개 이상 등록되어야 합니다.\""));
	}

	// ===== evaluate() — read-only 수집(throw 안 함, 사전점검 공용) =====

	@Test
	@DisplayName("evaluate — 전조건 충족 시 빈 리스트(throw 없음)")
	void evaluateReturnsEmptyWhenAllMet() {
		assertTrue(validator.evaluate(HOUSE_SN, OWNER).isEmpty());
	}

	@Test
	@DisplayName("evaluate — 누락 조건 수집(대표이미지 없음 → REPRESENTATIVE_IMAGE_MISSING)")
	void evaluateCollectsMissing() {
		when(houseImageMapper.countRepresentativeImagesByHouse(HOUSE_SN)).thenReturn(0);
		assertEquals(List.of(PublishCondition.REPRESENTATIVE_IMAGE_MISSING), validator.evaluate(HOUSE_SN, OWNER));
	}

	@Test
	@DisplayName("evaluate — 타인/삭제 고택은 수집이 아니라 404 우선(throw)")
	void evaluateForeignNotFound() {
		when(houseMapper.selectHouseDetail(HOUSE_SN)).thenReturn(house(999L));
		BusinessException e = assertThrows(BusinessException.class, () -> validator.evaluate(HOUSE_SN, OWNER));
		assertEquals(HouseErrorCode.HOUSE_NOT_FOUND, e.getErrorCode());
	}

	// ------------------------------------------------------------------

	private static PartnerDetailResponse partner(String aprvStts, String acntStts) {
		return new PartnerDetailResponse("1", "7", "p@eeum.kr", "파트너", acntStts, "고즈넉", "1234567890",
				"홍대표", null, "서울시 종로구", null, null, null, aprvStts, null, null, null, null, null);
	}

	private static HouseVO house(long prtnrSn) {
		return HouseVO.builder().houseSn(HOUSE_SN).prtnrSn(prtnrSn).houseNm("고택").zip("12345").addr("주소")
				.houseDc("소개").chkinTime(LocalTime.of(15, 0)).chkoutTime(LocalTime.of(11, 0))
				.rsvtCnfirmCd("AUTO").cnclRfndPlcyCn("취소·환불규정").delYn("N").build();
	}

	private static RoomVO publishableRoom(long roomSn) {
		return RoomVO.builder().roomSn(roomSn).roomSttsCd("ACTIVE").roomCnt(1).bathrmCnt(1).build();
	}
}
