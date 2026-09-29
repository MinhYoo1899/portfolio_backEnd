-- =============================================================================
-- V43 : 고택 정기휴관 메타(HOUSE) — 상품관리 갭 C(화면정의서 상품 §1-1 closedWeekday/공휴일/명절 휴무)
-- 설계(2026-07-08, 상품관리 백엔드 지원):
--   위저드 3단계 "정기 휴관 및 점검일" — 매주 휴관 요일 1개(선택) + 공휴일 휴무 + 명절 휴무 플래그.
--   이는 House 단위 "정기휴관 설정" 메타이며, 실제 판매일의 마감(availability/예약 가드)은 종전대로
--   ROOM_CALENDAR 행(CLOSED_YN)으로 materialize 한다(프론트가 규칙→날짜 펼쳐 기존 마감 API 호출).
--   요일코드 = MON/TUE/WED/THU/FRI/SAT/SUN 중 1개 또는 NULL(서비스 검증). 플래그는 Y/N.
--   저장 = POST /operator/houses/regular-closure/set(전체 교체, HOUSE_PROXY_MNG). 조회 = 고택 상세.
-- =============================================================================

ALTER TABLE HOUSE
    ADD COLUMN CLOSED_WKDAY_CD        VARCHAR(3) NULL              COMMENT '매주 휴관 요일(MON~SUN, 선택 1개)'   AFTER PEAK_RATE_AMT,
    ADD COLUMN HOLIDAY_CLOSED_YN      CHAR(1) NOT NULL DEFAULT 'N' COMMENT '공휴일 휴무 여부'                    AFTER CLOSED_WKDAY_CD,
    ADD COLUMN LUNAR_HOLIDAY_CLOSED_YN CHAR(1) NOT NULL DEFAULT 'N' COMMENT '명절 휴무 여부'                     AFTER HOLIDAY_CLOSED_YN,
    ADD CONSTRAINT CK_HOUSE_HOLIDAY_CLOSED       CHECK (HOLIDAY_CLOSED_YN IN ('Y','N')),
    ADD CONSTRAINT CK_HOUSE_LUNAR_HOLIDAY_CLOSED CHECK (LUNAR_HOLIDAY_CLOSED_YN IN ('Y','N'));
