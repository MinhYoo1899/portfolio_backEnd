package egovframework.eeum.house;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import egovframework.eeum.account.service.partner.PartnerService;
import egovframework.eeum.account.web.dto.PartnerDetailResponse;
import egovframework.eeum.base.exception.BusinessException;
import egovframework.eeum.house.exception.HouseErrorCode;
import egovframework.eeum.house.exception.HouseNotPublishableException;
import egovframework.eeum.house.service.calendar.dao.RoomCalendarMapper;
import egovframework.eeum.house.service.house.HousePublishGuard;
import egovframework.eeum.house.service.house.HousePublishValidator;
import egovframework.eeum.house.service.house.ReadinessSnapshot;
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
 * ACTIVE 편집 가드 — 비퇴행(non-regression) 검증. 실제 {@link HousePublishValidator}(mock 매퍼)로 편집 전/후
 * 원시 스냅샷을 만들고, "충족→미충족 전환"만 409, 고택 유실/소유권 불일치는 fail-closed 임을 확인한다.
 *
 * <p>가드는 편집 전 {@code selectHouseForUpdate}(FOR UPDATE 잠금 + owner/DEL_YN/status 검증) 후 스냅샷을 캡처하고,
 * 편집 후 {@code selectHouseDetail} 로 after 를 재평가한다. 전→후 값은 {@code thenReturn(before).thenReturn(after)} 로 표현.
 */
class HousePublishGuardVerification {

	private static final long HOUSE_SN = 5L;
	private static final long OWNER = 1L;
	private static final long ROOM_SN = 10L;

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
	private final HousePublishGuard guard = new HousePublishGuard(houseMapper, validator);

	/** 기본: 잠금(ACTIVE 소유) + 편집 전/후 스냅샷 모두 완전 충족(개별 테스트가 한 값만 before→after 로 흔든다). */
	@BeforeEach
	void bothSnapshotsFullyPublishable() {
		when(houseMapper.selectHouseForUpdate(HOUSE_SN)).thenReturn(lockedHouse("ACTIVE"));
		when(houseMapper.selectHouseDetail(HOUSE_SN)).thenReturn(house(OWNER));
		when(partnerService.detail(OWNER)).thenReturn(partner("APPROVED", "ACTIVE"));
		when(payMethodMapper.selectActiveCodesByHouse(HOUSE_SN)).thenReturn(List.of("CARD"));
		when(houseImageMapper.countRepresentativeImagesByHouse(HOUSE_SN)).thenReturn(1);
		when(roomMapper.selectRoomsByHouse(HOUSE_SN)).thenReturn(List.of(publishableRoom(ROOM_SN)));
		when(roomBeddingMapper.selectActiveByRoom(ROOM_SN)).thenReturn(List.of(new RoomBeddingVO()));
		when(roomImageMapper.countActiveImagesByRoom(ROOM_SN)).thenReturn(1);
		when(roomCalendarMapper.countFutureSellableByRoom(eq(ROOM_SN), any())).thenReturn(1);
	}

	private void guardEdit() {
		ReadinessSnapshot before = guard.snapshotIfActive(HOUSE_SN, OWNER);
		guard.enforceNoRegression(HOUSE_SN, OWNER, before);
	}

	// ===== 개선(성공) / 파괴(409) 기본 =====

	@Test
	@DisplayName("이미 미달(침구0·캘린더0)인 ACTIVE 고택에서 침구 추가(개선) — 성공(교착 해소)")
	void improveBeddingOnDeficientRoomSucceeds() {
		when(roomCalendarMapper.countFutureSellableByRoom(eq(ROOM_SN), any())).thenReturn(0);
		when(roomBeddingMapper.selectActiveByRoom(ROOM_SN)).thenReturn(List.of()).thenReturn(List.of(new RoomBeddingVO()));
		assertDoesNotThrow(this::guardEdit);
	}

	@Test
	@DisplayName("개선으로 전 조건 충족(캘린더 추가) — 성공")
	void improvingToFullComplianceSucceeds() {
		when(roomCalendarMapper.countFutureSellableByRoom(eq(ROOM_SN), any())).thenReturn(0).thenReturn(1);
		assertDoesNotThrow(this::guardEdit);
	}

	@Test
	@DisplayName("기존 미달(캘린더0) 유지한 채 충족 조건(대표이미지)을 깨뜨림 — 409")
	void breakingAnotherMetConditionWhileDeficientFails() {
		when(roomCalendarMapper.countFutureSellableByRoom(eq(ROOM_SN), any())).thenReturn(0);
		when(houseImageMapper.countRepresentativeImagesByHouse(HOUSE_SN)).thenReturn(1).thenReturn(0);
		assertEquals("REPRESENTATIVE_IMAGE_MISSING", firstFailedCode(assertThrows(HouseNotPublishableException.class, this::guardEdit)));
	}

	@Test
	@DisplayName("완전 충족 ACTIVE 고택을 미달로 만드는 변경(침구 비움) — 409")
	void makingFullyPublishableRoomDeficientFails() {
		when(roomBeddingMapper.selectActiveByRoom(ROOM_SN)).thenReturn(List.of(new RoomBeddingVO())).thenReturn(List.of());
		assertEquals("PUBLISHABLE_ROOM_MISSING", firstFailedCode(assertThrows(HouseNotPublishableException.class, this::guardEdit)));
	}

	@Test
	@DisplayName("유일한 게시가능 객실 삭제(after 에 객실 없음) — 집계 조건 전환으로 409")
	void deletingTheOnlyPublishableRoomFails() {
		when(roomMapper.selectRoomsByHouse(HOUSE_SN)).thenReturn(List.of(publishableRoom(ROOM_SN))).thenReturn(List.of());
		assertEquals("PUBLISHABLE_ROOM_MISSING", firstFailedCode(assertThrows(HouseNotPublishableException.class, this::guardEdit)));
	}

	// ===== 다중 객실 집계 의미 — 객실별 키는 직접 퇴행 아님, HAS_PUBLISHABLE_ROOM(집계)만 판정 =====

	@Test
	@DisplayName("게시가능 객실 2개 중 1개를 DRAFT 로 전환 — 나머지 1개가 게시가능이라 성공")
	void oneOfTwoPublishableRoomsToDraftSucceeds() {
		when(roomMapper.selectRoomsByHouse(HOUSE_SN))
				.thenReturn(List.of(publishableRoom(ROOM_SN), publishableRoom(20L)))
				.thenReturn(List.of(publishableRoom(ROOM_SN),
						RoomVO.builder().roomSn(20L).roomSttsCd("DRAFT").roomCnt(1).bathrmCnt(1).build()));
		when(roomBeddingMapper.selectActiveByRoom(20L)).thenReturn(List.of(new RoomBeddingVO()));
		when(roomImageMapper.countActiveImagesByRoom(20L)).thenReturn(1);
		when(roomCalendarMapper.countFutureSellableByRoom(eq(20L), any())).thenReturn(1);
		assertDoesNotThrow(this::guardEdit);
	}

	@Test
	@DisplayName("게시가능 객실 2개 중 1개의 침구를 비워도 — 나머지 1개가 게시가능이라 성공(객실별 키는 직접 퇴행 아님)")
	void breakingOneOfTwoPublishableRoomsSucceeds() {
		when(roomMapper.selectRoomsByHouse(HOUSE_SN)).thenReturn(List.of(publishableRoom(ROOM_SN), publishableRoom(20L)));
		when(roomBeddingMapper.selectActiveByRoom(20L)).thenReturn(List.of(new RoomBeddingVO())).thenReturn(List.of());
		when(roomImageMapper.countActiveImagesByRoom(20L)).thenReturn(1);
		when(roomCalendarMapper.countFutureSellableByRoom(eq(20L), any())).thenReturn(1);
		assertDoesNotThrow(this::guardEdit);
	}

	// ===== 원시 조건 분해 — 같은 카테고리 내 "추가 훼손" 감지(단순 집합 비교면 놓침) =====

	@Test
	@DisplayName("기존 houseDc 미달 ACTIVE 고택에서 addr 까지 제거 — 409(HOUSE_BASIC_INFO 원시키 addr 전환)")
	void houseDcDeficientThenRemoveAddrFails() {
		HouseVO before = house(OWNER);
		before.setHouseDc(null); // 이미 미달
		HouseVO after = house(OWNER);
		after.setHouseDc(null);
		after.setAddr(null); // 추가 훼손
		when(houseMapper.selectHouseDetail(HOUSE_SN)).thenReturn(before).thenReturn(after);
		assertEquals("HOUSE_BASIC_INFO_MISSING", firstFailedCode(assertThrows(HouseNotPublishableException.class, this::guardEdit)));
	}

	@Test
	@DisplayName("기존 checkin 미달 ACTIVE 고택에서 checkout 까지 제거 — 409(CHECKIN_CHECKOUT 원시키 checkout 전환)")
	void checkinDeficientThenRemoveCheckoutFails() {
		HouseVO before = house(OWNER);
		before.setChkinTime(null); // 이미 미달
		HouseVO after = house(OWNER);
		after.setChkinTime(null);
		after.setChkoutTime(null); // 추가 훼손
		when(houseMapper.selectHouseDetail(HOUSE_SN)).thenReturn(before).thenReturn(after);
		assertEquals("CHECKIN_CHECKOUT_TIME_MISSING", firstFailedCode(assertThrows(HouseNotPublishableException.class, this::guardEdit)));
	}

	// ===== Room update — roomCnt/bathrmCnt 충족→미충족 =====

	@Test
	@DisplayName("Room update 로 roomCnt 충족→미충족 — 409")
	void roomCntRegressionFails() {
		when(roomMapper.selectRoomsByHouse(HOUSE_SN))
				.thenReturn(List.of(publishableRoom(ROOM_SN)))
				.thenReturn(List.of(RoomVO.builder().roomSn(ROOM_SN).roomSttsCd("ACTIVE").roomCnt(null).bathrmCnt(1).build()));
		assertEquals("PUBLISHABLE_ROOM_MISSING", firstFailedCode(assertThrows(HouseNotPublishableException.class, this::guardEdit)));
	}

	@Test
	@DisplayName("Room update 로 bathrmCnt 충족→미충족 — 409")
	void bathrmCntRegressionFails() {
		when(roomMapper.selectRoomsByHouse(HOUSE_SN))
				.thenReturn(List.of(publishableRoom(ROOM_SN)))
				.thenReturn(List.of(RoomVO.builder().roomSn(ROOM_SN).roomSttsCd("ACTIVE").roomCnt(1).bathrmCnt(null).build()));
		assertEquals("PUBLISHABLE_ROOM_MISSING", firstFailedCode(assertThrows(HouseNotPublishableException.class, this::guardEdit)));
	}

	// ===== fail-closed — after 스냅샷 시 고택 유실/소유권 불일치 =====

	@Test
	@DisplayName("after 스냅샷 시 고택 유실(삭제) — fail-closed HOUSE_NOT_FOUND(빈 스냅샷 금지)")
	void afterSnapshotHouseLostFailsClosed() {
		when(houseMapper.selectHouseDetail(HOUSE_SN)).thenReturn(house(OWNER)).thenReturn(null);
		BusinessException e = assertThrows(BusinessException.class, this::guardEdit);
		assertEquals(HouseErrorCode.HOUSE_NOT_FOUND, e.getErrorCode());
	}

	@Test
	@DisplayName("after 스냅샷 시 소유권 불일치 — fail-closed HOUSE_NOT_FOUND")
	void afterSnapshotOwnershipMismatchFailsClosed() {
		when(houseMapper.selectHouseDetail(HOUSE_SN)).thenReturn(house(OWNER)).thenReturn(house(999L));
		BusinessException e = assertThrows(BusinessException.class, this::guardEdit);
		assertEquals(HouseErrorCode.HOUSE_NOT_FOUND, e.getErrorCode());
	}

	@Test
	@DisplayName("편집 전 캡처 시 타인 고택(잠긴 행 기준 소유권 불일치) — fail-closed HOUSE_NOT_FOUND")
	void captureOnForeignHouseFailsClosed() {
		when(houseMapper.selectHouseForUpdate(HOUSE_SN)).thenReturn(lockedHouse("ACTIVE", 999L));
		BusinessException e = assertThrows(BusinessException.class, () -> guard.snapshotIfActive(HOUSE_SN, OWNER));
		assertEquals(HouseErrorCode.HOUSE_NOT_FOUND, e.getErrorCode());
	}

	// ===== 비ACTIVE no-op =====

	@Test
	@DisplayName("비ACTIVE(DRAFT) 고택 — 캡처 null, enforce no-op(스냅샷 미구성). 기존 동작 보존")
	void nonActiveIsNoOp() {
		when(houseMapper.selectHouseForUpdate(HOUSE_SN)).thenReturn(lockedHouse("DRAFT"));
		ReadinessSnapshot before = guard.snapshotIfActive(HOUSE_SN, OWNER);
		assertNull(before);
		assertDoesNotThrow(() -> guard.enforceNoRegression(HOUSE_SN, OWNER, before));
		verify(houseMapper, never()).selectHouseDetail(anyLong()); // 비ACTIVE 면 스냅샷 자체를 만들지 않음
	}

	// ------------------------------------------------------------------

	private static String firstFailedCode(HouseNotPublishableException e) {
		return e.failedConditions().get(0).code();
	}

	private static PartnerDetailResponse partner(String aprvStts, String acntStts) {
		return new PartnerDetailResponse("1", "7", "p@eeum.kr", "파트너", acntStts, "고즈넉", "1234567890",
				"홍대표", null, "서울시 종로구", null, null, null, aprvStts, null, null, null, null, null);
	}

	/** selectHouseForUpdate 반환(잠금+검증용) — prtnrSn/delYn/houseSttsCd 만. */
	private static HouseVO lockedHouse(String stts) {
		return lockedHouse(stts, OWNER);
	}

	private static HouseVO lockedHouse(String stts, long prtnrSn) {
		return HouseVO.builder().houseSn(HOUSE_SN).prtnrSn(prtnrSn).delYn("N").houseSttsCd(stts).build();
	}

	/** selectHouseDetail 반환(스냅샷용) — 전조건 충족 기본값. */
	private static HouseVO house(long prtnrSn) {
		return HouseVO.builder().houseSn(HOUSE_SN).prtnrSn(prtnrSn).houseNm("고택").zip("12345").addr("주소")
				.houseDc("소개").chkinTime(LocalTime.of(15, 0)).chkoutTime(LocalTime.of(11, 0))
				.rsvtCnfirmCd("AUTO").cnclRfndPlcyCn("취소·환불규정").delYn("N").build();
	}

	private static RoomVO publishableRoom(long roomSn) {
		return RoomVO.builder().roomSn(roomSn).roomSttsCd("ACTIVE").roomCnt(1).bathrmCnt(1).build();
	}
}
