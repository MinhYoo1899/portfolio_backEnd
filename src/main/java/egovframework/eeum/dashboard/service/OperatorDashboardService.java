package egovframework.eeum.dashboard.service;

import egovframework.eeum.dashboard.web.dto.OperatorDashboardResponse;

/**
 * 운영자 대시보드 서비스 — 현재 더미 데이터(예약/결제/매출/정산 도메인 구축 시 실데이터 교체).
 */
public interface OperatorDashboardService {

	OperatorDashboardResponse getDashboard();
}
