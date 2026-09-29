package egovframework.eeum.reservation.service.reservation.impl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import egovframework.eeum.auth.vo.AuthUser;
import egovframework.eeum.base.audit.Audit;
import egovframework.eeum.base.exception.BusinessException;
import egovframework.eeum.reservation.exception.ReservationErrorCode;
import egovframework.eeum.reservation.service.hold.ReservationHoldException;
import egovframework.eeum.reservation.service.hold.ReservationHoldStore;
import egovframework.eeum.reservation.service.hold.ReservationHoldStore.HoldNight;
import egovframework.eeum.reservation.service.hold.ReservationHoldStore.HoldResult;
import egovframework.eeum.reservation.service.payment.PaymentApproval;
import egovframework.eeum.reservation.service.payment.PaymentGateway;
import egovframework.eeum.reservation.service.reservation.ReservationService;
import egovframework.eeum.reservation.service.reservation.dao.ReservationMapper;
import egovframework.eeum.reservation.vo.ReservationAgreeVO;
import egovframework.eeum.reservation.vo.ReservationCalendarNightVO;
import egovframework.eeum.reservation.vo.ReservationGuestVO;
import egovframework.eeum.reservation.vo.ReservationItemNightVO;
import egovframework.eeum.reservation.vo.ReservationItemVO;
import egovframework.eeum.reservation.vo.ReservationRoomOwnershipVO;
import egovframework.eeum.reservation.vo.ReservationVO;
import egovframework.eeum.reservation.web.dto.AgreementRequest;
import egovframework.eeum.reservation.web.dto.GuestRequest;
import egovframework.eeum.reservation.web.dto.ReservationCreateRequest;
import egovframework.eeum.reservation.web.dto.ReservationResponse;
import egovframework.eeum.notification.service.notification.NotificationEmitter;
import egovframework.eeum.notification.service.notification.NotificationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 예약 서비스 구현(회원) — 집합체 생성(HOLD) + 본인 예약 조회.
 *
 * <p>흐름: 기간/게스트 검증 → 소유권·판매성 해석(roomSn) → 멱등 사전점검(sha256hex) → 숙박일별 캘린더 검증
 *   → 소프트홀드(fail-open) → 금액 스냅샷 → 집합체 insert(ORDER_NO 재시도 + 홀드 안전 try/catch).
 *
 * <p>IDOR 방어: 예약자=토큰 USER_SN, 판매자=서버 해석(본문 미신뢰). 재고 최종확정(SOLD_QTY)은 이 슬라이스에서 하지 않는다(HOLD 만).
 * 예약 도메인 자체 매퍼로 ROOM/HOUSE/ROOM_CALENDAR/TRMS_MST 를 직접 읽는다(타 도메인 매퍼 미호출).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReservationServiceImpl implements ReservationService {

	private static final String Y = "Y";
	private static final String STTS_ACTIVE = "ACTIVE";
	private static final String STTS_HOLD = "HOLD";
	private static final String STTS_PAYMENT_PENDING = "PAYMENT_PENDING";
	private static final String STTS_RESERVED = "RESERVED";
	private static final String CRNC_KRW = "KRW";
	private static final String GUEST_RESERVER = "RESERVER";
	/** 이 슬라이스의 밤당 점유 수량 — 1 고정(다객실 예약은 후속). */
	private static final int QTY = 1;

	/** ORDER_NO 채번 충돌 재시도 상한(전역 UNIQUE 경합 대비 — 사실상 1회로 성공). */
	private static final int MAX_ORDER_NO_ATTEMPTS = 3;
	/** DuplicateKeyException 제약 구분자 — 근본 원인 메시지에 포함되는 UNIQUE 키 이름. */
	private static final String UK_IDEMPOTENCY = "UK_RESERVATION_IDEMPOTENCY";
	private static final String UK_ORDER_NO = "UK_RESERVATION_ORDER_NO";

	/** 숙박일/과거판정 기준 타임존(KST LocalDate — timezone-standard). */
	private static final ZoneId KST = ZoneId.of("Asia/Seoul");
	private static final String ORDER_NO_ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
	private static final int ORDER_NO_RANDOM_LEN = 4;
	private static final SecureRandom ORDER_NO_RANDOM = new SecureRandom();

	private final ReservationMapper reservationMapper;
	private final ReservationHoldStore holdStore;
	private final PaymentGateway paymentGateway;
	private final NotificationEmitter notificationEmitter;

	@Override
	@Transactional
	@Audit(actn = "RESERVATION_CREATE", trgtType = "ROOM", trgtId = "#request.roomSn()")
	public ReservationResponse create(ReservationCreateRequest request, String idempotencyKeyHeader, AuthUser member) {
		if (member == null) {
			throw BusinessException.unauthorized();
		}
		long userSn = member.userSn();
		long roomSn = request.roomSn();

		// 1. 기간/게스트 검증
		LocalDate checkIn = request.checkInDate();
		LocalDate checkOut = request.checkOutDate();
		validateRange(checkIn, checkOut);
		int ngtCnt = (int) ChronoUnit.DAYS.between(checkIn, checkOut);
		validateGuests(request.guests());

		// 2. 소유권/판매성 해석(roomSn → 판매자/고택, 객실·고택 ACTIVE). 없음/비활성 = 판매 불가
		ReservationRoomOwnershipVO ownership = reservationMapper.selectRoomOwnership(roomSn);
		if (ownership == null || !STTS_ACTIVE.equals(ownership.getRoomSttsCd())
				|| !STTS_ACTIVE.equals(ownership.getHouseSttsCd())) {
			throw new BusinessException(ReservationErrorCode.RESERVATION_ROOM_UNAVAILABLE);
		}

		// 3. 멱등 사전점검 — 원시 헤더는 저장/조회하지 않고 sha256hex 만 사용. 기존 있으면 홀드 획득 전 중단
		String idemHash = (idempotencyKeyHeader == null || idempotencyKeyHeader.isBlank())
				? null : sha256Hex(idempotencyKeyHeader);
		if (idemHash != null && reservationMapper.selectExistingByIdemHash(userSn, idemHash) != null) {
			throw new BusinessException(ReservationErrorCode.RESERVATION_DUPLICATE);
		}

		// 4. 숙박일별 캘린더 — 모든 밤 행 존재(건수 일치) + 마감(CLOSED) 아님 + 단가 양수. 하나라도 누락/마감/무가격 = 판매 불가
		//    (PRICE_AMT<=0 밤 차단 — 공개 가용검색·발행게이트의 판매가능 기준과 계약 정렬. 0원 밤이 스냅샷되면 무상숙박이 확정됨)
		List<ReservationCalendarNightVO> nights = reservationMapper.selectCalendarNights(roomSn, checkIn, checkOut);
		if (nights.size() != ngtCnt) {
			throw new BusinessException(ReservationErrorCode.RESERVATION_ROOM_UNAVAILABLE);
		}
		for (ReservationCalendarNightVO night : nights) {
			if (Y.equals(night.getClosedYn()) || night.getPriceAmt() == null || night.getPriceAmt() <= 0) {
				throw new BusinessException(ReservationErrorCode.RESERVATION_ROOM_UNAVAILABLE);
			}
		}
		List<LocalDate> stayDates = nights.stream().map(ReservationCalendarNightVO::getStayDate).toList();

		// 5. 소프트홀드(fail-open) — 인프라 장애면 홀드 없이 DB-only 진행. 매진이면 SOLD_OUT
		long windowSeconds = holdStore.windowSeconds();
		String holdIdForDb = acquireHoldOrFailOpen(roomSn, nights);

		// 6. 금액 스냅샷(밤단가 합계, qty=1) + 홀드 만료(양 경로 공통 — 획득/fail-open 모두 설정)
		int itemAmt = nights.stream().mapToInt(ReservationCalendarNightVO::getPriceAmt).sum();
		LocalDateTime holdExpry = LocalDateTime.now(ZoneOffset.UTC).plusSeconds(windowSeconds);

		// 7~8. 집합체 insert — 홀드 안전 try/catch(고스트홀드 방지) + ORDER_NO 채번 재시도
		ReservationVO header = ReservationVO.builder()
				.idempotencyKey(idemHash)
				.reservationSttsCd(STTS_HOLD)
				.prtnrSn(ownership.getPrtnrSn())
				.houseSn(ownership.getHouseSn())
				.roomSn(roomSn)
				.reserverUserSn(userSn)
				.checkInDate(checkIn)
				.checkOutDate(checkOut)
				.ngtCnt(ngtCnt)
				.guestCnt(request.guests().size())
				.totalAmt(itemAmt)
				.crncCd(CRNC_KRW)
				.holdExpryDt(holdExpry)
				.holdId(holdIdForDb)
				.rgtrSn(userSn)
				.build();
		try {
			insertHeaderWithOrderNoRetry(header);
			insertChildren(header, ownership, nights, request, userSn);
		} catch (RuntimeException e) {
			// insert 도중 어떤 예외든 — 이미 획득한 홀드가 있으면 해제(고스트홀드 방지) 후 원 예외 전파
			if (holdIdForDb != null) {
				releaseHoldQuietly(holdIdForDb, roomSn, stayDates);
			}
			throw e;
		}
		return ReservationResponse.from(header);
	}

	@Override
	@Transactional(readOnly = true)
	public ReservationResponse detail(long reservationSn, AuthUser member) {
		if (member == null) {
			throw BusinessException.unauthorized();
		}
		ReservationVO vo = reservationMapper.selectReservationOwned(reservationSn, member.userSn());
		if (vo == null) {
			throw new BusinessException(ReservationErrorCode.RESERVATION_NOT_FOUND);
		}
		return ReservationResponse.from(vo);
	}

	@Override
	@Transactional(readOnly = true)
	public List<ReservationResponse> listMine(AuthUser member) {
		if (member == null) {
			throw BusinessException.unauthorized();
		}
		return reservationMapper.selectMyReservations(member.userSn()).stream()
				.map(ReservationResponse::from).toList();
	}

	@Override
	@Transactional
	@Audit(actn = "RESERVATION_CONFIRM", trgtType = "RESERVATION", trgtId = "#reservationSn")
	public ReservationResponse confirm(long reservationSn, AuthUser member) {
		if (member == null) {
			throw BusinessException.unauthorized();
		}
		long userSn = member.userSn();

		// 1. 본인 예약 행 잠금(FOR UPDATE) — 소유권 WHERE 로 타 소유/없음 차단
		ReservationVO vo = reservationMapper.selectReservationForUpdate(reservationSn, userSn);
		if (vo == null) {
			throw new BusinessException(ReservationErrorCode.RESERVATION_NOT_FOUND);
		}

		// 2. 멱등 no-op — 이미 RESERVED 면 결제·재고변경 없이 즉시 반환(재시도/웹훅 이중확정 시 SOLD_QTY 이중차감 방지)
		if (STTS_RESERVED.equals(vo.getReservationSttsCd())) {
			return ReservationResponse.from(vo);
		}

		// 3. 상태머신 — 확정은 HOLD/PAYMENT_PENDING 에서만(CANCELLED/EXPIRED/USED → 불법 전이)
		validateConfirmTransition(vo.getReservationSttsCd());

		// 4. 결제 승인 경계 — 스텁은 즉시 승인(PAYMENT 행 영속화·웹훅 멱등은 실 Toss 슬라이스). 재고확정 전에 호출
		PaymentApproval approval = paymentGateway.approve(vo.getOrderNo(), vo.getTotalAmt(), vo.getCrncCd());
		if (!approval.approved()) {
			// 스텁은 항상 승인 — 실 PG 도입 시 실패 처리(결제실패 상태전이 등)는 후속 슬라이스에서 구체화
			throw new BusinessException(ReservationErrorCode.RESERVATION_INVALID_STATE);
		}

		// 5. 밤별 재고 최종확정(오버부킹 최후 가드) — STAY_DATE 오름차순(데드락 방지)으로 조건부 UPDATE.
		//    합계 affected != ngtCnt 면 매진 → 트랜잭션 롤백(모든 밤 원복, 부분차감 없음). CK_ROOM_CALENDAR_QTY 가 DB 최후 가드
		long roomSn = vo.getRoomSn();
		int ngtCnt = vo.getNgtCnt();
		List<LocalDate> stayDates = new ArrayList<>(ngtCnt);
		int affected = 0;
		for (LocalDate stayDate = vo.getCheckInDate(); stayDate.isBefore(vo.getCheckOutDate());
				stayDate = stayDate.plusDays(1)) {
			stayDates.add(stayDate);
			affected += reservationMapper.confirmNight(roomSn, stayDate, QTY, userSn);
		}
		if (affected != ngtCnt) {
			throw new BusinessException(ReservationErrorCode.RESERVATION_SOLD_OUT);
		}

		// 6. 상태 확정(RESERVED) — HOLD/PAYMENT_PENDING 조건부(행 잠금 내 동시성 안전).
		//    갱신 행 수 != 1 이면 방어적 예외(FOR UPDATE 잠금 + 상태가드로 도달 불가하나, 확정 1건을 단언 — 교차검토 권고).
		int statusUpdated = reservationMapper.updateStatusToReserved(reservationSn, userSn);
		if (statusUpdated != 1) {
			throw new BusinessException(ReservationErrorCode.RESERVATION_INVALID_STATE);
		}
		vo.setReservationSttsCd(STTS_RESERVED);
		// 확정(RESERVED) 성공 후 운영자 알림 — HOLD/멱등 no-op 경로(위 early return)는 적재 안 함(확정만).
		notificationEmitter.emit(userSn, "RESERVATION:" + reservationSn, NotificationType.RESERVATION,
				"새 예약 접수", null, String.valueOf(reservationSn));

		// 7. 홀드 해제 — DB 확정 후. 활성 트랜잭션이면 커밋 후 해제(롤백 시 홀드 유지), 아니면(단위테스트) 즉시 해제
		String holdId = vo.getHoldId();
		if (holdId != null) {
			if (TransactionSynchronizationManager.isSynchronizationActive()) {
				TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
					@Override
					public void afterCommit() {
						releaseHoldQuietly(holdId, roomSn, stayDates);
					}
				});
			} else {
				releaseHoldQuietly(holdId, roomSn, stayDates);
			}
		}

		// 8. 확정(RESERVED) 응답
		return ReservationResponse.from(vo);
	}

	// ------------------------------------------------------------------

	/**
	 * 확정 상태전이 검증 — RESERVED 전이는 HOLD/PAYMENT_PENDING 에서만 허용(아니면 INVALID_STATE).
	 * ({@link egovframework.eeum.account.service.member.impl.MemberServiceImpl} 의 validateTransition 스타일)
	 */
	private static void validateConfirmTransition(String from) {
		boolean allowed = STTS_HOLD.equals(from) || STTS_PAYMENT_PENDING.equals(from);
		if (!allowed) {
			throw new BusinessException(ReservationErrorCode.RESERVATION_INVALID_STATE);
		}
	}

	/**
	 * 소프트홀드 획득 — 성공 시 holdId, 매진 시 SOLD_OUT, 인프라 장애 시 fail-open(null 반환, DB-only).
	 * 매진(acquired=false)은 Redis 에 아무 멤버도 남기지 않으므로 해제 불요.
	 */
	private String acquireHoldOrFailOpen(long roomSn, List<ReservationCalendarNightVO> nights) {
		String holdId = UUID.randomUUID().toString();
		try {
			List<HoldNight> holdNights = nights.stream()
					.map(n -> new HoldNight(n.getStayDate(), n.getTotalQty(), n.getSoldQty()))
					.toList();
			HoldResult result = holdStore.acquire(holdId, roomSn, holdNights, QTY, holdStore.windowSeconds());
			if (!result.acquired()) {
				throw new BusinessException(ReservationErrorCode.RESERVATION_SOLD_OUT);
			}
			return holdId;
		} catch (ReservationHoldException e) {
			// 인프라 장애 → fail-open(NEVER fail-closed). 홀드 없이 진행하고 DB 가 최후 가드
			log.warn("[RESV_HOLD] fail-open — DB-only (roomSn={})", roomSn, e);
			return null;
		}
	}

	/**
	 * 예약 헤더 insert(ORDER_NO 채번 재시도). DuplicateKeyException 은 제약 이름으로 구분:
	 * IDEMPOTENCY 위반 → RESERVATION_DUPLICATE(재시도 금지, 동시요청 백스톱), ORDER_NO 위반 → 채번 후 재시도(최대 3회),
	 * 그 외 UNIQUE → 블랭킷 재시도 금지(그대로 전파).
	 */
	private void insertHeaderWithOrderNoRetry(ReservationVO header) {
		for (int attempt = 1; attempt <= MAX_ORDER_NO_ATTEMPTS; attempt++) {
			header.setOrderNo(generateOrderNo());
			try {
				reservationMapper.insertReservation(header);
				return;
			} catch (DuplicateKeyException e) {
				String cause = causeChainUpper(e);
				if (cause.contains(UK_IDEMPOTENCY)) {
					throw new BusinessException(ReservationErrorCode.RESERVATION_DUPLICATE,
							ReservationErrorCode.RESERVATION_DUPLICATE.defaultMessage(), e);
				}
				if (!cause.contains(UK_ORDER_NO)) {
					throw e; // 알 수 없는 UNIQUE 위반 — 블랭킷 재시도 금지
				}
				// ORDER_NO 충돌 — 다음 시도에서 채번 재생성(루프 지속)
			}
		}
		throw new BusinessException(ReservationErrorCode.RESERVATION_ORDER_NO_CONFLICT);
	}

	/** 자식 행 insert — 항목/일자별단가/게스트(전화 암호화)/동의(약관버전 서버해석). 헤더 정착 후 실행. */
	private void insertChildren(ReservationVO header, ReservationRoomOwnershipVO ownership,
			List<ReservationCalendarNightVO> nights, ReservationCreateRequest request, long userSn) {
		long reservationSn = header.getReservationSn();

		// 항목(1객실) — 객실명/소계 스냅샷
		ReservationItemVO item = ReservationItemVO.builder()
				.reservationSn(reservationSn)
				.roomSn(header.getRoomSn())
				.roomNmSnap(ownership.getRoomNm())
				.itemAmt(header.getTotalAmt())
				.roomQty(QTY)
				.rgtrSn(userSn)
				.build();
		reservationMapper.insertItem(item);
		long itemSn = item.getReservationItemSn();

		// 일자별 단가 스냅샷
		for (ReservationCalendarNightVO night : nights) {
			reservationMapper.insertItemNight(ReservationItemNightVO.builder()
					.reservationItemSn(itemSn)
					.stayDate(night.getStayDate())
					.nightPriceAmt(night.getPriceAmt())
					.nightQty(QTY)
					.rgtrSn(userSn)
					.build());
		}

		// 게스트(PII 분리 — 전화 암호화 TypeHandler 는 매퍼가 바인딩)
		for (GuestRequest guest : request.guests()) {
			reservationMapper.insertGuest(ReservationGuestVO.builder()
					.reservationSn(reservationSn)
					.guestTypeCd(guest.guestType())
					.guestNm(guest.guestNm())
					.guestMblTelno(guest.guestMblTelno())
					.rgtrSn(userSn)
					.build());
		}

		// 동의 스냅샷(선택) — 약관버전은 서버가 TRMS_MST 에서 해석(클라이언트 값 미신뢰)
		List<AgreementRequest> agreements = request.agreements();
		if (agreements != null) {
			LocalDateTime agreDt = LocalDateTime.now(ZoneOffset.UTC);
			for (AgreementRequest agreement : agreements) {
				String verNo = (agreement.trmsSn() == null)
						? null : reservationMapper.selectTermsVersion(agreement.trmsSn());
				reservationMapper.insertAgre(ReservationAgreeVO.builder()
						.reservationSn(reservationSn)
						.trmsSn(agreement.trmsSn())
						.trmsVerNo(verNo)
						.agrePrpsCd(agreement.agrePrpsCd())
						.agreItemCn(agreement.agreItemCn())
						.agreDt(agreDt)
						.rgtrSn(userSn)
						.build());
			}
		}
	}

	/** 홀드 해제(best-effort) — 해제 자체 실패는 원 예외를 가리지 않는다(경고만, TTL 로 자연 회수). */
	private void releaseHoldQuietly(String holdId, long roomSn, List<LocalDate> stayDates) {
		try {
			holdStore.release(holdId, roomSn, stayDates, QTY);
		} catch (RuntimeException e) {
			log.warn("[RESV_HOLD] release 실패 — 홀드는 TTL 로 회수 (roomSn={}, holdId={})", roomSn, holdId, e);
		}
	}

	/** 기간 검증 — 체크아웃 > 체크인 AND 체크인 >= 오늘(KST). 아니면 INVALID_RANGE. */
	private static void validateRange(LocalDate checkIn, LocalDate checkOut) {
		if (checkIn == null || checkOut == null || !checkOut.isAfter(checkIn) || checkIn.isBefore(LocalDate.now(KST))) {
			throw new BusinessException(ReservationErrorCode.RESERVATION_INVALID_RANGE);
		}
	}

	/** 게스트 검증 — 예약자(RESERVER) 정확히 1명. 아니면 GUEST_INVALID. */
	private static void validateGuests(List<GuestRequest> guests) {
		long reserverCount = guests.stream().filter(g -> GUEST_RESERVER.equals(g.guestType())).count();
		if (reserverCount != 1) {
			throw new BusinessException(ReservationErrorCode.RESERVATION_GUEST_INVALID);
		}
	}

	/** 서버측 주문번호 채번 — "R" + base36(epochMillis) + 4자 랜덤(전역 UNIQUE). */
	private static String generateOrderNo() {
		String time = Long.toString(System.currentTimeMillis(), 36).toUpperCase(Locale.ROOT);
		StringBuilder rand = new StringBuilder(ORDER_NO_RANDOM_LEN);
		for (int i = 0; i < ORDER_NO_RANDOM_LEN; i++) {
			rand.append(ORDER_NO_ALPHABET.charAt(ORDER_NO_RANDOM.nextInt(ORDER_NO_ALPHABET.length())));
		}
		return "R" + time + rand;
	}

	/** 원인 체인의 메시지를 이어 대문자화 — 제약 이름 매칭용(DB 가 키 이름 대소문자를 달리 보고해도 안전). */
	private static String causeChainUpper(Throwable e) {
		StringBuilder sb = new StringBuilder();
		for (Throwable t = e; t != null && sb.length() < 4000; t = t.getCause()) {
			if (t.getMessage() != null) {
				sb.append(t.getMessage()).append(' ');
			}
		}
		return sb.toString().toUpperCase(Locale.ROOT);
	}

	/**
	 * 멱등키 SHA-256 hex — 원시 Idempotency-Key 헤더는 DB 에 저장하지 않고 이 해시만 IDEMPOTENCY_KEY 로 보관한다.
	 * (idempotency 패키지의 package-private 헬퍼에 의존하지 않는 독립 구현 — 테스트에서도 동일 기대값 검증에 사용.)
	 */
	public static String sha256Hex(String value) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다.", e);
		}
	}
}
