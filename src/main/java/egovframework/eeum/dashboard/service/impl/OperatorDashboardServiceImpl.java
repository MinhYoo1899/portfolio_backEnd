package egovframework.eeum.dashboard.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;

import egovframework.eeum.dashboard.service.OperatorDashboardService;
import egovframework.eeum.dashboard.web.dto.OperatorDashboardResponse;
import egovframework.eeum.dashboard.web.dto.OperatorDashboardResponse.HouseSummary;
import egovframework.eeum.dashboard.web.dto.OperatorDashboardResponse.Kpi;
import egovframework.eeum.dashboard.web.dto.OperatorDashboardResponse.NewMember;
import egovframework.eeum.dashboard.web.dto.OperatorDashboardResponse.PartnerStatus;
import egovframework.eeum.dashboard.web.dto.OperatorDashboardResponse.PendingApproval;
import egovframework.eeum.dashboard.web.dto.OperatorDashboardResponse.RegionShare;
import egovframework.eeum.dashboard.web.dto.OperatorDashboardResponse.SalesTrendPoint;
import egovframework.eeum.dashboard.web.dto.OperatorDashboardResponse.Settlement;
import egovframework.eeum.dashboard.web.dto.OperatorDashboardResponse.TodayPayment;
import egovframework.eeum.dashboard.web.dto.OperatorDashboardResponse.TodayReservation;

/**
 * 운영자 대시보드 구현 — 더미 데이터(퍼블리싱 위젯 1:1). 예약/결제/정산 도메인 구축 시 실집계로 교체.
 */
@Service
public class OperatorDashboardServiceImpl implements OperatorDashboardService {

	@Override
	public OperatorDashboardResponse getDashboard() {
		Kpi kpi = new Kpi(
				new TodayReservation(142, 8, 6, 2),
				new HouseSummary(48, 2, 31, 45),
				new TodayPayment(28_400_000L, 138, 8, -840_000L),
				new NewMember(24, 8, 8_420, 3.2));

		List<SalesTrendPoint> salesTrend = List.of(
				new SalesTrendPoint("4/28", 21_600_000L),
				new SalesTrendPoint("5/5", 23_400_000L),
				new SalesTrendPoint("5/12", 18_200_000L),
				new SalesTrendPoint("5/19", 24_800_000L),
				new SalesTrendPoint("5/26", 24_200_000L),
				new SalesTrendPoint("6/2", 28_400_000L));

		List<RegionShare> regionShare = List.of(
				new RegionShare(1, "경주", 18_400_000L, 27),
				new RegionShare(2, "안동", 13_600_000L, 20),
				new RegionShare(3, "대구", 10_200_000L, 15),
				new RegionShare(4, "강릉", 6_800_000L, 10));

		Settlement settlement = new Settlement("2026-06-05", 8, 48_200_000L, 62_400_000L,
				-1_872_000L, -9_360_000L, -2_968_000L);

		List<PartnerStatus> partners = List.of(
				new PartnerStatus(1L, "고운당 한옥", "경주", 3, 28, 3, 21_400_000L, "ACTIVE"),
				new PartnerStatus(2L, "솔향 고택", "안동", 5, 28, 3, 21_400_000L, "ACTIVE"),
				new PartnerStatus(3L, "달빛채 스테이", "전주", 4, 28, 3, 21_400_000L, "ACTIVE"));

		List<PendingApproval> pendingApprovals = List.of(
				new PendingApproval(101L, "청학재 종가", "안동", "VERIFIED", "2026-06-23T09:00:00Z"),
				new PendingApproval(102L, "운서헌", "경주", "REQUESTED", "2026-06-22T09:00:00Z"),
				new PendingApproval(103L, "매월당", "강릉", "VERIFIED", "2026-06-21T09:00:00Z"));

		return new OperatorDashboardResponse(kpi, salesTrend, regionShare, settlement, partners, pendingApprovals);
	}
}
