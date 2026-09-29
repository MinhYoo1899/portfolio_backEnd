package egovframework.eeum.reservation.service.reservation.dao;

import java.time.LocalDate;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import egovframework.eeum.reservation.vo.ReservationAgreeVO;
import egovframework.eeum.reservation.vo.ReservationCalendarNightVO;
import egovframework.eeum.reservation.vo.ReservationGuestVO;
import egovframework.eeum.reservation.vo.ReservationItemNightVO;
import egovframework.eeum.reservation.vo.ReservationItemVO;
import egovframework.eeum.reservation.vo.ReservationRoomOwnershipVO;
import egovframework.eeum.reservation.vo.ReservationVO;

/**
 * 예약 매퍼 — 예약 도메인 자체 매퍼. 소유권/판매성/재고/약관을 읽기 위해 ROOM/HOUSE/ROOM_CALENDAR/TRMS_MST 를
 * 직접 SELECT 한다(타 도메인 매퍼 미호출 — ArchUnit 도메인 격리 준수).
 */
@Mapper
public interface ReservationMapper {

	// ---- 읽기(소유권/재고/약관 해석) ----

	/** 객실 소유권/판매성 해석 — ROOM→HOUSE 조인(houseSn/prtnrSn/roomNm/객실·고택 상태). 없으면 null. */
	ReservationRoomOwnershipVO selectRoomOwnership(@Param("roomSn") long roomSn);

	/** [체크인, 체크아웃) 반개구간의 캘린더 행(숙박일별 재고/단가/마감). 누락 밤은 결과에서 빠진다. */
	List<ReservationCalendarNightVO> selectCalendarNights(@Param("roomSn") long roomSn,
			@Param("checkIn") LocalDate checkIn, @Param("checkOut") LocalDate checkOut);

	/** 멱등 해시로 기존 예약 조회 — (예약자, IDEMPOTENCY_KEY) 스코프. 있으면 RESERVATION_SN, 없으면 null. */
	Long selectExistingByIdemHash(@Param("userSn") long userSn, @Param("idemHash") String idemHash);

	/** 약관 버전번호 해석(TRMS_MST) — 동의 스냅샷용. 없으면 null. */
	String selectTermsVersion(@Param("trmsSn") long trmsSn);

	// ---- 쓰기(집합체 insert) ----

	/** 예약 헤더 insert(useGeneratedKeys → reservationSn). */
	void insertReservation(ReservationVO vo);

	/** 예약 항목 insert(useGeneratedKeys → reservationItemSn). */
	void insertItem(ReservationItemVO vo);

	/** 예약 일자별 단가 insert. */
	void insertItemNight(ReservationItemNightVO vo);

	/** 예약 게스트 insert(GUEST_MBL_TELNO 암호화 TypeHandler 바인딩). */
	void insertGuest(ReservationGuestVO vo);

	/** 예약 동의 스냅샷 insert. */
	void insertAgre(ReservationAgreeVO vo);

	// ---- 읽기(본인 예약 조회) ----

	/** 본인(예약자) 예약 상세 — RESERVATION_SN + RESERVER_USER_SN 일치만. 타 소유/없음 → null. */
	ReservationVO selectReservationOwned(@Param("reservationSn") long reservationSn, @Param("userSn") long userSn);

	/** 본인(예약자) 예약 목록 — 최신순. */
	List<ReservationVO> selectMyReservations(@Param("userSn") long userSn);

	// ---- 확정(슬라이스③ — 재고 최종확정) ----

	/**
	 * 본인 예약 행 잠금(FOR UPDATE) — RESERVATION_SN + RESERVER_USER_SN 일치만(미삭제). 타 소유/없음 → null.
	 * 확정 흐름에서 상태전이/재고확정 판단에 필요한 컬럼(상태/객실/기간/박수/홀드/주문/금액/통화)까지 함께 로드한다.
	 */
	ReservationVO selectReservationForUpdate(@Param("reservationSn") long reservationSn, @Param("userSn") long userSn);

	/**
	 * 한 밤 재고 최종확정(오버부킹 최후 가드) — SOLD_QTY += qty. 마감 아님 AND 잔여(TOTAL-SOLD)≥qty 조건부 UPDATE.
	 * 갱신된 행 수 반환(0=용량부족/마감/누락 → 서비스가 매진 판정). CK_ROOM_CALENDAR_QTY 가 DB 최후 가드.
	 */
	int confirmNight(@Param("roomSn") long roomSn, @Param("stayDate") LocalDate stayDate,
			@Param("qty") int qty, @Param("mdfrSn") long mdfrSn);

	/**
	 * 상태 확정(RESERVED) — HOLD/PAYMENT_PENDING 에서만 전이(행 잠금 내 동시성 안전 조건). 갱신 행 수 반환.
	 */
	int updateStatusToReserved(@Param("reservationSn") long reservationSn, @Param("mdfrSn") long mdfrSn);
}
