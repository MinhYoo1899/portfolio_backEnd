-- =============================================================================
-- V29 : 고택 정책 2필드 추가 — 레이트 체크아웃 시각 + 예약 공지/주의사항
-- DB 변경: HOUSE 컬럼 2개(LATE_CHKOUT_TIME TIME, NOTICE_CN TEXT). dev 적용은 상위 세션.
-- 기존 정책필드(CHKOUT_TIME/PBTRNSP_GUIDE_CN) 패턴 미러 — 시각=TIME(현지 wall-clock), 본문=TEXT.
-- 명명: UPPER_SNAKE.
-- =============================================================================
ALTER TABLE HOUSE
  ADD COLUMN LATE_CHKOUT_TIME TIME NULL COMMENT '레이트 체크아웃 시각(현지 wall-clock)',
  ADD COLUMN NOTICE_CN        TEXT NULL COMMENT '예약 공지/주의사항';
