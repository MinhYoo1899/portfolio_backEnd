-- =============================================================================
-- V33 : AMENITY_MST '에어컨'(ICON_KEY='aircon') 비활성화 — 냉난방으로 일원화
-- 사유: V32 에서 객실 냉난방시설(ROOM_CLIM_FCLTY, COOLING_TYPE.AIRCON 등)을 별도 개념으로 도입.
--   기존 편의시설 '에어컨'과 개념이 중복되므로 마스터 선택목록에서 숨긴다(USE_YN='N').
--   기존 HOUSE_AMENITY/ROOM_AMENITY 매핑 데이터는 그대로 보존(삭제하지 않음 — 과거 선택 이력 유지),
--   신규 선택만 차단된다(USE_YN='N' 이면 노출/선택 불가).
-- 타임존: UTC_TIMESTAMP(6). 수정일만 갱신(등록일 불변).
-- =============================================================================

UPDATE AMENITY_MST
   SET USE_YN = 'N',
       MDFCN_DT = UTC_TIMESTAMP(6)
 WHERE ICON_KEY = 'aircon'
   AND USE_YN = 'Y';
