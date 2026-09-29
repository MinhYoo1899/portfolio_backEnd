-- =============================================================================
-- V32 : 객실 냉난방시설(ROOM_CLIM_FCLTY) — 냉방/난방을 별도 개념으로 분리
-- 구조: 판매자(PRTNR_MST) ─< 고택(HOUSE) ─< 객실(ROOM) ─< 냉난방시설.
--   소유권 체인 = ROOM → HOUSE → PRTNR. 변경계는 ROOM JOIN HOUSE 로 PRTNR_SN 2중가드(IDOR 차단).
-- 단일테이블에 FCLTY_DIV_CD(COOLING/HEATING)로 냉방·난방 구분, 각각 다중선택.
--   FCLTY_CD = COMM_CD(COOLING_TYPE / HEATING_TYPE) 값. 설정은 교체(replace) 방식 — 서비스가 소유권 선검증.
-- 감사컬럼 REG_DT/RGTR_SN/MDFCN_DT/MDFR_SN/DEL_YN, 활성 유니크=생성컬럼(DEL_YN='N'일 때만 값). insert 시 MDFCN_DT=REG_DT.
-- 타임존: UTC_TIMESTAMP(6) (JVM/MySQL UTC 표준).
-- (관련: V33 에서 AMENITY_MST '에어컨'을 비활성화 — 냉난방으로 일원화.)
-- =============================================================================

-- ── 1) COMM_CD 신규그룹 (냉방종류 / 난방종류) ────────────────────────────────
INSERT INTO COMM_CD_GROUP (GROUP_CD, GROUP_NM, EXPLN, USE_YN, REG_DT, MDFCN_DT) VALUES
 ('COOLING_TYPE', '냉방종류', '객실 냉방시설 종류(ROOM_CLIM_FCLTY.FCLTY_CD, DIV=COOLING)', 'Y', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
 ('HEATING_TYPE', '난방종류', '객실 난방시설 종류(ROOM_CLIM_FCLTY.FCLTY_CD, DIV=HEATING)', 'Y', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6));

INSERT INTO COMM_CD (GROUP_CD, CD, CD_NM, SORT_SEQ, USE_YN, REG_DT, MDFCN_DT) VALUES
 ('COOLING_TYPE', 'AIRCON',          '에어컨',       1, 'Y', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
 ('COOLING_TYPE', 'SYSTEM_AIRCON',   '시스템에어컨', 2, 'Y', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
 ('COOLING_TYPE', 'FAN',             '선풍기',       3, 'Y', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
 ('COOLING_TYPE', 'CEILING_FAN',     '천장형 선풍기', 4, 'Y', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
 ('COOLING_TYPE', 'AIR_COOLER',      '냉풍기',       5, 'Y', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
 ('HEATING_TYPE', 'ONDOL',           '온돌',         1, 'Y', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
 ('HEATING_TYPE', 'BOILER',          '보일러',       2, 'Y', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
 ('HEATING_TYPE', 'ELECTRIC_HEATER', '전기히터',     3, 'Y', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
 ('HEATING_TYPE', 'FIREPLACE',       '벽난로',       4, 'Y', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
 ('HEATING_TYPE', 'RADIATOR',        '라디에이터',   5, 'Y', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
 ('HEATING_TYPE', 'HEAT_PUMP',       '히트펌프',     6, 'Y', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6));

-- ── 2) ROOM_CLIM_FCLTY (객실 냉난방시설 — 냉방/난방 각각 다중) ─────────────────
CREATE TABLE ROOM_CLIM_FCLTY (
    ROOM_CLIM_FCLTY_SN BIGINT      NOT NULL AUTO_INCREMENT COMMENT '객실냉난방시설일련번호',
    ROOM_SN            BIGINT      NOT NULL                COMMENT '객실일련번호',
    FCLTY_DIV_CD       VARCHAR(20) NOT NULL                COMMENT '냉난방구분(COOLING/HEATING)',
    FCLTY_CD           VARCHAR(30) NOT NULL                COMMENT '시설코드(COMM_CD COOLING_TYPE/HEATING_TYPE)',
    REG_DT             DATETIME(6) NOT NULL                COMMENT '등록일시',
    RGTR_SN            BIGINT      NULL                    COMMENT '등록자(사용자일련번호)',
    MDFCN_DT           DATETIME(6) NULL                    COMMENT '수정일시',
    MDFR_SN            BIGINT      NULL                    COMMENT '수정자(사용자일련번호)',
    DEL_YN             CHAR(1)     NOT NULL DEFAULT 'N'    COMMENT '삭제여부(선택 해제)' CHECK (DEL_YN IN ('Y','N')),
    ACTV_ROOM_SN       BIGINT      GENERATED ALWAYS AS (CASE WHEN DEL_YN = 'N' THEN ROOM_SN END) VIRTUAL COMMENT '활성 유니크 제약용 생성컬럼(비업무)',
    ACTV_FCLTY_DIV_CD  VARCHAR(20) GENERATED ALWAYS AS (CASE WHEN DEL_YN = 'N' THEN FCLTY_DIV_CD END) VIRTUAL COMMENT '활성 유니크 제약용 생성컬럼(비업무)',
    ACTV_FCLTY_CD      VARCHAR(30) GENERATED ALWAYS AS (CASE WHEN DEL_YN = 'N' THEN FCLTY_CD END) VIRTUAL COMMENT '활성 유니크 제약용 생성컬럼(비업무)',
    PRIMARY KEY (ROOM_CLIM_FCLTY_SN),
    UNIQUE KEY UK_ROOM_CLIM_FCLTY (ACTV_ROOM_SN, ACTV_FCLTY_DIV_CD, ACTV_FCLTY_CD),
    KEY IX_ROOM_CLIM_FCLTY_ROOM (ROOM_SN, DEL_YN),
    CONSTRAINT FK_ROOM_CLIM_FCLTY_ROOM FOREIGN KEY (ROOM_SN) REFERENCES ROOM (ROOM_SN),
    CONSTRAINT CK_ROOM_CLIM_FCLTY_DIV CHECK (FCLTY_DIV_CD IN ('COOLING','HEATING'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='객실냉난방시설';
