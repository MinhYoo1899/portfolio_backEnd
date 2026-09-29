package egovframework.eeum.settlement.service.settlement.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import egovframework.eeum.auth.vo.AuthUser;
import egovframework.eeum.base.audit.Audit;
import egovframework.eeum.base.exception.BusinessException;
import egovframework.eeum.settlement.exception.SettlementErrorCode;
import egovframework.eeum.settlement.service.settlement.SettlementService;
import egovframework.eeum.settlement.service.settlement.dao.SettlementMapper;
import egovframework.eeum.settlement.vo.PartnerSettlementRowVO;
import egovframework.eeum.settlement.vo.PartnerSettlementVO;
import egovframework.eeum.settlement.vo.SettlementSettingVO;
import egovframework.eeum.settlement.web.dto.PartnerSettlementExecuteRequest;
import egovframework.eeum.settlement.web.dto.PartnerSettlementResponse;
import egovframework.eeum.settlement.web.dto.SettlementSettingResponse;
import egovframework.eeum.settlement.web.dto.SettlementSettingUpdateRequest;
import egovframework.eeum.notification.service.notification.NotificationEmitter;
import egovframework.eeum.notification.service.notification.NotificationType;
import lombok.RequiredArgsConstructor;

/**
 * 정산 서비스 구현. 단일행(SN=1) 정산 설정을 조회/전체저장/기본값복원, 회차 파트너 집계 목록 + 실행 기록.
 *
 * <p>수수료/정산금은 설정값(수수료율 %)으로 산출: 카드수수료·플랫폼수수료 = 판매금 × 율/100(반올림),
 * 정산금 = 판매금 − 카드수수료 − 플랫폼수수료. 판매집계는 APPROVED 결제·체크아웃월 기준.
 * ★계좌이체는 오프라인 — 실행은 시스템 상태(COMPLETED)만 스냅샷 기록.
 */
@Service
@RequiredArgsConstructor
public class SettlementServiceImpl implements SettlementService {

	private static final Pattern ROUND_YM = Pattern.compile("^\\d{4}-\\d{2}$");
	private static final Set<String> ALLOWED_BASIS = Set.of("CHECKOUT_DATE", "CONFIRMED_DATE");
	private static final Set<String> ALLOWED_CYCLE = Set.of("MONTHLY", "WEEKLY", "DAILY");
	private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

	private final SettlementMapper settlementMapper;
	private final NotificationEmitter notificationEmitter;

	@Override
	@Transactional(readOnly = true)
	public SettlementSettingResponse getSettings() {
		return SettlementSettingResponse.from(requireSetting());
	}

	@Override
	@Transactional
	@Audit(actn = "SETTLEMENT_SETTING_UPDATE", trgtType = "SETTLEMENT_SETTING", trgtId = "1")
	public SettlementSettingResponse updateSettings(SettlementSettingUpdateRequest request, AuthUser user) {
		validateBasis(request.settlementBasisCd());
		validateCycle(request.settlementCycleCd());
		if (settlementMapper.updateSetting(request.platformFeeRate(), request.cardFeeRate(), request.vatRate(),
				request.settlementBasisCd(), request.depositDueDays(), request.settlementCycleCd(),
				request.minSettlementAmt(), request.autoInvoice() ? "Y" : "N", request.taxInvoice() ? "Y" : "N",
				user.userSn()) == 0) {
			throw new BusinessException(SettlementErrorCode.SETTLEMENT_SETTING_NOT_FOUND);
		}
		return getSettings();
	}

	@Override
	@Transactional
	@Audit(actn = "SETTLEMENT_SETTING_RESET", trgtType = "SETTLEMENT_SETTING", trgtId = "1")
	public SettlementSettingResponse resetSettings(AuthUser user) {
		if (settlementMapper.updateSetting(BigDecimal.valueOf(15), BigDecimal.valueOf(3), BigDecimal.valueOf(10),
				"CHECKOUT_DATE", 5, "MONTHLY", 10000, "Y", "Y", user.userSn()) == 0) {
			throw new BusinessException(SettlementErrorCode.SETTLEMENT_SETTING_NOT_FOUND);
		}
		return getSettings();
	}

	@Override
	@Transactional(readOnly = true)
	public List<PartnerSettlementResponse> listPartnerSettlements(String roundYm) {
		validateRound(roundYm);
		SettlementSettingVO setting = requireSetting();
		return settlementMapper.selectPartnerSettlementRows(roundYm).stream()
				.map(row -> toResponse(roundYm, row, setting)).toList();
	}

	@Override
	@Transactional
	@Audit(actn = "SETTLEMENT_EXECUTE", trgtType = "PARTNER_SETTLEMENT", trgtId = "#request.prtnrSn()")
	public PartnerSettlementResponse executePartnerSettlement(PartnerSettlementExecuteRequest request, AuthUser user) {
		validateRound(request.roundYm());
		if (settlementMapper.countExecuted(request.roundYm(), request.prtnrSn()) > 0) {
			throw new BusinessException(SettlementErrorCode.SETTLEMENT_ALREADY_DONE);
		}
		Long sales = settlementMapper.selectPartnerSalesForRound(request.roundYm(), request.prtnrSn());
		if (sales == null || sales <= 0) {
			throw new BusinessException(SettlementErrorCode.SETTLEMENT_TARGET_NOT_FOUND);
		}
		SettlementSettingVO setting = requireSetting();
		long cardFee = fee(sales, setting.getCardFeeRate());
		long platformFee = fee(sales, setting.getPlatformFeeRate());
		long settlement = sales - cardFee - platformFee;

		PartnerSettlementVO record = new PartnerSettlementVO();
		record.setRoundYm(request.roundYm());
		record.setPrtnrSn(request.prtnrSn());
		record.setTotalSalesAmt(sales.intValue());
		record.setCardFeeAmt((int) cardFee);
		record.setPlatformFeeAmt((int) platformFee);
		record.setSettlementAmt((int) settlement);
		record.setRgtrSn(user.userSn());
		settlementMapper.insertPartnerSettlement(record);
		// targetId=prtnrSn(생성키 미보장 회피, 안전), eventKey=회차+파트너로 유일. 정산 완료 알림.
		notificationEmitter.emit(user.userSn(), "SETTLEMENT:" + request.roundYm() + ":" + request.prtnrSn(),
				NotificationType.SETTLEMENT, "정산 완료", request.roundYm(), String.valueOf(request.prtnrSn()));

		// 실행 직후 상태(COMPLETED·실행일시)를 반영해 재조회 후 응답 구성
		return settlementMapper.selectPartnerSettlementRows(request.roundYm()).stream()
				.filter(r -> r.getPrtnrSn() != null && r.getPrtnrSn().equals(request.prtnrSn()))
				.findFirst()
				.map(r -> toResponse(request.roundYm(), r, setting))
				.orElseThrow(() -> new BusinessException(SettlementErrorCode.SETTLEMENT_TARGET_NOT_FOUND));
	}

	// ------------------------------------------------------------------

	private PartnerSettlementResponse toResponse(String roundYm, PartnerSettlementRowVO row, SettlementSettingVO setting) {
		long sales = row.getTotalSalesAmt() == null ? 0L : row.getTotalSalesAmt();
		long cardFee = fee(sales, setting.getCardFeeRate());
		long platformFee = fee(sales, setting.getPlatformFeeRate());
		long settlement = sales - cardFee - platformFee;
		return PartnerSettlementResponse.of(roundYm, row, cardFee, platformFee, settlement);
	}

	/** 판매금 × 율/100 (반올림, 원 단위). */
	private static long fee(long sales, BigDecimal ratePct) {
		if (ratePct == null || ratePct.signum() == 0 || sales == 0) {
			return 0L;
		}
		return BigDecimal.valueOf(sales).multiply(ratePct).divide(HUNDRED, 0, RoundingMode.HALF_UP).longValueExact();
	}

	private SettlementSettingVO requireSetting() {
		SettlementSettingVO vo = settlementMapper.selectSetting();
		if (vo == null) {
			throw new BusinessException(SettlementErrorCode.SETTLEMENT_SETTING_NOT_FOUND);
		}
		return vo;
	}

	private void validateRound(String roundYm) {
		if (roundYm == null || !ROUND_YM.matcher(roundYm).matches()) {
			throw new BusinessException(SettlementErrorCode.SETTLEMENT_ROUND_INVALID);
		}
	}

	private void validateBasis(String basis) {
		if (basis == null || !ALLOWED_BASIS.contains(basis)) {
			throw new BusinessException(SettlementErrorCode.SETTLEMENT_SETTING_INVALID);
		}
	}

	private void validateCycle(String cycle) {
		if (cycle == null || !ALLOWED_CYCLE.contains(cycle)) {
			throw new BusinessException(SettlementErrorCode.SETTLEMENT_SETTING_INVALID);
		}
	}
}
