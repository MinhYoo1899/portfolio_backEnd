-- =============================================================================
-- V37 : 기존 HOUSE 시도지역코드(SIDO_REGION_CD) 백필 — ADDR prefix ↔ REGION 시도명 매칭 (데이터 마이그레이션)
-- 배경: 등록/수정 DTO에 지역 입력이 없던 기간(V19~) 앱 등록분은 SIDO_REGION_CD 가 NULL.
--   V37(=지역 저장 기반 배포)부터 신규는 저장되나, 기존 NULL 은 지역필터에서 누락되므로 백필한다.
-- 매칭: 활성 시도(REGION DEPTH=1, USE_YN='Y', DEL_YN='N') 의 공식명(REGION_NM) 또는 짧은명(REGION_SHORT_NM)이
--   ADDR 접두인 고택. 시도 짧은명은 상호 접두 중복이 없어(서울/부산/…/제주) 고택당 최대 1개 시도만 매칭 → UPDATE JOIN 안전.
-- 범위/한계(★보고 필수):
--   - 시도(SIDO)만 백필. 시군구(SIGUNGU)는 ADDR 상 명칭 중복(중구/동구/서구 등)으로 자동매칭 위험 → 미백필(NULL 유지, 후속 수동/편집).
--   - ADDR 이 시도명으로 시작하지 않거나(예: 구주소·오타·NULL) 매칭 실패한 행은 SIDO_REGION_CD NULL 유지 → 지역필터에서 여전히 제외.
--   - 지역필터 기능은 항상 지원(unsupported.regionFilter=false, 필터 WHERE 구현됨). 미매칭 잔존 건수는 regionUnmappedCount(미매핑 데이터 개수, UX 안내용)로 노출 — 드롭다운 비활성 조건 아님.
-- 비파괴: 기존 값(SIDO_REGION_CD IS NOT NULL) 미변경, NULL 만 채움. 재실행 안전(NULL 조건). MDFCN_DT 미변경(시스템 보정 — "최근 수정" 오염 방지).
-- FK: 채우는 REGION_CD 는 REGION 시도행 → FK_HOUSE_SIDO_REGION 충족. SIGUNGU NULL 유지로 CK_HOUSE_REGION_HIER 충족.
-- =============================================================================

UPDATE HOUSE H
  JOIN REGION R
    ON R.REGION_DEPTH = 1 AND R.USE_YN = 'Y' AND R.DEL_YN = 'N'
   AND (H.ADDR LIKE CONCAT(R.REGION_NM, '%') OR H.ADDR LIKE CONCAT(R.REGION_SHORT_NM, '%'))
   SET H.SIDO_REGION_CD = R.REGION_CD
 WHERE H.SIDO_REGION_CD IS NULL
   AND H.DEL_YN = 'N'
   AND H.ADDR IS NOT NULL;
