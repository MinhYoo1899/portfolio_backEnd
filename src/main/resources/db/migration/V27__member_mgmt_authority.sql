-- =============================================================================
-- V27 : 운영자 콘솔 일반회원 관리 권한 (MEMBER_VIEW 조회 / MEMBER_MNG 상태관리)
-- 대상 = 일반회원(USER_ACNT.USER_TYPE_CD='MEMBER'). 실제 방어선은 API @PreAuthorize.
-- 역할매핑: STAFF=조회 / SUPERVISOR=조회+상태관리 (HOUSE_VIEW/HOUSE_MODER 패턴 미러).
-- 메뉴 '회원 관리'(/member)는 V25에 이미 시드(AUTH_CD=NULL 전체노출 — 메뉴 게이팅은 후속 정책).
-- 시각=UTC_TIMESTAMP(6). 재실행 안전(NOT EXISTS 가드).
-- =============================================================================

INSERT INTO AUTH_MST (AUTH_CD, AUTH_NM, USE_YN, REG_DT)
SELECT 'MEMBER_VIEW', '회원 조회(운영자 — 목록/상세, PII 접근 감사)', 'Y', UTC_TIMESTAMP(6)
 WHERE NOT EXISTS (SELECT 1 FROM AUTH_MST WHERE AUTH_CD = 'MEMBER_VIEW' AND DEL_YN = 'N');

INSERT INTO AUTH_MST (AUTH_CD, AUTH_NM, USE_YN, REG_DT)
SELECT 'MEMBER_MNG', '회원 상태관리(운영자 — 정지/해제/탈퇴처리)', 'Y', UTC_TIMESTAMP(6)
 WHERE NOT EXISTS (SELECT 1 FROM AUTH_MST WHERE AUTH_CD = 'MEMBER_MNG' AND DEL_YN = 'N');

INSERT INTO ROLE_AUTH (ROLE_SN, AUTH_SN, REG_DT)
SELECT R.ROLE_SN, A.AUTH_SN, UTC_TIMESTAMP(6)
  FROM ROLE_MST R
  JOIN AUTH_MST A
    ON (R.ROLE_CD = 'OPERATOR_STAFF'      AND A.AUTH_CD = 'MEMBER_VIEW')
    OR (R.ROLE_CD = 'OPERATOR_SUPERVISOR' AND A.AUTH_CD IN ('MEMBER_VIEW','MEMBER_MNG'))
 WHERE R.DEL_YN = 'N' AND A.DEL_YN = 'N'
   AND NOT EXISTS (SELECT 1 FROM ROLE_AUTH RA
                    WHERE RA.ROLE_SN = R.ROLE_SN AND RA.AUTH_SN = A.AUTH_SN AND RA.DEL_YN = 'N');
