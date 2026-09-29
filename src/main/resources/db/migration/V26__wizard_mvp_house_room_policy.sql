-- =============================================================================
-- V26 : 운영자 고택등록 위저드 MVP 스키마 — 고택 정책/추가금, 객실 상세, 결제수단·침구 자식테이블, 통신판매신고번호
-- codex 스키마 교차검토 반영: 시각 TIME, 금액 INT UNSIGNED DEFAULT 0(음수불가), 면적 CHECK>0,
--   자식테이블 활성유니크=생성컬럼(DEL_YN='N'만), 침구 BED_CNT>0, enum=COMM_CD 신규그룹.
-- 가격은 ROOM_CALENDAR(날짜별) 기반 — ROOM 요금컬럼 신설 안 함. 판매자정보는 PRTNR_MST 자동(고택 미저장).
-- 명명: UPPER_SNAKE. 감사 insert 수정일=등록일([[convention-mdfcndt-equals-regdt-on-insert]]).
-- =============================================================================

-- ── 1) HOUSE 추가 (고택 단위 정책/추가금) ────────────────────────────────────
ALTER TABLE HOUSE
  ADD COLUMN ADULT_ADD_CHRG_AMT  INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '성인(19+) 1인당 추가금(KRW)',
  ADD COLUMN CHILD_ADD_CHRG_AMT  INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '아동(8~18) 1인당 추가금(KRW)',
  ADD COLUMN INFANT_ADD_CHRG_AMT INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '영유아(<8) 1인당 추가금(KRW)',
  ADD COLUMN CHKIN_TIME        TIME        NULL COMMENT '체크인 시각(현지 wall-clock, TZ 무관)',
  ADD COLUMN CHKOUT_TIME       TIME        NULL COMMENT '체크아웃 시각(현지 wall-clock)',
  ADD COLUMN RSVT_CNFIRM_CD    VARCHAR(20) NULL COMMENT '예약확정방식(COMM_CD RSVT_CNFIRM: AUTO/MANUAL)',
  ADD COLUMN CNCL_RFND_PLCY_CN TEXT        NULL COMMENT '취소·환불규정(게시 필수)',
  ADD COLUMN PBTRNSP_GUIDE_CN  TEXT        NULL COMMENT '대중교통 안내';

-- ── 2) ROOM 추가 (객실 단위 상세) ────────────────────────────────────────────
ALTER TABLE ROOM
  ADD COLUMN AREA_SQM   DECIMAL(7,2)      NULL COMMENT '면적(m²)',
  ADD COLUMN ROOM_CNT   SMALLINT UNSIGNED NULL COMMENT '방 개수',
  ADD COLUMN BATHRM_CNT SMALLINT UNSIGNED NULL COMMENT '화장실 개수',
  ADD CONSTRAINT CK_ROOM_AREA_SQM CHECK (AREA_SQM IS NULL OR AREA_SQM > 0);

-- ── 3) PRTNR_MST 추가 (판매자정보 — 통신판매업신고번호) ──────────────────────
ALTER TABLE PRTNR_MST
  ADD COLUMN MAIL_ORDER_SALES_NO VARCHAR(40) NULL COMMENT '통신판매업신고번호(게시 필수화 권고)';

-- ── 4) HOUSE_PAY_MTHD (결제수단 다중) ────────────────────────────────────────
CREATE TABLE HOUSE_PAY_MTHD (
    HOUSE_PAY_MTHD_SN BIGINT      NOT NULL AUTO_INCREMENT COMMENT '고택결제수단일련번호',
    HOUSE_SN          BIGINT      NOT NULL                COMMENT '고택일련번호',
    PAY_MTHD_CD       VARCHAR(20) NOT NULL                COMMENT '결제수단코드(COMM_CD PAY_MTHD)',
    SORT_SEQ          INT         NOT NULL DEFAULT 0      COMMENT '정렬순서',
    REG_DT            DATETIME(6) NOT NULL                COMMENT '등록일시',
    RGTR_SN           BIGINT      NULL                    COMMENT '등록자(사용자일련번호)',
    MDFCN_DT          DATETIME(6) NULL                    COMMENT '수정일시',
    MDFR_SN           BIGINT      NULL                    COMMENT '수정자(사용자일련번호)',
    DEL_YN            CHAR(1)     NOT NULL DEFAULT 'N'    COMMENT '삭제여부' CHECK (DEL_YN IN ('Y','N')),
    ACTV_HOUSE_SN     BIGINT      GENERATED ALWAYS AS (CASE WHEN DEL_YN = 'N' THEN HOUSE_SN END) VIRTUAL COMMENT '활성 유니크용(비업무)',
    ACTV_PAY_MTHD_CD  VARCHAR(20) GENERATED ALWAYS AS (CASE WHEN DEL_YN = 'N' THEN PAY_MTHD_CD END) VIRTUAL COMMENT '활성 유니크용(비업무)',
    PRIMARY KEY (HOUSE_PAY_MTHD_SN),
    UNIQUE KEY UK_HOUSE_PAY_MTHD (ACTV_HOUSE_SN, ACTV_PAY_MTHD_CD),
    KEY IX_HOUSE_PAY_MTHD_HOUSE (HOUSE_SN, DEL_YN),
    CONSTRAINT FK_HOUSE_PAY_MTHD_HOUSE FOREIGN KEY (HOUSE_SN) REFERENCES HOUSE (HOUSE_SN)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='고택결제수단';

-- ── 5) ROOM_BEDDING (침구 타입+개수) ─────────────────────────────────────────
CREATE TABLE ROOM_BEDDING (
    ROOM_BEDDING_SN BIGINT      NOT NULL AUTO_INCREMENT COMMENT '객실침구일련번호',
    ROOM_SN         BIGINT      NOT NULL                COMMENT '객실일련번호',
    BEDDING_TYPE_CD VARCHAR(20) NOT NULL                COMMENT '침구타입코드(COMM_CD BEDDING_TYPE)',
    BED_CNT         SMALLINT UNSIGNED NOT NULL          COMMENT '개수(>=1)',
    REG_DT          DATETIME(6) NOT NULL                COMMENT '등록일시',
    RGTR_SN         BIGINT      NULL                    COMMENT '등록자(사용자일련번호)',
    MDFCN_DT        DATETIME(6) NULL                    COMMENT '수정일시',
    MDFR_SN         BIGINT      NULL                    COMMENT '수정자(사용자일련번호)',
    DEL_YN          CHAR(1)     NOT NULL DEFAULT 'N'    COMMENT '삭제여부' CHECK (DEL_YN IN ('Y','N')),
    ACTV_ROOM_SN         BIGINT      GENERATED ALWAYS AS (CASE WHEN DEL_YN = 'N' THEN ROOM_SN END) VIRTUAL COMMENT '활성 유니크용(비업무)',
    ACTV_BEDDING_TYPE_CD VARCHAR(20) GENERATED ALWAYS AS (CASE WHEN DEL_YN = 'N' THEN BEDDING_TYPE_CD END) VIRTUAL COMMENT '활성 유니크용(비업무)',
    PRIMARY KEY (ROOM_BEDDING_SN),
    UNIQUE KEY UK_ROOM_BEDDING (ACTV_ROOM_SN, ACTV_BEDDING_TYPE_CD),
    KEY IX_ROOM_BEDDING_ROOM (ROOM_SN, DEL_YN),
    CONSTRAINT FK_ROOM_BEDDING_ROOM FOREIGN KEY (ROOM_SN) REFERENCES ROOM (ROOM_SN),
    CONSTRAINT CK_ROOM_BEDDING_CNT CHECK (BED_CNT > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='객실침구';

-- ── 6) COMM_CD 신규그룹 (예약확정방식 / 결제수단 / 침구타입) ─────────────────
INSERT INTO COMM_CD_GROUP (GROUP_CD, GROUP_NM, EXPLN, USE_YN, REG_DT, MDFCN_DT) VALUES
 ('RSVT_CNFIRM', '예약확정방식', '고택 예약 확정 방식(HOUSE.RSVT_CNFIRM_CD)', 'Y', NOW(6), NOW(6)),
 ('PAY_MTHD',    '결제수단',     '고택 결제수단(HOUSE_PAY_MTHD.PAY_MTHD_CD)',  'Y', NOW(6), NOW(6)),
 ('BEDDING_TYPE','침구타입',     '객실 침구 타입(ROOM_BEDDING.BEDDING_TYPE_CD)', 'Y', NOW(6), NOW(6));

INSERT INTO COMM_CD (GROUP_CD, CD, CD_NM, SORT_SEQ, USE_YN, REG_DT, MDFCN_DT) VALUES
 ('RSVT_CNFIRM', 'AUTO',         '자동확정',     1, 'Y', NOW(6), NOW(6)),
 ('RSVT_CNFIRM', 'MANUAL',       '수동확정',     2, 'Y', NOW(6), NOW(6)),
 ('PAY_MTHD',    'CARD',         '신용카드',     1, 'Y', NOW(6), NOW(6)),
 ('PAY_MTHD',    'BANK_TRANSFER','계좌이체',     2, 'Y', NOW(6), NOW(6)),
 ('PAY_MTHD',    'ONSITE',       '현장결제',     3, 'Y', NOW(6), NOW(6)),
 ('BEDDING_TYPE','SINGLE_BED',   '싱글 베드',    1, 'Y', NOW(6), NOW(6)),
 ('BEDDING_TYPE','DOUBLE_BED',   '더블 베드',    2, 'Y', NOW(6), NOW(6)),
 ('BEDDING_TYPE','QUEEN_BED',    '퀸 베드',      3, 'Y', NOW(6), NOW(6)),
 ('BEDDING_TYPE','KING_BED',     '킹 베드',      4, 'Y', NOW(6), NOW(6)),
 ('BEDDING_TYPE','FLOOR_BEDDING','이부자리(온돌)', 5, 'Y', NOW(6), NOW(6));
