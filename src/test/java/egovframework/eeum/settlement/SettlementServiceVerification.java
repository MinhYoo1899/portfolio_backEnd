package egovframework.eeum.settlement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import egovframework.eeum.auth.vo.AuthUser;
import egovframework.eeum.base.exception.BusinessException;
import egovframework.eeum.settlement.exception.SettlementErrorCode;
import egovframework.eeum.settlement.service.settlement.dao.SettlementMapper;
import egovframework.eeum.settlement.service.settlement.impl.SettlementServiceImpl;
import egovframework.eeum.settlement.vo.PartnerSettlementRowVO;
import egovframework.eeum.settlement.vo.PartnerSettlementVO;
import egovframework.eeum.settlement.vo.SettlementSettingVO;
import egovframework.eeum.settlement.web.dto.PartnerSettlementExecuteRequest;
import egovframework.eeum.settlement.web.dto.PartnerSettlementResponse;
import egovframework.eeum.settlement.web.dto.SettlementSettingResponse;
import egovframework.eeum.settlement.web.dto.SettlementSettingUpdateRequest;

/**
 * 정산 서비스 검증 — 설정 조회/저장/초기화 validation + 파트너 집계·수수료 산출·실행(중복/대상없음) 규칙.
 */
class SettlementServiceVerification {

	private final SettlementMapper settlementMapper = mock(SettlementMapper.class);
	private final SettlementServiceImpl service = new SettlementServiceImpl(settlementMapper,
			mock(egovframework.eeum.notification.service.notification.NotificationEmitter.class));

	private static AuthUser user() {
		return new AuthUser(9L, "op@eeum.dev", "운영자", "OPERATOR", "ACTIVE", List.of("SETTLEMENT_MNG"));
	}

	private SettlementSettingVO setting() {
		SettlementSettingVO vo = new SettlementSettingVO();
		vo.setSettlementSettingSn(1L);
		vo.setPlatformFeeRate(new BigDecimal("15.00"));
		vo.setCardFeeRate(new BigDecimal("3.00"));
		vo.setVatRate(new BigDecimal("10.00"));
		vo.setSettlementBasisCd("CHECKOUT_DATE");
		vo.setDepositDueDays(5);
		vo.setSettlementCycleCd("MONTHLY");
		vo.setMinSettlementAmt(10000);
		vo.setAutoInvoiceYn("Y");
		vo.setTaxInvoiceYn("Y");
		return vo;
	}

	private SettlementSettingUpdateRequest req(String basis, String cycle) {
		return new SettlementSettingUpdateRequest(new BigDecimal("15.00"), new BigDecimal("3.00"),
				new BigDecimal("10.00"), basis, 5, cycle, 10000, true, true);
	}

	@Test
	@DisplayName("설정 조회 — 없으면 SETTLEMENT_SETTING_NOT_FOUND")
	void get_notFound() {
		when(settlementMapper.selectSetting()).thenReturn(null);
		BusinessException e = assertThrows(() -> service.getSettings());
		assertEquals(SettlementErrorCode.SETTLEMENT_SETTING_NOT_FOUND, e.getErrorCode());
	}

	@Test
	@DisplayName("설정 조회 성공")
	void get_success() {
		when(settlementMapper.selectSetting()).thenReturn(setting());
		SettlementSettingResponse res = service.getSettings();
		assertEquals(new BigDecimal("15.00"), res.platformFeeRate());
		assertEquals("CHECKOUT_DATE", res.settlementBasisCd());
		assertEquals(true, res.autoInvoice());
	}

	@Test
	@DisplayName("저장 — 정산기준 미허용값 → SETTLEMENT_SETTING_INVALID, 매퍼 미진입")
	void update_invalidBasis() {
		BusinessException e = assertThrows(() -> service.updateSettings(req("BOGUS", "MONTHLY"), user()));
		assertEquals(SettlementErrorCode.SETTLEMENT_SETTING_INVALID, e.getErrorCode());
		verify(settlementMapper, never()).updateSetting(any(), any(), any(), any(), anyInt(), any(), anyInt(),
				any(), any(), anyLong());
	}

	@Test
	@DisplayName("저장 — 정산주기 미허용값 → SETTLEMENT_SETTING_INVALID")
	void update_invalidCycle() {
		BusinessException e = assertThrows(() -> service.updateSettings(req("CHECKOUT_DATE", "YEARLY"), user()));
		assertEquals(SettlementErrorCode.SETTLEMENT_SETTING_INVALID, e.getErrorCode());
	}

	@Test
	@DisplayName("저장 성공 — updateSetting 위임(Y/N 변환) + 재조회")
	void update_success() {
		when(settlementMapper.updateSetting(any(), any(), any(), eq("CHECKOUT_DATE"), eq(5), eq("MONTHLY"),
				eq(10000), eq("Y"), eq("Y"), eq(9L))).thenReturn(1);
		when(settlementMapper.selectSetting()).thenReturn(setting());
		service.updateSettings(req("CHECKOUT_DATE", "MONTHLY"), user());
		verify(settlementMapper).updateSetting(any(), any(), any(), eq("CHECKOUT_DATE"), eq(5), eq("MONTHLY"),
				eq(10000), eq("Y"), eq("Y"), eq(9L));
	}

	@Test
	@DisplayName("초기화 — 서버 기본값(15/3/10·CHECKOUT_DATE·5·MONTHLY·10000·Y·Y)으로 저장")
	void reset_defaults() {
		when(settlementMapper.updateSetting(any(), any(), any(), any(), anyInt(), any(), anyInt(), any(), any(),
				anyLong())).thenReturn(1);
		when(settlementMapper.selectSetting()).thenReturn(setting());
		service.resetSettings(user());
		verify(settlementMapper).updateSetting(BigDecimal.valueOf(15), BigDecimal.valueOf(3), BigDecimal.valueOf(10),
				"CHECKOUT_DATE", 5, "MONTHLY", 10000, "Y", "Y", 9L);
	}

	@Test
	@DisplayName("파트너 목록 — 회차 형식 오류 → SETTLEMENT_ROUND_INVALID")
	void list_invalidRound() {
		BusinessException e = assertThrows(() -> service.listPartnerSettlements("2026-7"));
		assertEquals(SettlementErrorCode.SETTLEMENT_ROUND_INVALID, e.getErrorCode());
	}

	@Test
	@DisplayName("파트너 목록 — 수수료 산출(판매 1,000,000 · 카드3%·플랫폼15% → 30,000/150,000/820,000), 상태 WAITING")
	void list_computesFees() {
		when(settlementMapper.selectSetting()).thenReturn(setting());
		PartnerSettlementRowVO row = new PartnerSettlementRowVO();
		row.setPrtnrSn(7L);
		row.setPrtnrConm("한옥마을");
		row.setTotalSalesAmt(1_000_000L);
		when(settlementMapper.selectPartnerSettlementRows("2026-07")).thenReturn(List.of(row));

		List<PartnerSettlementResponse> list = service.listPartnerSettlements("2026-07");
		assertEquals(1, list.size());
		PartnerSettlementResponse r = list.get(0);
		assertEquals(30_000L, r.cardFeeAmt());
		assertEquals(150_000L, r.platformFeeAmt());
		assertEquals(820_000L, r.settlementAmt());
		assertEquals("WAITING", r.statusCd());
		assertNull(r.executedAt());
	}

	@Test
	@DisplayName("실행 — 이미 실행됨 → SETTLEMENT_ALREADY_DONE")
	void execute_alreadyDone() {
		when(settlementMapper.countExecuted("2026-07", 7L)).thenReturn(1);
		BusinessException e = assertThrows(
				() -> service.executePartnerSettlement(new PartnerSettlementExecuteRequest("2026-07", 7L), user()));
		assertEquals(SettlementErrorCode.SETTLEMENT_ALREADY_DONE, e.getErrorCode());
		verify(settlementMapper, never()).insertPartnerSettlement(any());
	}

	@Test
	@DisplayName("실행 — 판매 집계 없음 → SETTLEMENT_TARGET_NOT_FOUND")
	void execute_targetNotFound() {
		when(settlementMapper.countExecuted("2026-07", 7L)).thenReturn(0);
		when(settlementMapper.selectPartnerSalesForRound("2026-07", 7L)).thenReturn(0L);
		BusinessException e = assertThrows(
				() -> service.executePartnerSettlement(new PartnerSettlementExecuteRequest("2026-07", 7L), user()));
		assertEquals(SettlementErrorCode.SETTLEMENT_TARGET_NOT_FOUND, e.getErrorCode());
		verify(settlementMapper, never()).insertPartnerSettlement(any());
	}

	@Test
	@DisplayName("실행 성공 — 스냅샷 기록 후 COMPLETED 응답")
	void execute_success() {
		when(settlementMapper.countExecuted("2026-07", 7L)).thenReturn(0);
		when(settlementMapper.selectPartnerSalesForRound("2026-07", 7L)).thenReturn(1_000_000L);
		when(settlementMapper.selectSetting()).thenReturn(setting());

		PartnerSettlementRowVO done = new PartnerSettlementRowVO();
		done.setPrtnrSn(7L);
		done.setPrtnrConm("한옥마을");
		done.setTotalSalesAmt(1_000_000L);
		done.setExecutedSn(100L);
		done.setExecutedAt(LocalDateTime.of(2026, 7, 8, 1, 0));
		when(settlementMapper.selectPartnerSettlementRows("2026-07")).thenReturn(List.of(done));

		PartnerSettlementResponse res = service.executePartnerSettlement(
				new PartnerSettlementExecuteRequest("2026-07", 7L), user());

		assertEquals("COMPLETED", res.statusCd());
		assertEquals(820_000L, res.settlementAmt());

		org.mockito.ArgumentCaptor<PartnerSettlementVO> cap =
				org.mockito.ArgumentCaptor.forClass(PartnerSettlementVO.class);
		verify(settlementMapper).insertPartnerSettlement(cap.capture());
		PartnerSettlementVO saved = cap.getValue();
		assertEquals(1_000_000, saved.getTotalSalesAmt());
		assertEquals(30_000, saved.getCardFeeAmt());
		assertEquals(150_000, saved.getPlatformFeeAmt());
		assertEquals(820_000, saved.getSettlementAmt());
		assertEquals(9L, saved.getRgtrSn());
	}

	// ------------------------------------------------------------------

	private static BusinessException assertThrows(org.junit.jupiter.api.function.Executable exec) {
		return org.junit.jupiter.api.Assertions.assertThrows(BusinessException.class, exec);
	}
}
