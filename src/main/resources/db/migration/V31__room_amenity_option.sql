-- =============================================================================
-- V31 : 객실 확장 2종 — 객실 편의시설(ROOM_AMENITY) / 객실 추가옵션(ROOM_OPTION)
-- 구조: 판매자(PRTNR_MST) ─< 고택(HOUSE) ─< 객실(ROOM) ─< {편의시설/옵션}.
--   소유권 체인 = ROOM → HOUSE → PRTNR. 변경계는 ROOM JOIN HOUSE 로 PRTNR_SN 2중가드(IDOR 차단).
-- ROOM_AMENITY  = 객실 레벨 편의시설(기존 AMENITY_MST 마스터 재사용 — HOUSE_AMENITY 미러).
-- ROOM_OPTION   = 객실 자유텍스트 추가옵션(옵션명+추가금액). 과금단위=예약당 1회 고정.
-- 감사컬럼 REG_DT/RGTR_SN/MDFCN_DT/MDFR_SN/DEL_YN, 활성 유니크=생성컬럼(DEL_YN='N'일 때만 값). insert 시 MDFCN_DT=REG_DT.
-- 타임존: UTC_TIMESTAMP(6) (JVM/MySQL UTC 표준). 설정은 교체(replace) 방식 — 서비스가 소유권 선검증.
-- =============================================================================

-- ----------------------------------------------------------------------------
-- 객실-편의시설 매핑 (판매자가 자기 객실에 선택. 수정 시 자유 추가/해제 — HOUSE_AMENITY 미러)
-- ----------------------------------------------------------------------------
CREATE TABLE ROOM_AMENITY (
    ROOM_AMENITY_SN BIGINT      NOT NULL AUTO_INCREMENT COMMENT '객실편의시설일련번호',
    ROOM_SN         BIGINT      NOT NULL                COMMENT '객실일련번호',
    AMENITY_SN      BIGINT      NOT NULL                COMMENT '편의시설일련번호',
    REG_DT          DATETIME(6) NOT NULL                COMMENT '등록일시',
    RGTR_SN         BIGINT      NULL                    COMMENT '등록자(사용자일련번호)',
    MDFCN_DT        DATETIME(6) NULL                    COMMENT '수정일시',
    MDFR_SN         BIGINT      NULL                    COMMENT '수정자(사용자일련번호)',
    DEL_YN          CHAR(1)     NOT NULL DEFAULT 'N'    COMMENT '삭제여부(선택 해제)' CHECK (DEL_YN IN ('Y','N')),
    ACTV_ROOM_SN    BIGINT      GENERATED ALWAYS AS (CASE WHEN DEL_YN = 'N' THEN ROOM_SN END) VIRTUAL COMMENT '활성 유니크 제약용 생성컬럼(비업무)',
    ACTV_AMENITY_SN BIGINT      GENERATED ALWAYS AS (CASE WHEN DEL_YN = 'N' THEN AMENITY_SN END) VIRTUAL COMMENT '활성 유니크 제약용 생성컬럼(비업무)',
    PRIMARY KEY (ROOM_AMENITY_SN),
    UNIQUE KEY UK_ROOM_AMENITY (ACTV_ROOM_SN, ACTV_AMENITY_SN),
    KEY IX_ROOM_AMENITY_ROOM (ROOM_SN, DEL_YN),
    KEY IX_ROOM_AMENITY_AMENITY (AMENITY_SN, DEL_YN),
    CONSTRAINT FK_ROOM_AMENITY_ROOM FOREIGN KEY (ROOM_SN) REFERENCES ROOM (ROOM_SN),
    CONSTRAINT FK_ROOM_AMENITY_AMENITY FOREIGN KEY (AMENITY_SN) REFERENCES AMENITY_MST (AMENITY_SN)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='객실편의시설매핑';

-- ----------------------------------------------------------------------------
-- 객실 추가옵션 (자유텍스트 옵션명 + 추가금액)
--   과금단위 = 예약당 1회 고정(사용자 확정) — 1박당/인원당은 후속 CHARGE_UNIT_CD 컬럼으로 확장.
-- ----------------------------------------------------------------------------
CREATE TABLE ROOM_OPTION (
    ROOM_OPTION_SN BIGINT       NOT NULL AUTO_INCREMENT COMMENT '객실추가옵션일련번호',
    ROOM_SN        BIGINT       NOT NULL                COMMENT '객실일련번호',
    OPTION_NM      VARCHAR(100) NOT NULL                COMMENT '옵션명(자유텍스트)',
    ADD_AMT        INT UNSIGNED NOT NULL DEFAULT 0      COMMENT '추가금액(원) — 과금단위=예약당 1회 고정',
    SORT_SEQ       INT          NOT NULL DEFAULT 0      COMMENT '정렬순서(0부터)',
    REG_DT         DATETIME(6)  NOT NULL                COMMENT '등록일시',
    RGTR_SN        BIGINT       NULL                    COMMENT '등록자(사용자일련번호)',
    MDFCN_DT       DATETIME(6)  NULL                    COMMENT '수정일시',
    MDFR_SN        BIGINT       NULL                    COMMENT '수정자(사용자일련번호)',
    DEL_YN         CHAR(1)      NOT NULL DEFAULT 'N'    COMMENT '삭제여부(soft delete)' CHECK (DEL_YN IN ('Y','N')),
    ACTV_ROOM_SN   BIGINT       GENERATED ALWAYS AS (CASE WHEN DEL_YN = 'N' THEN ROOM_SN END) VIRTUAL COMMENT '활성 유니크 제약용 생성컬럼(비업무)',
    ACTV_OPTION_NM VARCHAR(100) GENERATED ALWAYS AS (CASE WHEN DEL_YN = 'N' THEN OPTION_NM END) VIRTUAL COMMENT '활성 유니크 제약용 생성컬럼(비업무)',
    PRIMARY KEY (ROOM_OPTION_SN),
    UNIQUE KEY UK_ROOM_OPTION (ACTV_ROOM_SN, ACTV_OPTION_NM),
    KEY IX_ROOM_OPTION_ROOM (ROOM_SN, DEL_YN),
    CONSTRAINT FK_ROOM_OPTION_ROOM FOREIGN KEY (ROOM_SN) REFERENCES ROOM (ROOM_SN),
    CONSTRAINT CK_ROOM_OPTION_NM_NOT_BLANK CHECK (CHAR_LENGTH(TRIM(OPTION_NM)) > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='객실추가옵션(과금=예약당 1회)';
