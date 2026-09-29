-- =============================================================================
-- V41 : 고택 일자별 대표요금 4구간(HOUSE) — 상품관리 갭 A(화면정의서 상품 §4 "일자별 비용")
-- 설계(2026-07-08, 상품관리 백엔드 지원):
--   PR-06 상세보기 "일자별 비용"(주중/금/토/성수기·공휴일)은 House 단위 대표/참고 요금으로
--   객실 캘린더(ROOM_CALENDAR) 판매가와 별도 저장한다(집계 아님 — 화면정의서 §4 확정).
--   전부 선택(NULL 허용), KRW 정수, 0 이상. 위저드 3단계에서 운영자가 별도 입력.
--   저장 = POST /operator/houses/daily-rate/set(전체 교체, HOUSE_PROXY_MNG). 조회 = 고택 상세.
-- =============================================================================

ALTER TABLE HOUSE
    ADD COLUMN WEEKDAY_RATE_AMT  INT NULL COMMENT '주중(일~목) 대표요금(KRW, 선택)'      AFTER INFANT_ADD_CHRG_AMT,
    ADD COLUMN FRIDAY_RATE_AMT   INT NULL COMMENT '금요일 대표요금(KRW, 선택)'           AFTER WEEKDAY_RATE_AMT,
    ADD COLUMN SATURDAY_RATE_AMT INT NULL COMMENT '주말(토) 대표요금(KRW, 선택)'         AFTER FRIDAY_RATE_AMT,
    ADD COLUMN PEAK_RATE_AMT     INT NULL COMMENT '성수기·공휴일 대표요금(KRW, 선택)'    AFTER SATURDAY_RATE_AMT,
    ADD CONSTRAINT CK_HOUSE_WEEKDAY_RATE  CHECK (WEEKDAY_RATE_AMT  IS NULL OR WEEKDAY_RATE_AMT  >= 0),
    ADD CONSTRAINT CK_HOUSE_FRIDAY_RATE   CHECK (FRIDAY_RATE_AMT   IS NULL OR FRIDAY_RATE_AMT   >= 0),
    ADD CONSTRAINT CK_HOUSE_SATURDAY_RATE CHECK (SATURDAY_RATE_AMT IS NULL OR SATURDAY_RATE_AMT >= 0),
    ADD CONSTRAINT CK_HOUSE_PEAK_RATE     CHECK (PEAK_RATE_AMT     IS NULL OR PEAK_RATE_AMT     >= 0);
