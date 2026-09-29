-- =============================================================================
-- V30 : 침구타입 COMM_CD 에 TWIN_BED(트윈 베드) 추가 — 퍼블 위저드 "트윈" 대응.
-- 기존 SINGLE_BED/DOUBLE_BED/QUEEN_BED/KING_BED/FLOOR_BEDDING(V26) 에 이어 SORT_SEQ=6.
-- 코드검증은 CodeService.list("BEDDING_TYPE") 동적조회라 시드 추가 즉시 허용(Java 변경 불필요).
-- 재실행 안전(NOT EXISTS). 시각 UTC_TIMESTAMP(6). dev 적용은 상위 세션.
-- =============================================================================

INSERT INTO COMM_CD (GROUP_CD, CD, CD_NM, SORT_SEQ, USE_YN, REG_DT, MDFCN_DT)
SELECT 'BEDDING_TYPE', 'TWIN_BED', '트윈 베드', 6, 'Y', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
 WHERE NOT EXISTS (SELECT 1 FROM COMM_CD WHERE GROUP_CD = 'BEDDING_TYPE' AND CD = 'TWIN_BED');
