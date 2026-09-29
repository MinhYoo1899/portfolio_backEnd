-- =============================================================================
-- V24 : 운영자 대행 상품등록 권한(HOUSE_PROXY_MNG) — 운영자가 파트너 대신 고택 등록
-- 사유(사용자 확정): 고령 파트너의 등록 부담 → 일반 운영자(STAFF) 이상이 파트너 선택 후 대행 등록.
--   소유자=선택 파트너(PRTNR_SN), 행위자=운영자(RGTR_SN). 파트너용 /houses/insert(HOUSE_MNG)와 분리(IDOR 차단).
-- 권한: HOUSE_PROXY_MNG → OPERATOR_STAFF + OPERATOR_SUPERVISOR (사용자: 일반 운영자 이상).
-- 명명: AUTH_MST/ROLE_AUTH. 감사 insert 수정일=등록일([[convention-mdfcndt-equals-regdt-on-insert]]).
-- =============================================================================

INSERT INTO AUTH_MST (AUTH_CD, AUTH_NM, USE_YN, REG_DT, MDFCN_DT)
SELECT 'HOUSE_PROXY_MNG', '상품(고택) 대행 등록 — 운영자가 파트너 대신 등록', 'Y', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
 WHERE NOT EXISTS (SELECT 1 FROM AUTH_MST WHERE AUTH_CD = 'HOUSE_PROXY_MNG' AND DEL_YN = 'N');

INSERT INTO ROLE_AUTH (ROLE_SN, AUTH_SN, REG_DT, MDFCN_DT)
SELECT R.ROLE_SN, A.AUTH_SN, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
  FROM ROLE_MST R
  JOIN AUTH_MST A ON A.AUTH_CD = 'HOUSE_PROXY_MNG'
 WHERE R.ROLE_CD IN ('OPERATOR_STAFF', 'OPERATOR_SUPERVISOR')
   AND R.DEL_YN = 'N' AND A.DEL_YN = 'N'
   AND NOT EXISTS (SELECT 1 FROM ROLE_AUTH RA
                    WHERE RA.ROLE_SN = R.ROLE_SN AND RA.AUTH_SN = A.AUTH_SN AND RA.DEL_YN = 'N');
