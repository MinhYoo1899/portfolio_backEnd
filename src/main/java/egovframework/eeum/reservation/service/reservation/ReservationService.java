package egovframework.eeum.reservation.service.reservation;

import java.util.List;

import egovframework.eeum.auth.vo.AuthUser;
import egovframework.eeum.reservation.web.dto.ReservationCreateRequest;
import egovframework.eeum.reservation.web.dto.ReservationResponse;

/**
 * 예약 서비스(회원) — 집합체 생성(HOLD) + 본인 예약 조회.
 *
 * <p>소유권: 예약자=토큰 USER_SN(본문 미신뢰), 판매자=roomSn 서버 해석. 재고 최종확정(SOLD_QTY)은 후속 슬라이스.
 */
public interface ReservationService {

	/**
	 * 예약 집합체 생성(HOLD) — 검증 → 소유권/재고 해석 → 소프트홀드 → 헤더/항목/일자/게스트/동의 insert.
	 *
	 * @param request              생성 요청(객실/기간/게스트/동의)
	 * @param idempotencyKeyHeader 멱등키 원시 헤더(없으면 null) — 저장은 sha256hex 만, 원시값 미저장
	 * @param member               인증 회원(예약자 — USER_SN 만 신뢰)
	 */
	ReservationResponse create(ReservationCreateRequest request, String idempotencyKeyHeader, AuthUser member);

	/** 본인 예약 상세(RESERVER_USER_SN==토큰). 타 소유/없음 → 404. */
	ReservationResponse detail(long reservationSn, AuthUser member);

	/** 본인 예약 목록(최신순). */
	List<ReservationResponse> listMine(AuthUser member);

	/**
	 * 예약 확정(HOLD/PAYMENT_PENDING → RESERVED) — 결제 승인 → 숙박일별 재고 최종확정(SOLD_QTY 조건부 UPDATE) → 상태 확정 → 홀드 해제.
	 *
	 * <p>멱등: 이미 RESERVED 면 결제·재고변경 없이 즉시 반환(재시도/웹훅 이중확정 시 재고 이중차감 방지).
	 * 오버부킹 최후 가드는 밤별 조건부 UPDATE(잔여≥qty) — 한 밤이라도 실패하면 전체 롤백(부분차감 없음).
	 *
	 * @param reservationSn 예약일련번호
	 * @param member        인증 회원(예약자 — 본인 예약만)
	 */
	ReservationResponse confirm(long reservationSn, AuthUser member);
}
