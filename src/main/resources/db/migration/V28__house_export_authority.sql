-- =============================================================================
-- V28 : 상품목록 엑셀 내보내기 권한(HOUSE_EXPORT) — 운영자 대량 반출 전용(목록조회 HOUSE_VIEW 와 분리)
-- 사유(codex 교차검토): 대량 PII/이미지 반출은 단순 조회보다 높은 거버넌스 → 별도 권한으로 감사·통제.
-- 권한: HOUSE_EXPORT → OPERATOR_STAFF + OPERATOR_SUPERVISOR (목록을 보는 운영자가 내보낼 수 있게).
-- DB 변경: AUTH_MST / ROLE_AUTH. 재실행 안전(NOT EXISTS 가드). dev 적용은 상위 세션이 수행.
-- 명명: 감사 insert 수정일=등록일([[convention-mdfcndt-equals-regdt-on-insert]]).
-- =============================================================================

INSERT INTO AUTH_MST (AUTH_CD, AUTH_NM, USE_YN, REG_DT, MDFCN_DT)
SELECT 'HOUSE_EXPORT', '상품목록 엑셀 내보내기(운영자)', 'Y', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
 WHERE NOT EXISTS (SELECT 1 FROM AUTH_MST WHERE AUTH_CD = 'HOUSE_EXPORT' AND DEL_YN = 'N');

INSERT INTO ROLE_AUTH (ROLE_SN, AUTH_SN, REG_DT, MDFCN_DT)
SELECT R.ROLE_SN, A.AUTH_SN, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
  FROM ROLE_MST R
  JOIN AUTH_MST A ON A.AUTH_CD = 'HOUSE_EXPORT'
 WHERE R.ROLE_CD IN ('OPERATOR_STAFF', 'OPERATOR_SUPERVISOR')
   AND R.DEL_YN = 'N' AND A.DEL_YN = 'N'
   AND NOT EXISTS (SELECT 1 FROM ROLE_AUTH RA
                    WHERE RA.ROLE_SN = R.ROLE_SN AND RA.AUTH_SN = A.AUTH_SN AND RA.DEL_YN = 'N');
