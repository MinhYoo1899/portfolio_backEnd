-- =============================================================================
-- V8 : 운영자 다단계 권한 1차 — 일반직원(STAFF)/수퍼바이저(SUPERVISOR) 분화
-- 원 기획 범위(킥오프 "다단계권한" 명시, D11 데이터 주도 RBAC 전제). 사용자 확정 2026-06-12.
-- codex 교차검토 반영: ① 역할 개명만이 아니라 권한 "재배치"까지(STAFF 에 처리권한 잔류 금지)
--   ② 조회/처리 분리 = PRTNR_VIEW 신설(목록·상세), PRTNR_APRV 는 승인·반려 처리 전용으로 축소
--   ③ SUPERVISOR 보유 계정 전원에 STAFF 동시 부여(동시 부여 불변식 — 상속 효과 대용)
--   ④ 운영자 메뉴의 필요권한은 VIEW 기준(메뉴=조회 노출, 버튼=처리 권한으로 프론트 분기)
-- 계층 컬럼(UPPER_ROLE_SN/ROLE_STEP)은 메타데이터 — 권한 조회는 직접 배정 역할만 평가(상속 미구현).
-- =============================================================================

-- ----------------------------------------------------------------------------
-- 1. 권한: 조회/처리 분리
-- ----------------------------------------------------------------------------
-- (NOT EXISTS 가드: 수동 수습·부분 재실행 대비 — codex 권고)
INSERT INTO AUTH_MST (AUTH_CD, AUTH_NM, USE_YN, REG_DT)
SELECT 'PRTNR_VIEW', '파트너 조회(목록/상세)', 'Y', NOW(6)
 WHERE NOT EXISTS (SELECT 1 FROM AUTH_MST WHERE AUTH_CD = 'PRTNR_VIEW' AND DEL_YN = 'N');

UPDATE AUTH_MST SET AUTH_NM = '파트너 승인 처리(승인/반려)', MDFCN_DT = NOW(6)
 WHERE AUTH_CD = 'PRTNR_APRV' AND DEL_YN = 'N';

-- ----------------------------------------------------------------------------
-- 2. 역할: 기존 OPERATOR → OPERATOR_SUPERVISOR 개명(역할 연결 보존) + OPERATOR_STAFF 신설
-- ----------------------------------------------------------------------------
UPDATE ROLE_MST SET ROLE_CD = 'OPERATOR_SUPERVISOR', ROLE_NM = '운영자 수퍼바이저', ROLE_STEP = 2, MDFCN_DT = NOW(6)
 WHERE ROLE_CD = 'OPERATOR' AND DEL_YN = 'N';

INSERT INTO ROLE_MST (ROLE_CD, ROLE_NM, UPPER_ROLE_SN, ROLE_STEP, SORT_SEQ, USE_YN, REG_DT)
SELECT 'OPERATOR_STAFF', '운영자 일반직원', NULL, 1, 5, 'Y', NOW(6)
 WHERE NOT EXISTS (SELECT 1 FROM ROLE_MST WHERE ROLE_CD = 'OPERATOR_STAFF' AND DEL_YN = 'N');

-- 계층 메타데이터: SUPERVISOR 의 상위지정 → STAFF (※ 상속 효과 없음 — 문서화된 메타데이터)
UPDATE ROLE_MST
   SET UPPER_ROLE_SN = (SELECT ROLE_SN FROM (SELECT ROLE_SN FROM ROLE_MST
                         WHERE ROLE_CD = 'OPERATOR_STAFF' AND DEL_YN = 'N') T),
       MDFCN_DT = NOW(6)
 WHERE ROLE_CD = 'OPERATOR_SUPERVISOR' AND DEL_YN = 'N';

-- ----------------------------------------------------------------------------
-- 3. 권한 재배치
-- ----------------------------------------------------------------------------
-- 3-1) STAFF = 운영자웹 로그인 자격 + 파트너 조회
INSERT INTO ROLE_AUTH (ROLE_SN, AUTH_SN, REG_DT)
SELECT R.ROLE_SN, A.AUTH_SN, NOW(6)
  FROM ROLE_MST R
  JOIN AUTH_MST A ON A.AUTH_CD IN ('CHNL_LOGIN_OPERATOR', 'PRTNR_VIEW') AND A.DEL_YN = 'N'
 WHERE R.ROLE_CD = 'OPERATOR_STAFF' AND R.DEL_YN = 'N'
   AND NOT EXISTS (SELECT 1 FROM ROLE_AUTH RA
                    WHERE RA.ROLE_SN = R.ROLE_SN AND RA.AUTH_SN = A.AUTH_SN AND RA.DEL_YN = 'N');

-- 3-2) SUPERVISOR 에서 채널 로그인 자격 제거(STAFF 로 이동 — STAFF 동시 부여가 자격을 공급.
--      누락 사고 시 해당 계정 로그인이 거부되어 즉시 드러남: fail-fast)
UPDATE ROLE_AUTH RA
  JOIN ROLE_MST R ON R.ROLE_SN = RA.ROLE_SN AND R.ROLE_CD = 'OPERATOR_SUPERVISOR' AND R.DEL_YN = 'N'
  JOIN AUTH_MST A ON A.AUTH_SN = RA.AUTH_SN AND A.AUTH_CD = 'CHNL_LOGIN_OPERATOR'
   SET RA.DEL_YN = 'Y', RA.MDFCN_DT = NOW(6)
 WHERE RA.DEL_YN = 'N';
-- (SUPERVISOR 의 PRTNR_APRV·ACNT_MNG 매핑은 유지 — 처리 권한 전담)

-- ----------------------------------------------------------------------------
-- 4. 동시 부여 불변식: 기존 SUPERVISOR 보유 계정 전원에 STAFF 역할 추가
-- ----------------------------------------------------------------------------
INSERT INTO USER_ROLE (USER_SN, ROLE_SN, REG_DT)
SELECT UR.USER_SN, ST.ROLE_SN, NOW(6)
  FROM USER_ROLE UR
  JOIN ROLE_MST SP ON SP.ROLE_SN = UR.ROLE_SN AND SP.ROLE_CD = 'OPERATOR_SUPERVISOR' AND SP.DEL_YN = 'N'
 CROSS JOIN (SELECT ROLE_SN FROM ROLE_MST WHERE ROLE_CD = 'OPERATOR_STAFF' AND DEL_YN = 'N') ST
 WHERE UR.DEL_YN = 'N'
   AND NOT EXISTS (SELECT 1 FROM USER_ROLE X
                    WHERE X.USER_SN = UR.USER_SN AND X.ROLE_SN = ST.ROLE_SN AND X.DEL_YN = 'N');

-- ----------------------------------------------------------------------------
-- 5. 운영자 메뉴 필요권한 = 조회(VIEW) 기준 (직원에게도 메뉴 노출 — 승인 버튼은 프론트가 PRTNR_APRV 로 분기)
-- ----------------------------------------------------------------------------
UPDATE MENU_INFO SET AUTH_CD = 'PRTNR_VIEW', MDFCN_DT = NOW(6)
 WHERE AUTH_CD = 'PRTNR_APRV' AND DEL_YN = 'N';
