package egovframework.eeum.house.service.house.impl;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import egovframework.eeum.account.service.partner.PartnerService;
import egovframework.eeum.account.web.dto.PartnerDetailResponse;
import egovframework.eeum.auth.vo.AuthUser;
import egovframework.eeum.base.audit.Audit;
import egovframework.eeum.base.audit.AuditRecorder;
import egovframework.eeum.base.exception.BusinessException;
import egovframework.eeum.base.response.PageResponse;
import egovframework.eeum.code.service.code.CodeService;
import egovframework.eeum.code.web.dto.CodeItemResponse;
import egovframework.eeum.house.exception.HouseErrorCode;
import egovframework.eeum.house.service.house.HouseProxyOwnerResolver;
import egovframework.eeum.house.service.house.HousePublishGuard;
import egovframework.eeum.house.service.house.HousePublishValidator;
import egovframework.eeum.house.service.house.HouseReviewGuard;
import egovframework.eeum.house.service.house.ReadinessSnapshot;
import egovframework.eeum.house.service.house.OperatorHouseService;
import egovframework.eeum.house.service.house.OperatorHouseStatus;
import egovframework.eeum.house.service.house.dao.HouseDrawerMapper;
import egovframework.eeum.house.service.house.dao.HouseMapper;
import egovframework.eeum.house.service.house.dao.HousePayMethodMapper;
import egovframework.eeum.house.service.room.dao.RoomMapper;
import egovframework.eeum.house.vo.DrawerPriceBucketVO;
import egovframework.eeum.house.vo.DrawerRoomAmenityVO;
import egovframework.eeum.house.vo.DrawerRoomBeddingVO;
import egovframework.eeum.house.vo.DrawerRoomImageVO;
import egovframework.eeum.house.vo.DrawerRoomVO;
import egovframework.eeum.house.vo.HouseDrawerVO;
import egovframework.eeum.house.vo.HousePayMethodVO;
import egovframework.eeum.house.vo.HouseReviewHstryVO;
import egovframework.eeum.house.vo.HouseVO;
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
import egovframework.eeum.region.service.region.RegionService;
import egovframework.eeum.region.vo.RegionVO;
import egovframework.eeum.notification.service.notification.NotificationEmitter;
import egovframework.eeum.notification.service.notification.NotificationType;
import lombok.RequiredArgsConstructor;

/**
 * 운영자 상품(고택) 서비스 구현 — 조회는 소유권 무관, 임시조치는 상태머신(SUSPENDED) + 사유 감사기록.
 *
 * <p>대행(HOUSE_PROXY_MNG) 수정/상태변경/정책/결제수단은 owner PRTNR_SN 을 {@link HouseProxyOwnerResolver}로
 * 서버 해석(요청 body 불신 — IDOR 차단)하고, 매퍼의 {@code WHERE PRTNR_SN=?} 이중가드를 판매자 경로와 동일하게 탄다.
 */
@Service
@RequiredArgsConstructor
public class OperatorHouseServiceImpl implements OperatorHouseService {

	private static final String Y = "Y";
	private static final String STTS_DRAFT = "DRAFT";
	private static final String STTS_REVIEW_REQUESTED = "REVIEW_REQUESTED";
	private static final String STTS_ACTIVE = "ACTIVE";
	private static final String STTS_HIDDEN = "HIDDEN";
	private static final String STTS_SUSPENDED = "SUSPENDED";
	private static final String APRV_APPROVED = "APPROVED";
	private static final String PAY_MTHD_GROUP = "PAY_MTHD";
	private static final String RSVT_CNFIRM_GROUP = "RSVT_CNFIRM";
	private static final Set<String> CLOSED_WEEKDAYS = Set.of("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN");
	private static final ZoneId KST = ZoneId.of("Asia/Seoul");
	private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");
	private static final int DRAWER_DEFAULT_DAYS = 90;
	private static final int DRAWER_MAX_SPAN_DAYS = 92;
	private static final String FILE_URL_PREFIX = OperatorHouseDetailDrawerResponse.FILE_URL_PREFIX;

	private final HouseMapper houseMapper;
	private final RoomMapper roomMapper;
	private final AuditRecorder auditRecorder;
	private final PartnerService partnerService;
	private final HouseProxyOwnerResolver proxyResolver;
	private final HousePayMethodMapper housePayMethodMapper;
	private final CodeService codeService;
	private final HousePublishGuard publishGuard;
	private final HousePublishValidator publishValidator;
	private final RegionService regionService;
	private final HouseDrawerMapper houseDrawerMapper;
	private final HouseReviewGuard reviewGuard;
	private final NotificationEmitter notificationEmitter;

	@Override
	@Transactional
	public HouseDetailResponse insert(OperatorHouseInsertRequest request, AuthUser operator) {
		AuthUser op = requireUser(operator);
		// 대상 파트너 검증 — 승인된 파트너만 대행 등록 대상(미존재면 detail 에서 404 PARTNER_NOT_FOUND)
		PartnerDetailResponse partner = partnerService.detail(request.prtnrSn());
		// 승인(APPROVED) + 계정 활성(ACTIVE) 둘 다 — 승인 후 정지/탈퇴된 파트너 명의 대행 차단(파트너 본인 경로와 동일, codex MUST).
		if (!APRV_APPROVED.equals(partner.aprvStts()) || !STTS_ACTIVE.equals(partner.acntStts())) {
			auditRecorder.recordFail(
					String.format("HOUSE_PROXY_INSERT_DENIED {\"prtnrSn\":%d,\"aprv\":\"%s\",\"acnt\":\"%s\"}",
							request.prtnrSn(), jsonSafe(partner.aprvStts()), jsonSafe(partner.acntStts())),
					"PARTNER", String.valueOf(request.prtnrSn()));
			throw BusinessException.conflict("승인된 활성 파트너만 대행 등록할 수 있습니다.");
		}
		// 소유자=선택 파트너(PRTNR_SN), 행위자=운영자(RGTR_SN). DRAFT 로 생성.
		validateRegion(request.sidoRegionCd(), request.sigunguRegionCd());
		HouseVO vo = HouseVO.builder()
				.prtnrSn(request.prtnrSn())
				.houseNm(request.houseNm())
				.zip(request.zip())
				.addr(request.addr())
				.daddr(request.daddr())
				.houseDc(request.houseDc())
				.sidoRegionCd(request.sidoRegionCd())
				.sigunguRegionCd(request.sigunguRegionCd())
				.rgtrSn(op.userSn())
				.build();
		try {
			houseMapper.insertHouse(vo);
		} catch (DuplicateKeyException e) {
			throw new BusinessException(HouseErrorCode.HOUSE_NAME_DUPLICATE,
					HouseErrorCode.HOUSE_NAME_DUPLICATE.defaultMessage(), e);
		}
		auditRecorder.recordSuccess(
				String.format("HOUSE_PROXY_INSERT {\"houseSn\":%d,\"prtnrSn\":%d}", vo.getHouseSn(), request.prtnrSn()),
				"HOUSE", String.valueOf(vo.getHouseSn()));
		return detail(vo.getHouseSn());
	}

	@Override
	@Transactional(readOnly = true)
	public PageResponse<OperatorHouseListItemResponse> list(OperatorHouseSearchRequest request) {
		String status = trimToNull(request.status());
		String keyword = escapeLike(trimToNull(request.keyword()));
		String sidoRegionCd = trimToNull(request.sidoRegionCd());
		String sigunguRegionCd = trimToNull(request.sigunguRegionCd());
		int page = request.pageOrDefault();
		int pageSize = request.pageSizeOrDefault();
		long total = houseMapper.countHouseListForOperator(status, keyword, request.prtnrSn(),
				sidoRegionCd, sigunguRegionCd);
		List<OperatorHouseListItemResponse> items = houseMapper
				.selectHouseListForOperator(status, keyword, request.prtnrSn(), sidoRegionCd, sigunguRegionCd,
						pageSize, request.offset())
				.stream()
				.map(OperatorHouseListItemResponse::from)
				.toList();
		return PageResponse.of(items, total, page, pageSize);
	}

	@Override
	@Transactional(readOnly = true)
	public OperatorHouseFilterOptionsResponse filterOptions() {
		// 상태 = 실제 HOUSE_STTS_CD SSOT(검수요청/검수중 없음). 지역 = REGION 시도 마스터(안정 코드).
		List<OperatorHouseFilterOptionsResponse.StatusOption> statuses = Arrays.stream(OperatorHouseStatus.values())
				.map(s -> new OperatorHouseFilterOptionsResponse.StatusOption(s.value(), s.label(), true))
				.toList();
		List<OperatorHouseFilterOptionsResponse.RegionOption> regions = regionService.sidoRegions().stream()
				.map(r -> new OperatorHouseFilterOptionsResponse.RegionOption(r.getRegionCd(), regionLabel(r)))
				.toList();
		// regionFilter 기능은 지원(WHERE 구현) → of() 내부 false. 데이터 미매핑 건수만 분리 제공(UX 안내용).
		long regionUnmappedCount = houseMapper.countHousesWithoutRegion();
		return OperatorHouseFilterOptionsResponse.of(statuses, regions, regionUnmappedCount);
	}

	/** 시도 표시명 — 짧은명 우선(없으면 공식명). */
	private static String regionLabel(RegionVO region) {
		return region.getRegionShortNm() != null ? region.getRegionShortNm() : region.getRegionNm();
	}

	@Override
	@Transactional(readOnly = true)
	public OperatorHouseDetailDrawerResponse detailDrawer(long houseSn, LocalDate from, LocalDate to) {
		// 창 결정 — 기본 [오늘(KST), 오늘+90). 과거 from 허용, 역전/최대 92일 초과는 400.
		LocalDate start = (from != null) ? from : LocalDate.now(KST);
		LocalDate end = (to != null) ? to : start.plusDays(DRAWER_DEFAULT_DAYS);
		long span = ChronoUnit.DAYS.between(start, end);
		if (span <= 0 || span > DRAWER_MAX_SPAN_DAYS) {
			throw new BusinessException(HouseErrorCode.HOUSE_DRAWER_RANGE_INVALID);
		}

		HouseDrawerVO h = houseDrawerMapper.selectDrawerHouse(houseSn);
		if (h == null) {
			throw new BusinessException(HouseErrorCode.HOUSE_NOT_FOUND);
		}

		List<DrawerRoomVO> roomVos = houseDrawerMapper.selectDrawerRooms(houseSn, start, end);
		List<Long> roomSns = roomVos.stream().map(DrawerRoomVO::getRoomSn).toList();

		// 침구/편의시설/이미지 — roomSn IN 배치(미삭제 등록 객실 없으면 조회 생략). 서비스에서 roomSn별 그룹핑.
		Map<Long, List<OperatorHouseDetailDrawerResponse.Bedding>> beddingsByRoom = roomSns.isEmpty() ? Map.of()
				: houseDrawerMapper.selectDrawerBeddings(roomSns).stream().collect(Collectors.groupingBy(
						DrawerRoomBeddingVO::getRoomSn,
						Collectors.mapping(OperatorHouseDetailDrawerResponse.Bedding::from, Collectors.toList())));
		Map<Long, List<OperatorHouseDetailDrawerResponse.Amenity>> amenitiesByRoom = roomSns.isEmpty() ? Map.of()
				: houseDrawerMapper.selectDrawerAmenities(roomSns).stream().collect(Collectors.groupingBy(
						DrawerRoomAmenityVO::getRoomSn,
						Collectors.mapping(OperatorHouseDetailDrawerResponse.Amenity::from, Collectors.toList())));
		Map<Long, List<OperatorHouseDetailDrawerResponse.Image>> imagesByRoom = roomSns.isEmpty() ? Map.of()
				: houseDrawerMapper.selectDrawerImages(roomSns).stream().collect(Collectors.groupingBy(
						DrawerRoomImageVO::getRoomSn,
						Collectors.mapping(OperatorHouseDetailDrawerResponse.Image::from, Collectors.toList())));

		List<OperatorHouseDetailDrawerResponse.RoomItem> rooms = roomVos.stream()
				.map(r -> toRoomItem(r, beddingsByRoom, amenitiesByRoom, imagesByRoom)).toList();

		// 수용인원 = 미삭제 등록 객실 전체(상태 무관) 합산 — DRAFT/REVIEW_REQUESTED 고택에서도 표시(검수/조회 표면).
		int bscTotal = roomVos.stream().mapToInt(r -> r.getBscPrsnCnt() == null ? 0 : r.getBscPrsnCnt()).sum();
		int maxTotal = roomVos.stream().mapToInt(r -> r.getMaxPrsnCnt() == null ? 0 : r.getMaxPrsnCnt()).sum();

		// 요일 요금 버킷 — 없는 버킷은 null(고정 객체). 성수기는 저장 개념 없음 → 항상 null.
		Map<String, DrawerPriceBucketVO> buckets = houseDrawerMapper.selectDrawerPriceBuckets(houseSn, start, end)
				.stream().collect(Collectors.toMap(DrawerPriceBucketVO::getBucket, b -> b));
		OperatorHouseDetailDrawerResponse.PriceSummary priceSummary = new OperatorHouseDetailDrawerResponse.PriceSummary(
				OperatorHouseDetailDrawerResponse.PriceBucket.from(buckets.get("WEEKDAY")),
				OperatorHouseDetailDrawerResponse.PriceBucket.from(buckets.get("FRI")),
				OperatorHouseDetailDrawerResponse.PriceBucket.from(buckets.get("SAT")),
				null);

		// 휴관일 — 분모 = 창 내 캘린더 행 보유 미삭제 객실 수(캘린더 없는 DRAFT 객실이 기존 ACTIVE 객실 휴관일 표시를 삼키지 않도록).
		//   객실이 아예 없거나 캘린더 참여 객실이 0이면 빈 목록(HAVING 기준 성립 불가).
		int calendarRoomCount = roomSns.isEmpty() ? 0
				: houseDrawerMapper.countDrawerCalendarRooms(houseSn, start, end);
		List<LocalDate> closedDates = calendarRoomCount == 0 ? List.of()
				: houseDrawerMapper.selectDrawerHouseClosedDates(houseSn, start, end, calendarRoomCount);

		String houseRepImgUrl = h.getRepImgFileSn() == null ? null : FILE_URL_PREFIX + h.getRepImgFileSn();
		OperatorHouseDetailDrawerResponse.HouseInfo houseInfo = new OperatorHouseDetailDrawerResponse.HouseInfo(
				h.getHouseSn(), h.getHouseNm(), h.getPrtnrConm(), h.getAddr(), h.getHouseSttsCd(),
				houseRepImgUrl, h.getHouseDc(), formatTime(h.getChkinTime()), formatTime(h.getChkoutTime()),
				h.getNoticeCn(), h.getCnclRfndPlcyCn(), h.getPbtrnspGuideCn());
		// 판매자 — 상호/대표/사업자번호/통신판매신고번호/사업장주소만(연락처 PII 제외).
		OperatorHouseDetailDrawerResponse.SellerInfo seller = new OperatorHouseDetailDrawerResponse.SellerInfo(
				h.getPrtnrConm(), h.getRprsvNm(), h.getBrno(), h.getMailOrderSalesNo(), h.getBizAddr());

		return new OperatorHouseDetailDrawerResponse(houseInfo,
				new OperatorHouseDetailDrawerResponse.Capacity(bscTotal, maxTotal), priceSummary, rooms, closedDates,
				seller, new OperatorHouseDetailDrawerResponse.Unsupported(true, true));
	}

	private static OperatorHouseDetailDrawerResponse.RoomItem toRoomItem(DrawerRoomVO r,
			Map<Long, List<OperatorHouseDetailDrawerResponse.Bedding>> beddingsByRoom,
			Map<Long, List<OperatorHouseDetailDrawerResponse.Amenity>> amenitiesByRoom,
			Map<Long, List<OperatorHouseDetailDrawerResponse.Image>> imagesByRoom) {
		List<OperatorHouseDetailDrawerResponse.Image> imgs = imagesByRoom.getOrDefault(r.getRoomSn(), List.of());
		String repImgUrl = imgs.stream().filter(i -> Y.equals(i.rprsYn()))
				.map(OperatorHouseDetailDrawerResponse.Image::url).findFirst().orElse(null);
		return new OperatorHouseDetailDrawerResponse.RoomItem(
				r.getRoomSn(), r.getRoomNm(), r.getRoomSttsCd(), r.getBscPrsnCnt(), r.getMaxPrsnCnt(),
				r.getRoomDc(), r.getAreaSqm(), r.getRoomCnt(), r.getBathrmCnt(), repImgUrl, r.getRepPriceFrom(),
				beddingsByRoom.getOrDefault(r.getRoomSn(), List.of()),
				amenitiesByRoom.getOrDefault(r.getRoomSn(), List.of()), imgs);
	}

	/** LocalTime → "HH:mm"(null 이면 null). */
	private static String formatTime(LocalTime t) {
		return t == null ? null : t.format(HH_MM);
	}

	@Override
	@Transactional(readOnly = true)
	public HouseDetailResponse detail(long houseSn) {
		HouseVO vo = houseMapper.selectHouseDetail(houseSn);
		if (vo == null) {
			throw new BusinessException(HouseErrorCode.HOUSE_NOT_FOUND);
		}
		vo.setPayMethods(housePayMethodMapper.selectActiveCodesByHouse(houseSn));
		return HouseDetailResponse.from(vo);
	}

	@Override
	@Transactional(readOnly = true)
	public PublishCheckResponse publishCheck(long houseSn, AuthUser operator) {
		// 상태변경 없이 read-only 점검 — 점검 전용 resolver(승인/활성 게이트 미적용)로 owner 해석(타인/삭제는 404).
		// 파트너 미승인/정지는 evaluate 가 PARTNER_NOT_APPROVED 로 수집해야 하므로 일반 resolveOwnerForHouse(승인게이트)는 쓰지 않는다.
		long owner = proxyResolver.resolveOwnerForHouseForPublishCheck(houseSn, operator);
		return PublishCheckResponse.from(publishValidator.evaluate(houseSn, owner));
	}

	@Override
	@Transactional
	public void suspend(HouseSuspendRequest request, AuthUser operator) {
		AuthUser op = requireUser(operator);
		HouseVO vo = houseMapper.selectHouseForUpdate(request.houseSn());
		if (vo == null || Y.equals(vo.getDelYn())) {
			throw new BusinessException(HouseErrorCode.HOUSE_NOT_FOUND);
		}
		// 게시된(ACTIVE)·숨김(HIDDEN) 또는 이미 SUSPENDED 만 대상 — DRAFT(미게시)는 임시조치 의미 없음(codex MUST: 상태 유실 방지)
		String stts = vo.getHouseSttsCd();
		if (!STTS_ACTIVE.equals(stts) && !STTS_HIDDEN.equals(stts) && !STTS_SUSPENDED.equals(stts)) {
			throw BusinessException.conflict("게시되지 않은(작성중) 고택은 임시조치 대상이 아닙니다.");
		}
		// 이미 SUSPENDED 면 0건(멱등) — 별도 에러 없이 사유만 기록
		houseMapper.forceSuspendHouse(request.houseSn(), op.userSn());
		auditRecorder.recordSuccess(
				String.format("HOUSE_SUSPEND {\"houseSn\":%d,\"rsn\":\"%s\"}",
						request.houseSn(), jsonSafe(request.suspendRsn())),
				"HOUSE", String.valueOf(request.houseSn()));
	}

	@Override
	@Transactional
	public void release(HouseReleaseRequest request, AuthUser operator) {
		AuthUser op = requireUser(operator);
		HouseVO vo = houseMapper.selectHouseForUpdate(request.houseSn());
		if (vo == null || Y.equals(vo.getDelYn())) {
			throw new BusinessException(HouseErrorCode.HOUSE_NOT_FOUND);
		}
		if (!STTS_SUSPENDED.equals(vo.getHouseSttsCd())) {
			throw BusinessException.conflict("임시중지 상태의 고택이 아닙니다.");
		}
		houseMapper.releaseHouse(request.houseSn(), op.userSn());
		auditRecorder.recordSuccess(
				String.format("HOUSE_RELEASE {\"houseSn\":%d}", request.houseSn()),
				"HOUSE", String.valueOf(request.houseSn()));
	}

	// ============ 운영자 대행(HOUSE_PROXY_MNG) — owner=대상 고택 서버해석, 행위자=운영자 ============

	@Override
	@Transactional
	@Audit(actn = "HOUSE_PROXY_UPDATE", trgtType = "HOUSE", trgtId = "#request.houseSn()")
	public void update(HouseUpdateRequest request, AuthUser operator) {
		long owner = proxyResolver.resolveOwnerForHouse(request.houseSn(), operator);
		validateRegion(request.sidoRegionCd(), request.sigunguRegionCd());
		// 발행 후 표류 방지(비퇴행) — 기본정보 변경 전 readiness 캡처(가드가 HOUSE FOR UPDATE 선잠금)
		ReadinessSnapshot before = publishGuard.snapshotIfActive(request.houseSn(), owner);
		try {
			if (houseMapper.updateHouse(request.houseSn(), owner, request.houseNm(), request.zip(),
					request.addr(), request.daddr(), request.houseDc(),
					request.sidoRegionCd(), request.sigunguRegionCd(), operator.userSn()) == 0) {
				throw new BusinessException(HouseErrorCode.HOUSE_NOT_FOUND);
			}
		} catch (DuplicateKeyException e) {
			throw new BusinessException(HouseErrorCode.HOUSE_NAME_DUPLICATE,
					HouseErrorCode.HOUSE_NAME_DUPLICATE.defaultMessage(), e);
		}
		// 기본정보(houseNm/zip/addr/houseDc)를 공란화해 충족→미충족 전환이면 거부(롤백)
		publishGuard.enforceNoRegression(request.houseSn(), owner, before);
		// 검수요청 상태였으면 내용 변경으로 검수 무효 → DRAFT 자동복귀(+REVERT 이력)
		reviewGuard.markDirtyIfUnderReview(request.houseSn(), operator.userSn());
	}

	@Override
	@Transactional
	@Audit(actn = "HOUSE_PROXY_STATUS_CHANGE", trgtType = "HOUSE", trgtId = "#request.houseSn()")
	public void changeStatus(HouseStatusChangeRequest request, AuthUser operator) {
		long owner = proxyResolver.resolveOwnerForHouse(request.houseSn(), operator);
		HouseVO vo = houseMapper.selectHouseForUpdate(request.houseSn());
		if (vo == null || Y.equals(vo.getDelYn()) || !vo.getPrtnrSn().equals(owner)) {
			throw new BusinessException(HouseErrorCode.HOUSE_NOT_FOUND);
		}
		// 현재 SUSPENDED(임시중지) 고택은 일반 대행 상태변경으로 빠져나갈 수 없음 — 해제는 release(HOUSE_MODER) 전용(codex MUST).
		if (STTS_SUSPENDED.equals(vo.getHouseSttsCd())) {
			throw BusinessException.conflict("임시중지(SUSPENDED) 고택은 해제(release) 후에만 상태를 변경할 수 있습니다.");
		}
		// 현재 REVIEW_REQUESTED(검수요청) 고택은 /status 로 전환 불가 — 승인/반려(review-approve/reject, HOUSE_REVIEW) 전용.
		// (PROXY_MNG 가 /status→ACTIVE 로 검수 승인게이트를 우회하는 것 차단)
		if (STTS_REVIEW_REQUESTED.equals(vo.getHouseSttsCd())) {
			throw new BusinessException(HouseErrorCode.HOUSE_REVIEW_STATE_INVALID);
		}
		// 운영자 대행 상태변경은 DRAFT/ACTIVE/HIDDEN 만(SUSPENDED 는 suspend/release(HOUSE_MODER) 전용) — DTO @Pattern 외 2중 방어
		if (STTS_SUSPENDED.equals(request.houseSttsCd())) {
			throw BusinessException.conflict("운영자 대행 상태변경은 SUSPENDED 를 지정할 수 없습니다.");
		}
		// 게시(ACTIVE) 전이 시 발행 필수조건 검증
		if (STTS_ACTIVE.equals(request.houseSttsCd())) {
			publishValidator.validate(request.houseSn(), owner);
		}
		if (houseMapper.updateHouseStatus(request.houseSn(), owner, request.houseSttsCd(), operator.userSn()) == 0) {
			throw new BusinessException(HouseErrorCode.HOUSE_NOT_FOUND);
		}
	}

	@Override
	@Transactional
	@Audit(actn = "HOUSE_PROXY_POLICY_UPDATE", trgtType = "HOUSE", trgtId = "#request.houseSn()")
	public void updatePolicy(HousePolicyUpdateRequest request, AuthUser operator) {
		long owner = proxyResolver.resolveOwnerForHouse(request.houseSn(), operator);
		requireActiveOwned(houseMapper.selectHouseForUpdate(request.houseSn()), owner);
		String rsvtCnfirmCd = trimToNull(request.rsvtCnfirmCd());
		validateRsvtCnfirmCd(rsvtCnfirmCd);
		// 발행 후 표류 방지(비퇴행) — 정책 변경 전 readiness 캡처
		ReadinessSnapshot before = publishGuard.snapshotIfActive(request.houseSn(), owner);
		if (houseMapper.updateHousePolicy(request.houseSn(), owner,
				request.adultAddChrgAmt(), request.childAddChrgAmt(), request.infantAddChrgAmt(),
				parseTime(request.chkinTime()), parseTime(request.chkoutTime()), parseTime(request.lateChkoutTime()),
				rsvtCnfirmCd, request.cnclRfndPlcyCn(), request.pbtrnspGuideCn(), request.noticeCn(),
				operator.userSn()) == 0) {
			throw new BusinessException(HouseErrorCode.HOUSE_NOT_FOUND);
		}
		// 필수정책(취소정책/체크인아웃/예약확정)을 공란화해 충족→미충족 전환이면 거부(롤백)
		publishGuard.enforceNoRegression(request.houseSn(), owner, before);
		reviewGuard.markDirtyIfUnderReview(request.houseSn(), operator.userSn());
	}

	@Override
	@Transactional
	@Audit(actn = "HOUSE_PROXY_PAYMETHOD_SET", trgtType = "HOUSE", trgtId = "#request.houseSn()")
	public void setPayMethods(HousePayMethodSetRequest request, AuthUser operator) {
		long owner = proxyResolver.resolveOwnerForHouse(request.houseSn(), operator);
		requireActiveOwned(houseMapper.selectHouseForUpdate(request.houseSn()), owner);
		validatePayMethodCodes(request.payMthdCds());
		// 발행 후 표류 방지(비퇴행) — 결제수단 변경 전 readiness 캡처
		ReadinessSnapshot before = publishGuard.snapshotIfActive(request.houseSn(), owner);
		housePayMethodMapper.softDeleteActiveByHouse(request.houseSn(), operator.userSn());
		int seq = 0;
		for (String code : new LinkedHashSet<>(request.payMthdCds())) { // 중복 제거 + 입력순 보존
			HousePayMethodVO vo = HousePayMethodVO.builder()
					.houseSn(request.houseSn())
					.payMthdCd(code)
					.sortSeq(seq++)
					.rgtrSn(operator.userSn())
					.mdfrSn(operator.userSn())
					.build();
			housePayMethodMapper.insertPayMethod(vo);
		}
		// 결제수단을 전부 비워(true→false) 충족→미충족 전환이면 거부(롤백)
		publishGuard.enforceNoRegression(request.houseSn(), owner, before);
		reviewGuard.markDirtyIfUnderReview(request.houseSn(), operator.userSn());
	}

	@Override
	@Transactional
	@Audit(actn = "HOUSE_PROXY_DAILY_RATE_SET", trgtType = "HOUSE", trgtId = "#request.houseSn()")
	public void setDailyRate(HouseDailyRateSetRequest request, AuthUser operator) {
		long owner = proxyResolver.resolveOwnerForHouse(request.houseSn(), operator);
		requireActiveOwned(houseMapper.selectHouseForUpdate(request.houseSn()), owner);
		if (houseMapper.updateHouseDailyRate(request.houseSn(), owner,
				request.weekdayRateAmt(), request.fridayRateAmt(), request.saturdayRateAmt(), request.peakRateAmt(),
				operator.userSn()) == 0) {
			throw new BusinessException(HouseErrorCode.HOUSE_NOT_FOUND);
		}
		reviewGuard.markDirtyIfUnderReview(request.houseSn(), operator.userSn());
	}

	@Override
	@Transactional
	@Audit(actn = "HOUSE_PROXY_REGULAR_CLOSURE_SET", trgtType = "HOUSE", trgtId = "#request.houseSn()")
	public void setRegularClosure(HouseRegularClosureSetRequest request, AuthUser operator) {
		long owner = proxyResolver.resolveOwnerForHouse(request.houseSn(), operator);
		requireActiveOwned(houseMapper.selectHouseForUpdate(request.houseSn()), owner);
		String wkday = trimToNull(request.closedWkdayCd());
		if (wkday != null && !CLOSED_WEEKDAYS.contains(wkday)) {
			throw new BusinessException(HouseErrorCode.HOUSE_CLOSED_WEEKDAY_INVALID);
		}
		if (houseMapper.updateHouseRegularClosure(request.houseSn(), owner, wkday,
				Boolean.TRUE.equals(request.holidayClosed()) ? "Y" : "N",
				Boolean.TRUE.equals(request.lunarHolidayClosed()) ? "Y" : "N",
				operator.userSn()) == 0) {
			throw new BusinessException(HouseErrorCode.HOUSE_NOT_FOUND);
		}
		reviewGuard.markDirtyIfUnderReview(request.houseSn(), operator.userSn());
	}

	@Override
	@Transactional
	@Audit(actn = "HOUSE_PROXY_DELETE", trgtType = "HOUSE", trgtId = "#request.houseSn()")
	public void delete(HouseDeleteRequest request, AuthUser operator) {
		long owner = proxyResolver.resolveOwnerForHouse(request.houseSn(), operator);
		requireActiveOwned(houseMapper.selectHouseForUpdate(request.houseSn()), owner); // 선잠금 + 소유확인
		roomMapper.softDeleteRoomsByHouse(request.houseSn(), owner, operator.userSn());
		if (houseMapper.softDeleteHouse(request.houseSn(), owner, operator.userSn()) == 0) {
			throw new BusinessException(HouseErrorCode.HOUSE_NOT_FOUND);
		}
	}

	// ============ 검수 전이(REVIEW_REQUESTED) — 상태별 전용 API. 이력=HOUSE_REVIEW_HSTRY ============

	@Override
	@Transactional
	public void reviewRequest(HouseReviewRequest request, AuthUser operator) {
		AuthUser op = requireUser(operator);
		HouseVO vo = houseMapper.selectHouseForUpdate(request.houseSn());
		if (vo == null || Y.equals(vo.getDelYn())) {
			throw new BusinessException(HouseErrorCode.HOUSE_NOT_FOUND);
		}
		String from = vo.getHouseSttsCd();
		// 검수요청 진입은 DRAFT/HIDDEN 에서만(SUSPENDED/ACTIVE/이미 REVIEW_REQUESTED 는 불가)
		if (!STTS_DRAFT.equals(from) && !STTS_HIDDEN.equals(from)) {
			throw new BusinessException(HouseErrorCode.HOUSE_REVIEW_STATE_INVALID);
		}
		// 발행 9조건 통과 필수 — 미충족 시 HouseNotPublishableException(409, failedConditions). owner=대상 고택 소유파트너.
		publishValidator.validate(request.houseSn(), vo.getPrtnrSn());
		if (houseMapper.transitionHouseReviewStatus(request.houseSn(), from, STTS_REVIEW_REQUESTED, op.userSn()) == 0) {
			throw new BusinessException(HouseErrorCode.HOUSE_REVIEW_STATE_INVALID);
		}
		houseMapper.insertHouseReviewHstry(
				HouseReviewHstryVO.of(request.houseSn(), "REQUEST", from, STTS_REVIEW_REQUESTED, op.userSn(), null));
		auditRecorder.recordSuccess(
				String.format("HOUSE_REVIEW_REQUEST {\"houseSn\":%d}", request.houseSn()),
				"HOUSE", String.valueOf(request.houseSn()));
		// 검수요청 → 검수 권한(HOUSE_REVIEW) 보유 운영자에게 알림(emitter 가 권한 필터).
		notificationEmitter.emit(op.userSn(), "PRODUCT_REVIEW:" + request.houseSn(), NotificationType.PRODUCT_REVIEW,
				"상품 검수요청", null, String.valueOf(request.houseSn()));
	}

	@Override
	@Transactional
	public void reviewApprove(HouseReviewRequest request, AuthUser operator) {
		AuthUser op = requireUser(operator);
		HouseVO vo = houseMapper.selectHouseForUpdate(request.houseSn());
		if (vo == null || Y.equals(vo.getDelYn())) {
			throw new BusinessException(HouseErrorCode.HOUSE_NOT_FOUND);
		}
		if (!STTS_REVIEW_REQUESTED.equals(vo.getHouseSttsCd())) {
			throw new BusinessException(HouseErrorCode.HOUSE_REVIEW_STATE_INVALID);
		}
		// 승인 시점 발행 9조건 재검증(요청~승인 사이 표류 방지) — 미충족 시 409+failedConditions.
		publishValidator.validate(request.houseSn(), vo.getPrtnrSn());
		if (houseMapper.transitionHouseReviewStatus(request.houseSn(), STTS_REVIEW_REQUESTED, STTS_ACTIVE,
				op.userSn()) == 0) {
			throw new BusinessException(HouseErrorCode.HOUSE_REVIEW_STATE_INVALID);
		}
		houseMapper.insertHouseReviewHstry(HouseReviewHstryVO.of(request.houseSn(), "APPROVE", STTS_REVIEW_REQUESTED,
				STTS_ACTIVE, op.userSn(), null));
		auditRecorder.recordSuccess(
				String.format("HOUSE_REVIEW_APPROVE {\"houseSn\":%d}", request.houseSn()),
				"HOUSE", String.valueOf(request.houseSn()));
	}

	@Override
	@Transactional
	public void reviewReject(HouseReviewRejectRequest request, AuthUser operator) {
		AuthUser op = requireUser(operator);
		HouseVO vo = houseMapper.selectHouseForUpdate(request.houseSn());
		if (vo == null || Y.equals(vo.getDelYn())) {
			throw new BusinessException(HouseErrorCode.HOUSE_NOT_FOUND);
		}
		if (!STTS_REVIEW_REQUESTED.equals(vo.getHouseSttsCd())) {
			throw new BusinessException(HouseErrorCode.HOUSE_REVIEW_STATE_INVALID);
		}
		if (houseMapper.transitionHouseReviewStatus(request.houseSn(), STTS_REVIEW_REQUESTED, STTS_DRAFT,
				op.userSn()) == 0) {
			throw new BusinessException(HouseErrorCode.HOUSE_REVIEW_STATE_INVALID);
		}
		houseMapper.insertHouseReviewHstry(HouseReviewHstryVO.of(request.houseSn(), "REJECT", STTS_REVIEW_REQUESTED,
				STTS_DRAFT, op.userSn(), request.rejectReason()));
		// 반려사유 원문은 HOUSE_REVIEW_HSTRY.RJCT_RSN 에만 저장(감사 ACTN_CN 은 VARCHAR(100)·절삭 → 원문 부적합)
		auditRecorder.recordSuccess(
				String.format("HOUSE_REVIEW_REJECT {\"houseSn\":%d}", request.houseSn()),
				"HOUSE", String.valueOf(request.houseSn()));
	}

	// ------------------------------------------------------------------

	private static AuthUser requireUser(AuthUser user) {
		if (user == null) {
			throw BusinessException.unauthorized();
		}
		return user;
	}

	/** 활성 + 해석된 소유자(PRTNR_SN) — 아니면 404(열거 방지). */
	private static void requireActiveOwned(HouseVO vo, long ownerPrtnrSn) {
		if (vo == null || Y.equals(vo.getDelYn()) || !vo.getPrtnrSn().equals(ownerPrtnrSn)) {
			throw new BusinessException(HouseErrorCode.HOUSE_NOT_FOUND);
		}
	}

	/** 지역코드 조합 검증(REGION 존재·시군구 소속) — 무효면 400. 둘 다 null(DRAFT)은 통과. */
	private void validateRegion(String sidoRegionCd, String sigunguRegionCd) {
		if (!regionService.isValidHouseRegion(sidoRegionCd, sigunguRegionCd)) {
			throw new BusinessException(HouseErrorCode.HOUSE_REGION_INVALID);
		}
	}

	/** 결제수단 코드가 COMM_CD PAY_MTHD 에 모두 존재하는지 — 아니면 400(빈 배열은 통과=전부해제). */
	private void validatePayMethodCodes(List<String> codes) {
		if (codes.isEmpty()) {
			return;
		}
		Set<String> allowed = codeService.list(PAY_MTHD_GROUP).stream()
				.map(CodeItemResponse::cd).collect(Collectors.toSet());
		for (String code : codes) {
			if (!allowed.contains(code)) {
				throw new BusinessException(HouseErrorCode.HOUSE_PAY_METHOD_INVALID);
			}
		}
	}

	/** 예약확정방식 코드가 COMM_CD RSVT_CNFIRM 에 존재하는지(non-null 일 때만) — 아니면 400. */
	private void validateRsvtCnfirmCd(String rsvtCnfirmCd) {
		if (rsvtCnfirmCd == null) {
			return;
		}
		Set<String> allowed = codeService.list(RSVT_CNFIRM_GROUP).stream()
				.map(CodeItemResponse::cd).collect(Collectors.toSet());
		if (!allowed.contains(rsvtCnfirmCd)) {
			throw new BusinessException(HouseErrorCode.HOUSE_RSVT_CNFIRM_INVALID);
		}
	}

	/** "HH:mm" → LocalTime(공백/널이면 null). 형식은 DTO @Pattern 이 보장. */
	private static LocalTime parseTime(String hhmm) {
		String v = trimToNull(hhmm);
		return v == null ? null : LocalTime.parse(v);
	}

	/** 감사 메시지 JSON 안전화 — 따옴표/역슬래시/개행 제거(로그 인젝션 방지). */
	private static String jsonSafe(String s) {
		if (s == null) {
			return "";
		}
		return s.replaceAll("[\"\\\\\\r\\n]", " ");
	}

	private static String escapeLike(String s) {
		if (s == null) {
			return null;
		}
		return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
	}

	private static String trimToNull(String s) {
		return (s == null || s.isBlank()) ? null : s.trim();
	}
}
