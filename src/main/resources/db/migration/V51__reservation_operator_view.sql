-- =============================================================================
-- V51 : 예약 운영자 콘솔 조회 권한(RESERVATION_VIEW) — 신규 테이블 없음(기존 RESERVATION/ITEM/GUEST/PAYMENT/HOUSE 조회)
-- 설계(2026-07-08, 화면정의서 예약관리 §1/§2):
--   운영자 예약 콘솔은 조회 전용(목록/상세/검색). 기존 예약·결제·게스트·고택 조인으로 구성.
--   ★스키마에 없는 필드(결제수단·예약경로 이음/제휴·환불추적·투숙상태 세부)는 응답에서 null/unsupported로 내려감(결제 도메인 성장 시 채움).
--   게스트 전화는 암호화 PII → 마스킹 노출(평문 금지). 상태는 예약상태코드(reservationSttsCd) SSOT + 표시 라벨.
--   권한 RESERVATION_VIEW(STAFF↑, CONTENT 패턴). 환불/취소 처리(mutation)는 후속(PG 스텁·상태전이 별도 슬라이스).
-- =============================================================================

INSERT INTO AUTH_MST (AUTH_CD, AUTH_NM, USE_YN, REG_DT)
SELECT 'RESERVATION_VIEW', '예약 조회(운영자 콘솔 — 전 판매자)', 'Y', UTC_TIMESTAMP(6)
 WHERE NOT EXISTS (SELECT 1 FROM AUTH_MST WHERE AUTH_CD = 'RESERVATION_VIEW' AND DEL_YN = 'N');

INSERT INTO ROLE_AUTH (ROLE_SN, AUTH_SN, REG_DT)
SELECT R.ROLE_SN, A.AUTH_SN, UTC_TIMESTAMP(6)
  FROM ROLE_MST R
  JOIN AUTH_MST A
    ON A.AUTH_CD = 'RESERVATION_VIEW'
   AND R.ROLE_CD IN ('OPERATOR_STAFF', 'OPERATOR_SUPERVISOR')
 WHERE R.DEL_YN = 'N' AND A.DEL_YN = 'N'
   AND NOT EXISTS (SELECT 1 FROM ROLE_AUTH RA
                    WHERE RA.ROLE_SN = R.ROLE_SN AND RA.AUTH_SN = A.AUTH_SN AND RA.DEL_YN = 'N');
