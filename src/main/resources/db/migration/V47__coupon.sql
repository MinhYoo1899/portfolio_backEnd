-- =============================================================================
-- V47 : 쿠폰(COUPON) + 할인유형 공통코드(COUPON_DISCOUNT_TYPE) + 쿠폰 권한(COUPON_VIEW/COUPON_MNG)
-- 설계(2026-07-08, 화면정의서 쿠폰 §1/§2/§5 확정값):
--   운영자 쿠폰 '정의' CRUD. status(UPCOMING/ACTIVE/ENDED)=저장 안 함·조회 시 (사용기간 vs KST 오늘) 산출.
--   할인유형 RATE(정률%)/FLAT(정액원). 수취인 targetType=ALL(전체회원, 등급/세그먼트는 회원도메인 확장 시).
--   발급수/사용수=회원 발급·사용 플로우 전까지 0. 삭제=soft. 권한 COUPON_VIEW/COUPON_MNG(STAFF↑, CONTENT 패턴 미러).
--   명명·감사5·CK·utf8mb4_0900. seed 재실행 안전(NOT EXISTS).
-- =============================================================================

-- 1) 쿠폰 테이블 ---------------------------------------------------------------
CREATE TABLE COUPON (
    COUPON_SN         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '쿠폰일련번호',
    TITLE             VARCHAR(200) NOT NULL                COMMENT '제목',
    CONTENT           VARCHAR(500) NOT NULL                COMMENT '내용',
    DISCOUNT_TYPE_CD  VARCHAR(20)  NOT NULL                COMMENT '할인유형(COMM_CD.GROUP_CD=COUPON_DISCOUNT_TYPE: RATE/FLAT)',
    DISCOUNT_VAL      INT          NOT NULL                COMMENT '할인값(RATE=%, FLAT=원)',
    TARGET_TYPE_CD    VARCHAR(50)  NOT NULL DEFAULT 'ALL'  COMMENT '수취인(현 단계 ALL=전체회원)',
    USAGE_BGN_DATE    DATE         NOT NULL                COMMENT '사용시작일(KST)',
    USAGE_END_DATE    DATE         NOT NULL                COMMENT '사용종료일(KST)',
    ISSUED_CNT        INT          NOT NULL DEFAULT 0      COMMENT '발급수(읽기전용)',
    USED_CNT          INT          NOT NULL DEFAULT 0      COMMENT '사용수(읽기전용)',
    REG_DT            DATETIME(6)  NOT NULL                COMMENT '등록일시(=발행일)',
    RGTR_SN           BIGINT       NULL                    COMMENT '등록자(사용자일련번호)',
    MDFCN_DT          DATETIME(6)  NULL                    COMMENT '수정일시',
    MDFR_SN           BIGINT       NULL                    COMMENT '수정자(사용자일련번호)',
    DEL_YN            CHAR(1)      NOT NULL DEFAULT 'N'    COMMENT '삭제여부(soft delete)',
    PRIMARY KEY (COUPON_SN),
    KEY IX_COUPON_USAGE (USAGE_BGN_DATE, USAGE_END_DATE),
    CONSTRAINT CK_COUPON_DEL         CHECK (DEL_YN IN ('Y','N')),
    CONSTRAINT CK_COUPON_DISCOUNT    CHECK (DISCOUNT_VAL >= 0),
    CONSTRAINT CK_COUPON_USAGE_DATE  CHECK (USAGE_END_DATE >= USAGE_BGN_DATE)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='쿠폰';

-- 2) 할인유형 공통코드 ---------------------------------------------------------
INSERT INTO COMM_CD_GROUP (GROUP_CD, GROUP_NM, EXPLN, USE_YN, REG_DT, MDFCN_DT)
SELECT 'COUPON_DISCOUNT_TYPE', '쿠폰 할인유형', '쿠폰 할인 방식(RATE=정률%/FLAT=정액원)', 'Y', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
 WHERE NOT EXISTS (SELECT 1 FROM COMM_CD_GROUP WHERE GROUP_CD = 'COUPON_DISCOUNT_TYPE' AND DEL_YN = 'N');

INSERT INTO COMM_CD (GROUP_CD, CD, CD_NM, SORT_SEQ, USE_YN, REG_DT, MDFCN_DT)
SELECT 'COUPON_DISCOUNT_TYPE', 'RATE', '정률 할인(%)', 1, 'Y', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
 WHERE NOT EXISTS (SELECT 1 FROM COMM_CD WHERE GROUP_CD = 'COUPON_DISCOUNT_TYPE' AND CD = 'RATE' AND DEL_YN = 'N');

INSERT INTO COMM_CD (GROUP_CD, CD, CD_NM, SORT_SEQ, USE_YN, REG_DT, MDFCN_DT)
SELECT 'COUPON_DISCOUNT_TYPE', 'FLAT', '정액 할인(원)', 2, 'Y', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
 WHERE NOT EXISTS (SELECT 1 FROM COMM_CD WHERE GROUP_CD = 'COUPON_DISCOUNT_TYPE' AND CD = 'FLAT' AND DEL_YN = 'N');

-- 3) 쿠폰 권한 (AUTH_MST + ROLE_AUTH) — CONTENT 권한 패턴 미러 ---------------------
INSERT INTO AUTH_MST (AUTH_CD, AUTH_NM, USE_YN, REG_DT)
SELECT 'COUPON_VIEW', '쿠폰 조회(운영자)', 'Y', UTC_TIMESTAMP(6)
 WHERE NOT EXISTS (SELECT 1 FROM AUTH_MST WHERE AUTH_CD = 'COUPON_VIEW' AND DEL_YN = 'N');

INSERT INTO AUTH_MST (AUTH_CD, AUTH_NM, USE_YN, REG_DT)
SELECT 'COUPON_MNG', '쿠폰 관리(운영자 — 발행/수정/삭제)', 'Y', UTC_TIMESTAMP(6)
 WHERE NOT EXISTS (SELECT 1 FROM AUTH_MST WHERE AUTH_CD = 'COUPON_MNG' AND DEL_YN = 'N');

INSERT INTO ROLE_AUTH (ROLE_SN, AUTH_SN, REG_DT)
SELECT R.ROLE_SN, A.AUTH_SN, UTC_TIMESTAMP(6)
  FROM ROLE_MST R
  JOIN AUTH_MST A
    ON (R.ROLE_CD = 'OPERATOR_STAFF'      AND A.AUTH_CD IN ('COUPON_VIEW','COUPON_MNG'))
    OR (R.ROLE_CD = 'OPERATOR_SUPERVISOR' AND A.AUTH_CD IN ('COUPON_VIEW','COUPON_MNG'))
 WHERE R.DEL_YN = 'N' AND A.DEL_YN = 'N'
   AND NOT EXISTS (SELECT 1 FROM ROLE_AUTH RA
                    WHERE RA.ROLE_SN = R.ROLE_SN AND RA.AUTH_SN = A.AUTH_SN AND RA.DEL_YN = 'N');
