package egovframework.eeum.house.service.house.impl;

import java.time.LocalTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import egovframework.eeum.account.service.partner.PartnerService;
import egovframework.eeum.auth.vo.AuthUser;
import egovframework.eeum.base.audit.Audit;
import egovframework.eeum.base.exception.BusinessException;
import egovframework.eeum.base.response.PageResponse;
import egovframework.eeum.code.service.code.CodeService;
import egovframework.eeum.code.web.dto.CodeItemResponse;
import egovframework.eeum.house.exception.HouseErrorCode;
import egovframework.eeum.house.service.house.HousePublishGuard;
import egovframework.eeum.house.service.house.ReadinessSnapshot;
import egovframework.eeum.house.service.house.HousePublishValidator;
import egovframework.eeum.house.service.house.HouseService;
import egovframework.eeum.house.service.house.dao.HouseMapper;
import egovframework.eeum.house.service.house.dao.HousePayMethodMapper;
import egovframework.eeum.house.service.room.dao.RoomMapper;
import egovframework.eeum.house.vo.HousePayMethodVO;
import egovframework.eeum.house.vo.HouseVO;
import egovframework.eeum.house.web.dto.HouseDetailResponse;
import egovframework.eeum.house.web.dto.HouseInsertRequest;
import egovframework.eeum.house.web.dto.HouseListItemResponse;
import egovframework.eeum.house.web.dto.HousePayMethodSetRequest;
import egovframework.eeum.house.web.dto.HousePolicyUpdateRequest;
import egovframework.eeum.house.web.dto.HouseSearchRequest;
import egovframework.eeum.house.web.dto.HouseStatusChangeRequest;
import egovframework.eeum.house.web.dto.HouseUpdateRequest;
import egovframework.eeum.region.service.region.RegionService;
import lombok.RequiredArgsConstructor;

/**
 * 고택 서비스 구현(판매자) — 소유자/행위자 분리, 소유권 격리(prtnrSn), 상태머신 검증.
 *
 * <p>소유자(PRTNR_SN)는 {@link PartnerService}로 인증 사용자에서 해석(요청 body 불신 — IDOR 차단).
 * 운영자 임시조치(SUSPENDED)된 고택은 판매자가 스스로 상태를 되돌릴 수 없다.
 * 위저드 Step2(정책/결제수단)·Step3(발행)은 ACTIVE 전이 전 {@link HousePublishValidator}로 필수조건을 검증한다.
 */
@Service
@RequiredArgsConstructor
public class HouseServiceImpl implements HouseService {

	private static final String Y = "Y";
	private static final String STTS_ACTIVE = "ACTIVE";
	private static final String STTS_SUSPENDED = "SUSPENDED";
	private static final String PAY_MTHD_GROUP = "PAY_MTHD";
	private static final String RSVT_CNFIRM_GROUP = "RSVT_CNFIRM";

	private final HouseMapper houseMapper;
	private final RoomMapper roomMapper;
	private final PartnerService partnerService;
	private final HousePayMethodMapper housePayMethodMapper;
	private final CodeService codeService;
	private final HousePublishValidator publishValidator;
	private final HousePublishGuard publishGuard;
	private final RegionService regionService;

	@Override
	@Transactional
	@Audit(actn = "HOUSE_INSERT", trgtType = "HOUSE", trgtId = "#result.houseSn()")
	public HouseDetailResponse insert(HouseInsertRequest request, AuthUser partner) {
		long prtnrSn = requirePrtnrSn(partner);
		validateRegion(request.sidoRegionCd(), request.sigunguRegionCd());
		HouseVO vo = HouseVO.builder()
				.prtnrSn(prtnrSn)
				.houseNm(request.houseNm())
				.zip(request.zip())
				.addr(request.addr())
				.daddr(request.daddr())
				.houseDc(request.houseDc())
				.sidoRegionCd(request.sidoRegionCd())
				.sigunguRegionCd(request.sigunguRegionCd())
				.rgtrSn(partner.userSn())
				.build();
		try {
			houseMapper.insertHouse(vo); // HOUSE_STTS_CD='DRAFT', DEL_YN='N' DB 기본값
		} catch (DuplicateKeyException e) {
			throw new BusinessException(HouseErrorCode.HOUSE_NAME_DUPLICATE,
					HouseErrorCode.HOUSE_NAME_DUPLICATE.defaultMessage(), e);
		}
		return detail(vo.getHouseSn(), partner);
	}

	@Override
	@Transactional(readOnly = true)
	public PageResponse<HouseListItemResponse> list(HouseSearchRequest request, AuthUser partner) {
		long prtnrSn = requirePrtnrSn(partner);
		String status = trimToNull(request.status());
		String keyword = escapeLike(trimToNull(request.keyword()));
		int page = request.pageOrDefault();
		int pageSize = request.pageSizeOrDefault();
		long total = houseMapper.countHouseList(prtnrSn, status, keyword);
		List<HouseListItemResponse> items = houseMapper
				.selectHouseListOffset(prtnrSn, status, keyword, pageSize, request.offset()).stream()
				.map(HouseListItemResponse::from)
				.toList();
		return PageResponse.of(items, total, page, pageSize);
	}

	@Override
	@Transactional(readOnly = true)
	public HouseDetailResponse detail(long houseSn, AuthUser partner) {
		long prtnrSn = requirePrtnrSn(partner);
		HouseVO vo = houseMapper.selectHouseDetail(houseSn);
		// 미존재 또는 타 파트너 소유 → 404(소유권 격리 + 열거 방지)
		if (vo == null || !vo.getPrtnrSn().equals(prtnrSn)) {
			throw new BusinessException(HouseErrorCode.HOUSE_NOT_FOUND);
		}
		vo.setPayMethods(housePayMethodMapper.selectActiveCodesByHouse(houseSn));
		return HouseDetailResponse.from(vo);
	}

	@Override
	@Transactional
	@Audit(actn = "HOUSE_UPDATE", trgtType = "HOUSE", trgtId = "#request.houseSn()")
	public void update(HouseUpdateRequest request, AuthUser partner) {
		long prtnrSn = requirePrtnrSn(partner);
		requireActiveOwned(houseMapper.selectHouseForUpdate(request.houseSn()), prtnrSn);
		validateRegion(request.sidoRegionCd(), request.sigunguRegionCd());
		// 발행 후 표류 방지(비퇴행) — 기본정보(houseNm/zip/addr/houseDc) 변경 전 readiness 캡처
		ReadinessSnapshot before = publishGuard.snapshotIfActive(request.houseSn(), prtnrSn);
		try {
			if (houseMapper.updateHouse(request.houseSn(), prtnrSn, request.houseNm(), request.zip(),
					request.addr(), request.daddr(), request.houseDc(),
					request.sidoRegionCd(), request.sigunguRegionCd(), partner.userSn()) == 0) {
				throw new BusinessException(HouseErrorCode.HOUSE_NOT_FOUND);
			}
		} catch (DuplicateKeyException e) {
			throw new BusinessException(HouseErrorCode.HOUSE_NAME_DUPLICATE,
					HouseErrorCode.HOUSE_NAME_DUPLICATE.defaultMessage(), e);
		}
		// 기본정보(houseNm/zip/addr/houseDc)를 공란화해 충족→미충족 전환이면 거부(롤백)
		publishGuard.enforceNoRegression(request.houseSn(), prtnrSn, before);
	}

	@Override
	@Transactional
	@Audit(actn = "HOUSE_STATUS_CHANGE", trgtType = "HOUSE", trgtId = "#request.houseSn()")
	public void changeStatus(HouseStatusChangeRequest request, AuthUser partner) {
		long prtnrSn = requirePrtnrSn(partner);
		HouseVO vo = houseMapper.selectHouseForUpdate(request.houseSn());
		requireActiveOwned(vo, prtnrSn);
		// 운영자 임시중지(SUSPENDED)는 판매자가 직접 해제 불가
		if (STTS_SUSPENDED.equals(vo.getHouseSttsCd())) {
			throw new BusinessException(HouseErrorCode.HOUSE_SUSPENDED);
		}
		// 게시(ACTIVE) 전이 시 발행 필수조건 검증. DRAFT/HIDDEN 전이는 검증 없음.
		if (STTS_ACTIVE.equals(request.houseSttsCd())) {
			publishValidator.validate(request.houseSn(), prtnrSn);
		}
		// 매퍼 WHERE 에도 SUSPENDED 차단(2중 방어). 본인 소유 + 미삭제일 때만 갱신.
		if (houseMapper.updateHouseStatus(request.houseSn(), prtnrSn, request.houseSttsCd(), partner.userSn()) == 0) {
			throw new BusinessException(HouseErrorCode.HOUSE_NOT_FOUND);
		}
	}

	@Override
	@Transactional
	@Audit(actn = "HOUSE_POLICY_UPDATE", trgtType = "HOUSE", trgtId = "#request.houseSn()")
	public void updatePolicy(HousePolicyUpdateRequest request, AuthUser partner) {
		updatePolicyCore(request, requirePrtnrSn(partner), partner.userSn());
	}

	@Override
	@Transactional
	@Audit(actn = "HOUSE_PAYMETHOD_SET", trgtType = "HOUSE", trgtId = "#request.houseSn()")
	public void setPayMethods(HousePayMethodSetRequest request, AuthUser partner) {
		setPayMethodsCore(request, requirePrtnrSn(partner), partner.userSn());
	}

	@Override
	@Transactional
	@Audit(actn = "HOUSE_DELETE", trgtType = "HOUSE", trgtId = "#houseSn")
	public void delete(long houseSn, AuthUser partner) {
		long prtnrSn = requirePrtnrSn(partner);
		requireActiveOwned(houseMapper.selectHouseForUpdate(houseSn), prtnrSn); // 고택 선잠금(락 순서 HOUSE→ROOM)
		// 하위 객실 먼저 soft delete(고택 활성 상태에서 — JOIN H.PRTNR_SN 가드) → 그다음 고택. 고아 객실 방지.
		roomMapper.softDeleteRoomsByHouse(houseSn, prtnrSn, partner.userSn());
		if (houseMapper.softDeleteHouse(houseSn, prtnrSn, partner.userSn()) == 0) {
			throw new BusinessException(HouseErrorCode.HOUSE_NOT_FOUND);
		}
	}

	// ============ 코어(소유자=ownerPrtnrSn, 행위자=actorUserSn 공유 — 판매자/운영자 동일 로직) ============

	/** 정책 전체저장 — 부모 고택 선잠금 → 예약확정방식 검증 → 시각 파싱 → 8컬럼 일괄 SET. */
	private void updatePolicyCore(HousePolicyUpdateRequest request, long ownerPrtnrSn, long actorUserSn) {
		requireActiveOwned(houseMapper.selectHouseForUpdate(request.houseSn()), ownerPrtnrSn);
		String rsvtCnfirmCd = trimToNull(request.rsvtCnfirmCd());
		validateRsvtCnfirmCd(rsvtCnfirmCd);
		// 발행 후 표류 방지(비퇴행) — 변경 전 readiness 캡처(비ACTIVE면 null)
		ReadinessSnapshot before = publishGuard.snapshotIfActive(request.houseSn(), ownerPrtnrSn);
		if (houseMapper.updateHousePolicy(request.houseSn(), ownerPrtnrSn,
				request.adultAddChrgAmt(), request.childAddChrgAmt(), request.infantAddChrgAmt(),
				parseTime(request.chkinTime()), parseTime(request.chkoutTime()), parseTime(request.lateChkoutTime()),
				rsvtCnfirmCd, request.cnclRfndPlcyCn(), request.pbtrnspGuideCn(), request.noticeCn(),
				actorUserSn) == 0) {
			throw new BusinessException(HouseErrorCode.HOUSE_NOT_FOUND);
		}
		// 필수정책(취소정책/체크인아웃/예약확정)을 공란화해 충족→미충족 전환이면 거부(롤백)
		publishGuard.enforceNoRegression(request.houseSn(), ownerPrtnrSn, before);
	}

	/** 결제수단 전체교체 — 부모 고택 선잠금 → 코드 검증 → 활성 비움 → 순서대로 재삽입(빈 배열=전부해제). */
	private void setPayMethodsCore(HousePayMethodSetRequest request, long ownerPrtnrSn, long actorUserSn) {
		requireActiveOwned(houseMapper.selectHouseForUpdate(request.houseSn()), ownerPrtnrSn);
		validatePayMethodCodes(request.payMthdCds());
		// 발행 후 표류 방지(비퇴행) — 변경 전 readiness 캡처(비ACTIVE면 null)
		ReadinessSnapshot before = publishGuard.snapshotIfActive(request.houseSn(), ownerPrtnrSn);
		housePayMethodMapper.softDeleteActiveByHouse(request.houseSn(), actorUserSn);
		int seq = 0;
		for (String code : new LinkedHashSet<>(request.payMthdCds())) { // 중복 제거 + 입력순 보존
			HousePayMethodVO vo = HousePayMethodVO.builder()
					.houseSn(request.houseSn())
					.payMthdCd(code)
					.sortSeq(seq++)
					.rgtrSn(actorUserSn)
					.mdfrSn(actorUserSn)
					.build();
			housePayMethodMapper.insertPayMethod(vo);
		}
		// 결제수단을 전부 비워(true→false) ACTIVE 고택이 미달로 전환되면 거부(롤백)
		publishGuard.enforceNoRegression(request.houseSn(), ownerPrtnrSn, before);
	}

	// ------------------------------------------------------------------

	/** 로그인 사용자의 활성 파트너 일련번호 — 아니면 403. */
	private long requirePrtnrSn(AuthUser partner) {
		if (partner == null) {
			throw BusinessException.unauthorized();
		}
		Long prtnrSn = partnerService.findManageablePrtnrSn(partner.userSn());
		if (prtnrSn == null) {
			throw new BusinessException(HouseErrorCode.HOUSE_NOT_PARTNER);
		}
		return prtnrSn;
	}

	/** 활성 + 본인 소유(PRTNR_SN) — 아니면 404(열거 방지). */
	private static void requireActiveOwned(HouseVO vo, long prtnrSn) {
		if (vo == null || Y.equals(vo.getDelYn()) || !vo.getPrtnrSn().equals(prtnrSn)) {
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

	/** LIKE 와일드카드(%, _, \) 이스케이프 — 리터럴 검색(매퍼 ESCAPE '\\' 와 짝). */
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
