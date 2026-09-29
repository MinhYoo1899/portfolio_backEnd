-- =============================================================================
-- V5 : 로그인 채널 자격 권한 (CHNL_LOGIN_*) + SUPER 역할
-- 요구(2026-06-12 확정): SPA 3종(USER/PARTNER/MASTER)별로 "이 계정은 이 사이트만 로그인"을
--       서버가 로그인/재발급 단계에서 강제. 슈퍼계정(전 채널)은 SUPER 역할 부여로만 허용.
-- codex 교차검토 반영: ① 기본 역할(MASTER 포함)에 3채널을 얹지 않음 — 모든 마스터가
--       슈퍼가 되는 것 방지, SUPER 는 별도 역할로 분리 ② 권한 조회는 역할 계층을 상속하지
--       않으므로 SUPER 는 USER_ROLE 직접 부여로만 동작 ③ 역할코드 MEMBER ↔ 채널코드 USER 매핑 명시.
-- 분류 주의: CHNL_LOGIN_* 는 "로그인 채널 자격"으로, 업무 API 권한(@PreAuthorize 대상)과
--       성격이 다름 — 업무 권한 매핑에 섞지 말 것. 채널코드 체계는 메뉴채널(MENU_CHNL)과 동일.
-- =============================================================================

-- ----------------------------------------------------------------------------
-- 로그인 채널 자격 권한 3종
-- ----------------------------------------------------------------------------
INSERT INTO AUTHRT (AUTHRT_CD, AUTHRT_NM, USE_YN, REG_DT) VALUES
    ('CHNL_LOGIN_USER',    '로그인 채널 자격: 사용자 SPA',  'Y', NOW(6)),
    ('CHNL_LOGIN_PARTNER', '로그인 채널 자격: 파트너 SPA',  'Y', NOW(6)),
    ('CHNL_LOGIN_MASTER',  '로그인 채널 자격: 마스터 SPA',  'Y', NOW(6));

-- ----------------------------------------------------------------------------
-- 기본 역할 1:1 매핑 (주의: 역할코드 MEMBER 가 채널코드 USER 에 대응)
-- ----------------------------------------------------------------------------
INSERT INTO ROLE_AUTHRT (ROLE_SN, AUTHRT_SN, REG_DT)
SELECT R.ROLE_SN, A.AUTHRT_SN, NOW(6)
  FROM ROLE R
  JOIN AUTHRT A
    ON (R.ROLE_CD = 'MEMBER'  AND A.AUTHRT_CD = 'CHNL_LOGIN_USER')
    OR (R.ROLE_CD = 'PARTNER' AND A.AUTHRT_CD = 'CHNL_LOGIN_PARTNER')
    OR (R.ROLE_CD = 'MASTER'  AND A.AUTHRT_CD = 'CHNL_LOGIN_MASTER')
 WHERE R.DEL_YN = 'N' AND A.DEL_YN = 'N';

-- ----------------------------------------------------------------------------
-- SUPER 역할 — 전 채널 로그인 자격만 보유 (업무 권한은 없음: 업무 권한은 계정이 가진
-- 다른 역할이 결정 — 로그인 자격과 기능 권한의 분리)
-- ----------------------------------------------------------------------------
INSERT INTO ROLE (ROLE_CD, ROLE_NM, UPPER_ROLE_SN, ROLE_STEP, SORT_SEQ, USE_YN, REG_DT) VALUES
    ('SUPER', '슈퍼(전 채널 로그인)', NULL, 1, 4, 'Y', NOW(6));

INSERT INTO ROLE_AUTHRT (ROLE_SN, AUTHRT_SN, REG_DT)
SELECT R.ROLE_SN, A.AUTHRT_SN, NOW(6)
  FROM ROLE R
 CROSS JOIN AUTHRT A
 WHERE R.ROLE_CD = 'SUPER'
   AND A.AUTHRT_CD IN ('CHNL_LOGIN_USER', 'CHNL_LOGIN_PARTNER', 'CHNL_LOGIN_MASTER')
   AND R.DEL_YN = 'N' AND A.DEL_YN = 'N';
