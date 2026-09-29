package egovframework.eeum.reservation.service.hold;

import java.time.LocalDate;
import java.util.List;

/**
 * 예약 소프트홀드 저장소(Redis ZSET, advisory). 재고 최종확정은 슬라이스③ DB 조건부 UPDATE 가 담당하고,
 * 여기서는 "결제 전 임시점유"만 만료(TTL) 기반으로 관리한다(오버부킹은 DB CHECK 가 최후 가드).
 *
 * <p>다박(multi-night)은 전-박 원자 획득(all-or-nothing): 한 밤이라도 재고가 부족하면 <b>어느 밤에도</b> 멤버를 남기지 않는다.
 * 인프라 장애는 {@link ReservationHoldException} 으로 던져 서비스가 fail-open(DB-only) 하도록 한다.
 */
public interface ReservationHoldStore {

	/**
	 * 한 밤의 재고 스냅샷 — {@code totalQty}(판매가능 총수량), {@code soldQty}(DB 확정 판매수량). 남은 = total-sold-held(Redis).
	 */
	record HoldNight(LocalDate stayDate, int totalQty, int soldQty) {
	}

	/** 획득 결과 — {@code acquired}=전-박 획득 성공, {@code soldOutDate}=실패 시 최초 매진 숙박일(성공 시 null). */
	record HoldResult(boolean acquired, LocalDate soldOutDate) {
	}

	/**
	 * 전-박 원자 획득 — 모든 밤에 {@code qty} 만큼 임시점유(TTL=window+buffer)를 시도한다.
	 *
	 * @param holdId        이 획득 시도의 고유 식별자(UUID) — 멤버 접두. release 대상 지정용(재사용 금지)
	 * @param roomSn        객실일련번호(키 구성)
	 * @param nights        숙박일별 재고 스냅샷(체크인~체크아웃 반개구간)
	 * @param qty           밤당 점유 수량(객실 수)
	 * @param windowSeconds 홀드 유효창(초) — expireAt/TTL 계산 기준
	 * @return 획득 성공 여부 + 실패 숙박일
	 * @throws ReservationHoldException Redis 인프라 장애(연결/타임아웃/스크립트) — 서비스는 fail-open
	 */
	HoldResult acquire(String holdId, long roomSn, List<HoldNight> nights, int qty, long windowSeconds);

	/**
	 * 홀드 해제 — 지정 {@code holdId} 의 멤버만 각 숙박일 키에서 제거(멱등, 없는 멤버는 no-op). 타 holdId 멤버는 건드리지 않는다.
	 *
	 * @param holdId     해제 대상 획득 식별자
	 * @param roomSn     객실일련번호(키 구성)
	 * @param stayDates  해제할 숙박일 목록
	 * @param qty        획득 시 사용한 밤당 수량(멤버 {@code holdId#1..qty} 재구성용)
	 */
	void release(String holdId, long roomSn, List<LocalDate> stayDates, int qty);

	/** 홀드 유효창(초) — 서비스가 acquire 인자 + HOLD_EXPRY_DT 계산에 사용하는 단일 진실원. */
	long windowSeconds();
}
