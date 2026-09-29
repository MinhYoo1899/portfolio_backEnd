-- =============================================================================
-- R__ : 개발 전용 — 운영자(OPERATOR) 계정 시드 (application-dev.properties 의 dev location 에서만 적용, 운영 미적용)
-- 멱등(idempotent): 이미 있으면 건너뜀.
-- 비밀번호 해시는 Flyway placeholder(devMasterEnpswd)로 주입 — 기본 EeumMaster1! (개발 공용, 운영 금지)
-- ※ 파일명·placeholder 의 master 는 이력상 유지(Flyway 가 파일 설명으로 추적) — 개념 명칭은 V7부터 OPERATOR
-- V8 반영: 역할 OPERATOR_SUPERVISOR/OPERATOR_STAFF 분화 — 수퍼바이저 시드는 동시 부여 불변식 준수,
--          일반직원 검증용 계정(operator-staff@eeum.dev, STAFF 만) 추가.
-- =============================================================================

-- ---------- 수퍼바이저(겸 전 채널 SUPER) : master@eeum.dev ----------
INSERT INTO USER_ACNT (EML_ADDR, ENPSWD, USER_TYPE_CD, LGN_TYPE, ACNT_STTS, USER_NM, NCNM, REG_DT)
SELECT 'master@eeum.dev',
       '${devMasterEnpswd}',
       'OPERATOR', 'EMAIL', 'ACTIVE', '개발운영자', 'devmaster', UTC_TIMESTAMP(6)
 WHERE NOT EXISTS (
       SELECT 1 FROM USER_ACNT WHERE EML_ADDR = 'master@eeum.dev' AND DEL_YN = 'N');

INSERT INTO OPERATOR_DTL_INFO (USER_SN, DEPT_NM, JBPS_NM, MFA_USE_YN, REG_DT)
SELECT U.USER_SN, '개발', '수퍼바이저', 'N', UTC_TIMESTAMP(6)
  FROM USER_ACNT U
 WHERE U.EML_ADDR = 'master@eeum.dev' AND U.DEL_YN = 'N'
   AND NOT EXISTS (SELECT 1 FROM OPERATOR_DTL_INFO M WHERE M.USER_SN = U.USER_SN);

-- 역할: SUPERVISOR + STAFF(동시 부여 불변식) + SUPER(전 채널 로그인 — 개발 편의)
INSERT INTO USER_ROLE (USER_SN, ROLE_SN, REG_DT)
SELECT U.USER_SN, R.ROLE_SN, UTC_TIMESTAMP(6)
  FROM USER_ACNT U
 CROSS JOIN ROLE_MST R
 WHERE U.EML_ADDR = 'master@eeum.dev' AND U.DEL_YN = 'N'
   AND R.ROLE_CD IN ('OPERATOR_SUPERVISOR', 'OPERATOR_STAFF', 'SUPER') AND R.DEL_YN = 'N'
   AND NOT EXISTS (
       SELECT 1 FROM USER_ROLE UR
        WHERE UR.USER_SN = U.USER_SN AND UR.ROLE_SN = R.ROLE_SN AND UR.DEL_YN = 'N');

-- ---------- 일반직원(권한 분리 검증용) : operator-staff@eeum.dev ----------
INSERT INTO USER_ACNT (EML_ADDR, ENPSWD, USER_TYPE_CD, LGN_TYPE, ACNT_STTS, USER_NM, NCNM, REG_DT)
SELECT 'operator-staff@eeum.dev',
       '${devMasterEnpswd}',
       'OPERATOR', 'EMAIL', 'ACTIVE', '개발직원', 'devstaff', UTC_TIMESTAMP(6)
 WHERE NOT EXISTS (
       SELECT 1 FROM USER_ACNT WHERE EML_ADDR = 'operator-staff@eeum.dev' AND DEL_YN = 'N');

INSERT INTO OPERATOR_DTL_INFO (USER_SN, DEPT_NM, JBPS_NM, MFA_USE_YN, REG_DT)
SELECT U.USER_SN, '개발', '일반직원', 'N', UTC_TIMESTAMP(6)
  FROM USER_ACNT U
 WHERE U.EML_ADDR = 'operator-staff@eeum.dev' AND U.DEL_YN = 'N'
   AND NOT EXISTS (SELECT 1 FROM OPERATOR_DTL_INFO M WHERE M.USER_SN = U.USER_SN);

INSERT INTO USER_ROLE (USER_SN, ROLE_SN, REG_DT)
SELECT U.USER_SN, R.ROLE_SN, UTC_TIMESTAMP(6)
  FROM USER_ACNT U
 CROSS JOIN ROLE_MST R
 WHERE U.EML_ADDR = 'operator-staff@eeum.dev' AND U.DEL_YN = 'N'
   AND R.ROLE_CD = 'OPERATOR_STAFF' AND R.DEL_YN = 'N'
   AND NOT EXISTS (
       SELECT 1 FROM USER_ROLE UR
        WHERE UR.USER_SN = U.USER_SN AND UR.ROLE_SN = R.ROLE_SN AND UR.DEL_YN = 'N');

-- ---------- 개발자 전용 수퍼바이저(요청 2026-06-26) : ymh@eeum.dev ----------
-- 운영자 콘솔(위저드 대행·회원관리 등) 전체 테스트용 개인 계정. 비번 = EeumMaster1!(dev 공용 해시).
INSERT INTO USER_ACNT (EML_ADDR, ENPSWD, USER_TYPE_CD, LGN_TYPE, ACNT_STTS, USER_NM, NCNM, REG_DT)
SELECT 'ymh@eeum.dev',
       '${devMasterEnpswd}',
       'OPERATOR', 'EMAIL', 'ACTIVE', '유민호(개발)', 'devymh', UTC_TIMESTAMP(6)
 WHERE NOT EXISTS (
       SELECT 1 FROM USER_ACNT WHERE EML_ADDR = 'ymh@eeum.dev' AND DEL_YN = 'N');

INSERT INTO OPERATOR_DTL_INFO (USER_SN, DEPT_NM, JBPS_NM, MFA_USE_YN, REG_DT)
SELECT U.USER_SN, '개발', '수퍼바이저', 'N', UTC_TIMESTAMP(6)
  FROM USER_ACNT U
 WHERE U.EML_ADDR = 'ymh@eeum.dev' AND U.DEL_YN = 'N'
   AND NOT EXISTS (SELECT 1 FROM OPERATOR_DTL_INFO M WHERE M.USER_SN = U.USER_SN);

-- 역할: SUPERVISOR + STAFF(동시 부여 불변식 준수)
INSERT INTO USER_ROLE (USER_SN, ROLE_SN, REG_DT)
SELECT U.USER_SN, R.ROLE_SN, UTC_TIMESTAMP(6)
  FROM USER_ACNT U
 CROSS JOIN ROLE_MST R
 WHERE U.EML_ADDR = 'ymh@eeum.dev' AND U.DEL_YN = 'N'
   AND R.ROLE_CD IN ('OPERATOR_SUPERVISOR', 'OPERATOR_STAFF') AND R.DEL_YN = 'N'
   AND NOT EXISTS (
       SELECT 1 FROM USER_ROLE UR
        WHERE UR.USER_SN = U.USER_SN AND UR.ROLE_SN = R.ROLE_SN AND UR.DEL_YN = 'N');
