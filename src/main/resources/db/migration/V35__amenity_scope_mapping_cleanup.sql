-- =============================================================================
-- V35 : 편의시설 scope 불일치 기존 매핑 정리 (codex 치명 — 조회는 되나 재저장 시 404 나던 오염 데이터)
-- 배경: V34에서 일부 항목의 적용범위(APPLY_SCOPE_CD)가 바뀜(예: 무료주차 ROOM→HOUSE).
--   그 이전에 들어간 매핑이 ROOM_AMENITY/HOUSE_AMENITY 에 남아, 표출 조회에는 보이지만
--   set 재저장 시 scope 검증(객실=ROOM/BOTH, 고택=HOUSE/BOTH)에 걸려 AMENITY_NOT_FOUND(404) 발생.
--   → 조회 쿼리에 scope 필터 추가(코드)와 함께, 이미 들어간 불일치 매핑을 여기서 soft-delete 로 정리.
-- 멱등성: 매핑 활성(RA/HA.DEL_YN='N')이면서 scope 불일치인 행만 DEL_YN='Y'(카탈로그 USE_YN 과는 무관). 재실행 시 영향 0(이미 정리됨). MDFR_SN 등 다른 컬럼은 건드리지 않음.
-- =============================================================================

-- 객실에 매핑된 고택전용(HOUSE) 편의시설 → 객실 scope(ROOM/BOTH) 아니면 해제
UPDATE ROOM_AMENITY RA
  JOIN AMENITY_MST A ON A.AMENITY_SN = RA.AMENITY_SN
   SET RA.DEL_YN = 'Y', RA.MDFCN_DT = UTC_TIMESTAMP(6)
 WHERE RA.DEL_YN = 'N'
   AND A.APPLY_SCOPE_CD NOT IN ('ROOM','BOTH');

-- 고택에 매핑된 객실전용(ROOM) 편의시설 → 고택 scope(HOUSE/BOTH) 아니면 해제
UPDATE HOUSE_AMENITY HA
  JOIN AMENITY_MST A ON A.AMENITY_SN = HA.AMENITY_SN
   SET HA.DEL_YN = 'Y', HA.MDFCN_DT = UTC_TIMESTAMP(6)
 WHERE HA.DEL_YN = 'N'
   AND A.APPLY_SCOPE_CD NOT IN ('HOUSE','BOTH');
