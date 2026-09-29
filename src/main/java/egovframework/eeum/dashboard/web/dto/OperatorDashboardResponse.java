package egovframework.eeum.dashboard.web.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 운영자 대시보드 응답(현재 더미 — 예약/결제/매출/정산 도메인 구축 시 실데이터로 교체).
 *
 * <p>금액은 KRW 원단위 정수. 증감률(deltaPct)은 전일/전주 대비 % 정수. 퍼블리싱 위젯과 1:1 매핑.
 */
@Schema(description = "운영자 대시보드")
public record OperatorDashboardResponse(

		@Schema(description = "상단 KPI 4종") Kpi kpi,
		@Schema(description = "매출 추이(바차트)") List<SalesTrendPoint> salesTrend,
		@Schema(description = "지역별 매출 비중") List<RegionShare> regionShare,
		@Schema(description = "이번달 정산 예정") Settlement settlement,
		@Schema(description = "파트너 현황") List<PartnerStatus> partners,
		@Schema(description = "가입 승인 대기") List<PendingApproval> pendingApprovals) {

	@Schema(description = "KPI 4종")
	public record Kpi(TodayReservation todayReservation, HouseSummary house, TodayPayment todayPayment,
			NewMember newMember) {
	}

	@Schema(description = "금일 예약")
	public record TodayReservation(int count, int deltaPct, int cancelCnt, int refundCnt) {
	}

	@Schema(description = "등록 고택")
	public record HouseSummary(int count, int delta, int partnerCnt, int roomCnt) {
	}

	@Schema(description = "금일 결제")
	public record TodayPayment(long amount, int count, int deltaPct, long cancelAmount) {
	}

	@Schema(description = "신규 가입 회원")
	public record NewMember(int count, int deltaPct, int totalCnt, double conversionPct) {
	}

	@Schema(description = "매출 추이 포인트")
	public record SalesTrendPoint(String label, long amount) {
	}

	@Schema(description = "지역별 비중")
	public record RegionShare(int rank, String name, long amount, int pct) {
	}

	@Schema(description = "정산 예정")
	public record Settlement(String dueDate, int dDay, long expectedAmount, long totalSales, long cardFee,
			long platformFee, long refundCancel) {
	}

	@Schema(description = "파트너 현황 행")
	public record PartnerStatus(Long prtnrSn, String name, String region, int roomCnt, int reservationCnt,
			int cancelCnt, long monthSales, String status) {
	}

	@Schema(description = "승인 대기 행")
	public record PendingApproval(Long prtnrSn, String name, String region, String brnoStatus, String requestedAt) {
	}
}
