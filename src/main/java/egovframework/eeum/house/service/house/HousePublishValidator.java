package egovframework.eeum.house.service.house;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import egovframework.eeum.account.service.partner.PartnerService;
import egovframework.eeum.account.web.dto.PartnerDetailResponse;
import egovframework.eeum.base.exception.BusinessException;
import egovframework.eeum.house.exception.HouseErrorCode;
import egovframework.eeum.house.exception.HouseNotPublishableException;
import egovframework.eeum.house.exception.PublishCondition;
import egovframework.eeum.house.service.calendar.dao.RoomCalendarMapper;
import egovframework.eeum.house.service.house.dao.HouseMapper;
import egovframework.eeum.house.service.house.dao.HousePayMethodMapper;
import egovframework.eeum.house.service.image.dao.HouseImageMapper;
import egovframework.eeum.house.service.image.dao.RoomImageMapper;
import egovframework.eeum.house.service.room.dao.RoomBeddingMapper;
import egovframework.eeum.house.service.room.dao.RoomMapper;
import egovframework.eeum.house.vo.HouseVO;
import egovframework.eeum.house.vo.RoomVO;
import lombok.RequiredArgsConstructor;

/**
 * 게시(발행) 게이트 — 고택을 ACTIVE 로 전이하기 전 필수조건을 한 곳에서 검증(판매자·운영자 경로 공용).
 *
 * <p>발행/검수 승인 경로({@link #validate}/{@link #evaluate})는 전체 9조건을 검증한다. ACTIVE 편집 가드
 * ({@link HousePublishGuard})는 {@link #snapshot} 의 <b>원시 조건 스냅샷</b>을 전/후 비교해 "비퇴행"만 강제한다.
 *
 * <p>원시 조건은 API 계약(9개 {@link PublishCondition})보다 잘게 쪼갠다 — 파트너 승인/계정, 사업자 conm/brno/rprsvNm/addr,
 * 고택 houseNm/zip/addr/houseDc, chkinTime/chkoutTime 를 각각 독립 키로 둔다(같은 카테고리 내 추가 훼손 감지).
 * 응답 {@code failedConditions} 는 각 원시 키의 롤업 카테고리로 환원해 계약을 유지한다. 두 경로 모두 아래
 * {@link #snapshotOwned} 단일 출처를 사용해 조건 정의가 어긋나지 않게 한다.
 */
@Component
@RequiredArgsConstructor
public class HousePublishValidator {

	private static final String Y = "Y";
	private static final String APRV_APPROVED = "APPROVED";
	private static final String STTS_ACTIVE = "ACTIVE";

	// 원시 조건키 — 하우스/파트너 레벨(잘게 분해). 객실 원시조건은 roomKey(roomSn, sub) 로 동적 생성.
	private static final String K_PARTNER_APPROVAL = "PARTNER_APPROVAL";
	private static final String K_PARTNER_ACCOUNT = "PARTNER_ACCOUNT";
	private static final String K_BIZ_CONM = "BIZ_CONM";
	private static final String K_BIZ_BRNO = "BIZ_BRNO";
	private static final String K_BIZ_RPRSVNM = "BIZ_RPRSVNM";
	private static final String K_BIZ_ADDR = "BIZ_ADDR";
	private static final String K_HOUSE_NM = "HOUSE_NM";
	private static final String K_HOUSE_ZIP = "HOUSE_ZIP";
	private static final String K_HOUSE_ADDR = "HOUSE_ADDR";
	private static final String K_HOUSE_DC = "HOUSE_DC";
	private static final String K_CHECKIN_TIME = "CHECKIN_TIME";
	private static final String K_CHECKOUT_TIME = "CHECKOUT_TIME";
	private static final String K_RESERVATION_CONFIRM_TYPE = "RESERVATION_CONFIRM_TYPE";
	private static final String K_CANCELLATION_POLICY = "CANCELLATION_POLICY";
	private static final String K_PAYMENT_METHOD = "PAYMENT_METHOD";
	private static final String K_REPRESENTATIVE_IMAGE = "REPRESENTATIVE_IMAGE";
	private static final String K_HAS_PUBLISHABLE_ROOM = "HAS_PUBLISHABLE_ROOM";

	private final HouseMapper houseMapper;
	private final HousePayMethodMapper housePayMethodMapper;
	private final HouseImageMapper houseImageMapper;
	private final RoomMapper roomMapper;
	private final RoomBeddingMapper roomBeddingMapper;
	private final RoomImageMapper roomImageMapper;
	private final RoomCalendarMapper roomCalendarMapper;
	private final PartnerService partnerService;

	// ===== 발행/검수 승인 경로(전체 9조건) — 동작·조건순서 보존 =====

	/**
	 * 게시 가능 여부 검증(발행 경로용) — 미충족 시 {@link HouseNotPublishableException}(409, failedConditions 포함) throw.
	 * 기존 상태변경(ACTIVE) 발행 흐름이 사용한다. {@link #evaluate} 에 위임 — 동작 보존.
	 */
	public void validate(long houseSn, long ownerPrtnrSn) {
		List<PublishCondition> failed = evaluate(houseSn, ownerPrtnrSn);
		if (!failed.isEmpty()) {
			throw new HouseNotPublishableException(failed);
		}
	}

	/**
	 * 9조건 미충족 목록 수집(read-only) — 빈 리스트 = 발행 가능. 발행(validate)·사전점검(publish-check) 공용.
	 * 소유권/존재 실패는 {@link HouseErrorCode#HOUSE_NOT_FOUND}(404) hard throw(발행준비 정보누출 차단). DB write 없음.
	 *
	 * <p>{@link #snapshotOwned} 원시 스냅샷에서 조건별 미충족을 <b>기존과 동일 순서</b>로 롤업한다
	 * (편집 가드와 단일 출처 공유). ※ 통신판매업신고번호는 게이트 조건 아님(추가 금지).
	 */
	public List<PublishCondition> evaluate(long houseSn, long ownerPrtnrSn) {
		HouseVO house = requireOwnedHouse(houseSn, ownerPrtnrSn);
		ReadinessSnapshot snap = snapshotOwned(house, ownerPrtnrSn, LocalDate.now());

		List<PublishCondition> failed = new ArrayList<>();
		if (!(snap.met(K_PARTNER_APPROVAL) && snap.met(K_PARTNER_ACCOUNT))) {
			failed.add(PublishCondition.PARTNER_NOT_APPROVED);
		}
		if (!(snap.met(K_BIZ_CONM) && snap.met(K_BIZ_BRNO) && snap.met(K_BIZ_RPRSVNM) && snap.met(K_BIZ_ADDR))) {
			failed.add(PublishCondition.PARTNER_BUSINESS_INFO_MISSING);
		}
		if (!(snap.met(K_HOUSE_NM) && snap.met(K_HOUSE_ZIP) && snap.met(K_HOUSE_ADDR) && snap.met(K_HOUSE_DC))) {
			failed.add(PublishCondition.HOUSE_BASIC_INFO_MISSING);
		}
		if (!(snap.met(K_CHECKIN_TIME) && snap.met(K_CHECKOUT_TIME))) {
			failed.add(PublishCondition.CHECKIN_CHECKOUT_TIME_MISSING);
		}
		if (!snap.met(K_RESERVATION_CONFIRM_TYPE)) {
			failed.add(PublishCondition.RESERVATION_CONFIRM_TYPE_MISSING);
		}
		if (!snap.met(K_CANCELLATION_POLICY)) {
			failed.add(PublishCondition.CANCELLATION_POLICY_MISSING);
		}
		if (!snap.met(K_PAYMENT_METHOD)) {
			failed.add(PublishCondition.PAYMENT_METHOD_MISSING);
		}
		if (!snap.met(K_REPRESENTATIVE_IMAGE)) {
			failed.add(PublishCondition.REPRESENTATIVE_IMAGE_MISSING);
		}
		if (!snap.met(K_HAS_PUBLISHABLE_ROOM)) {
			failed.add(PublishCondition.PUBLISHABLE_ROOM_MISSING);
		}
		return failed;
	}

	// ===== ACTIVE 편집 가드(비퇴행) — 원시 readiness 스냅샷 =====

	/**
	 * 발행 readiness 원시 스냅샷(read-only) — 편집 가드의 전/후 비퇴행 비교용. evaluate() 와 동일 매퍼·기준 사용.
	 *
	 * <p><b>fail-closed</b>: 고택 미존재/삭제(DEL_YN='Y')/소유권 불일치는 {@link HouseErrorCode#HOUSE_NOT_FOUND} throw
	 * (빈 스냅샷 금지 — 편집 후 고택 자체가 유실/탈취되면 롤백). 정상적인 객실 삭제로 동적 room key 가 사라지는 것은
	 * 여기서 throw 하지 않고(고택 조회 성공), 비퇴행 판정({@link ReadinessSnapshot})이 집계 키로 처리한다.
	 *
	 * @param today 미래 판매가능 캘린더 기준일 — 전/후 스냅샷이 동일 기준일을 쓰도록 호출 측이 고정 전달.
	 */
	public ReadinessSnapshot snapshot(long houseSn, long ownerPrtnrSn, LocalDate today) {
		// selectHouseDetail 은 DEL_YN='N' 필터 내장 → null=미존재/삭제. 소유권 불일치도 fail-closed.
		HouseVO house = houseMapper.selectHouseDetail(houseSn);
		if (house == null || house.getPrtnrSn() == null || !house.getPrtnrSn().equals(ownerPrtnrSn)) {
			throw new BusinessException(HouseErrorCode.HOUSE_NOT_FOUND);
		}
		return snapshotOwned(house, ownerPrtnrSn, today);
	}

	/**
	 * 소유확인된 고택의 원시 readiness 스냅샷 구성(단일 출처) — 파트너/사업자/고택기본을 잘게 분해한 키 + 객실별 6조건
	 * + 파생 HAS_PUBLISHABLE_ROOM. 조건 정의를 여기 한 곳에만 두어 발행 경로(evaluate)와 편집 가드(snapshot)가 어긋나지 않게 한다.
	 */
	private ReadinessSnapshot snapshotOwned(HouseVO house, long ownerPrtnrSn, LocalDate today) {
		long houseSn = house.getHouseSn();
		PartnerDetailResponse partner = partnerService.detail(ownerPrtnrSn);

		ReadinessSnapshot.Builder b = ReadinessSnapshot.builder(today);
		// 파트너 승인상태 / 계정상태 — 독립 키(둘 다 PARTNER_NOT_APPROVED 로 롤업)
		b.put(K_PARTNER_APPROVAL, APRV_APPROVED.equals(partner.aprvStts()), PublishCondition.PARTNER_NOT_APPROVED);
		b.put(K_PARTNER_ACCOUNT, STTS_ACTIVE.equals(partner.acntStts()), PublishCondition.PARTNER_NOT_APPROVED);
		// 사업자정보 conm/brno/rprsvNm/addr — 독립 키(PARTNER_BUSINESS_INFO_MISSING 롤업)
		b.put(K_BIZ_CONM, !isBlank(partner.conm()), PublishCondition.PARTNER_BUSINESS_INFO_MISSING);
		b.put(K_BIZ_BRNO, !isBlank(partner.brno()), PublishCondition.PARTNER_BUSINESS_INFO_MISSING);
		b.put(K_BIZ_RPRSVNM, !isBlank(partner.rprsvNm()), PublishCondition.PARTNER_BUSINESS_INFO_MISSING);
		b.put(K_BIZ_ADDR, !isBlank(partner.addr()), PublishCondition.PARTNER_BUSINESS_INFO_MISSING);
		// 고택 기본정보 houseNm/zip/addr/houseDc — 독립 키(HOUSE_BASIC_INFO_MISSING 롤업)
		b.put(K_HOUSE_NM, !isBlank(house.getHouseNm()), PublishCondition.HOUSE_BASIC_INFO_MISSING);
		b.put(K_HOUSE_ZIP, !isBlank(house.getZip()), PublishCondition.HOUSE_BASIC_INFO_MISSING);
		b.put(K_HOUSE_ADDR, !isBlank(house.getAddr()), PublishCondition.HOUSE_BASIC_INFO_MISSING);
		b.put(K_HOUSE_DC, !isBlank(house.getHouseDc()), PublishCondition.HOUSE_BASIC_INFO_MISSING);
		// 체크인/체크아웃 — 독립 키(CHECKIN_CHECKOUT_TIME_MISSING 롤업)
		b.put(K_CHECKIN_TIME, house.getChkinTime() != null, PublishCondition.CHECKIN_CHECKOUT_TIME_MISSING);
		b.put(K_CHECKOUT_TIME, house.getChkoutTime() != null, PublishCondition.CHECKIN_CHECKOUT_TIME_MISSING);

		b.put(K_RESERVATION_CONFIRM_TYPE, !isBlank(house.getRsvtCnfirmCd()),
				PublishCondition.RESERVATION_CONFIRM_TYPE_MISSING);
		b.put(K_CANCELLATION_POLICY, !isBlank(house.getCnclRfndPlcyCn()),
				PublishCondition.CANCELLATION_POLICY_MISSING);
		b.put(K_PAYMENT_METHOD, !housePayMethodMapper.selectActiveCodesByHouse(houseSn).isEmpty(),
				PublishCondition.PAYMENT_METHOD_MISSING);
		b.put(K_REPRESENTATIVE_IMAGE, houseImageMapper.countRepresentativeImagesByHouse(houseSn) >= 1,
				PublishCondition.REPRESENTATIVE_IMAGE_MISSING);

		// 객실별 6조건(ACTIVE/재고/욕실/침구/이미지/미래 판매캘린더)은 "완비 객실 존재" 파생값(HAS_PUBLISHABLE_ROOM)
		// 계산에만 쓰고, 스냅샷 키로는 저장하지 않는다. → 다중 객실에서 한 객실을 깨도 다른 게시가능 객실이 남으면
		// 고택은 여전히 발행가능. PUBLISHABLE_ROOM_MISSING 퇴행은 집계 키 true→false 로만 판정(객실별 키는 직접 퇴행 대상 아님).
		boolean anyPublishableRoom = false;
		for (RoomVO room : roomMapper.selectRoomsByHouse(houseSn)) {
			long rs = room.getRoomSn();
			boolean publishable = STTS_ACTIVE.equals(room.getRoomSttsCd())
					&& room.getRoomCnt() != null && room.getRoomCnt() >= 1
					&& room.getBathrmCnt() != null
					&& !roomBeddingMapper.selectActiveByRoom(rs).isEmpty()
					&& roomImageMapper.countActiveImagesByRoom(rs) >= 1
					&& roomCalendarMapper.countFutureSellableByRoom(rs, today) >= 1;
			if (publishable) {
				anyPublishableRoom = true;
			}
		}
		b.put(K_HAS_PUBLISHABLE_ROOM, anyPublishableRoom, PublishCondition.PUBLISHABLE_ROOM_MISSING);
		return b.build();
	}

	// ------------------------------------------------------------------

	private HouseVO requireOwnedHouse(long houseSn, long ownerPrtnrSn) {
		HouseVO house = houseMapper.selectHouseDetail(houseSn);
		if (house == null || Y.equals(house.getDelYn())
				|| house.getPrtnrSn() == null || !house.getPrtnrSn().equals(ownerPrtnrSn)) {
			throw new BusinessException(HouseErrorCode.HOUSE_NOT_FOUND);
		}
		return house;
	}

	private static boolean isBlank(String s) {
		return s == null || s.isBlank();
	}
}
