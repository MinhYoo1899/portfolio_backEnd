-- =============================================================================
-- V17 : 상품 도메인 1차 — 고택(HOUSE) / 객실(ROOM) / 이미지 / 편의시설(AMENITY)
-- 구조(사용자 확정 2026-06-24): 판매자(PRTNR_MST) ─< 고택(HOUSE) ─< 객실(ROOM).
--   ROOM = 손님이 예약/결제하는 "예약가능 객실유형/상품"(물리 객실 아님).
--   독채 = ROOM 1개 + DFLT_STOCK_QTY=1 인 HOUSE (별도 분기 없음 — 전부 객실형으로 통일).
--   "같은 타입 객실 N개"(디럭스룸 3개) = ROOM 1행 + DFLT_STOCK_QTY=3. 날짜별 재고/요금은 후속 V(재고·요금 캘린더).
-- 소유자/행위자 분리: HOUSE.PRTNR_SN=소유자(불변), RGTR_SN/MDFR_SN+@Audit=행위자.
--   파트너 API는 인증토큰에서 PRTNR_SN 해석(요청 body의 PRTNR_SN 불신 — IDOR 차단), 운영자 대행 API만 소유 PRTNR_SN 명시 수령(후속).
-- 인가(사용자 확정): 등록/수정/삭제 1차 우선권은 판매자. 운영자 STAFF=조회만, 운영자급 SUPERVISOR=임시조치(강제 게시중지/내림)만(콘텐츠 편집 불가).
-- 상태: DRAFT(작성중) → ACTIVE(노출) ↔ HIDDEN(파트너 숨김) / SUSPENDED(운영자급 임시조치 — 파트너 자가복구 불가, 서비스/권한에서 차단).
-- 명명: 공공표준 기반. 감사컬럼 REG_DT/RGTR_SN/MDFCN_DT/MDFR_SN/DEL_YN, 활성 유니크=생성컬럼(DEL_YN='N'일 때만 값).
-- 타임존: 시드 시각 UTC_TIMESTAMP(6) (JVM/MySQL UTC 표준).
-- codex 교차검토(2026-06-24) 반영: 이미지 전용테이블 / AMENITY 전용 마스터(COMM_CD 재사용 비추) / ROOM 의미 명확화 / ACTIVE 전환은 서비스 검증.
-- codex DDL 검증(2026-06-24) 반영: 이미지→ATCH_FILE 하드FK 제거(논리참조+인덱스, 파일 물리삭제 차단 방지·기존 ATCH 참조 관례 일치) /
--   명시 인덱스 보강(HOUSE_AMENITY.HOUSE_SN, 이미지.ATCH_FILE_SN) / 명칭 공백금지 CHECK / ZIP 5자리 형식 CHECK / 운영자 메뉴 NOT EXISTS 가드.
-- 이번 범위 제외(후속): 재고캘린더·요금·예약·결제 / ROOM 물리호수·배정(ROOM_UNIT) / ROOM 레벨 편의시설(ROOM_AMENITY) / 위경도(지도 API 연동 시) / 다국어.
-- =============================================================================

-- ----------------------------------------------------------------------------
-- 고택 (판매자 소유 숙소 컨테이너 — 직접 예약 대상 아님)
-- ----------------------------------------------------------------------------
CREATE TABLE HOUSE (
    HOUSE_SN      BIGINT       NOT NULL AUTO_INCREMENT COMMENT '고택일련번호',
    PRTNR_SN      BIGINT       NOT NULL                COMMENT '소유파트너(판매자)일련번호 — 소유권 판정 기준(불변)',
    HOUSE_NM      VARCHAR(200) NOT NULL                COMMENT '고택명',
    HOUSE_STTS_CD VARCHAR(20)  NOT NULL DEFAULT 'DRAFT' COMMENT '고택상태(DRAFT/ACTIVE/HIDDEN/SUSPENDED)' CHECK (HOUSE_STTS_CD IN ('DRAFT','ACTIVE','HIDDEN','SUSPENDED')),
    ZIP           VARCHAR(5)   NULL                    COMMENT '우편번호(신우편번호 5자리)',
    ADDR          VARCHAR(320) NULL                    COMMENT '주소',
    DADDR         VARCHAR(200) NULL                    COMMENT '상세주소',
    HOUSE_DC      TEXT         NULL                    COMMENT '고택설명(소개)',
    REG_DT        DATETIME(6)  NOT NULL                COMMENT '등록일시',
    RGTR_SN       BIGINT       NULL                    COMMENT '등록자(사용자일련번호 — 행위자: 파트너 본인 또는 운영자)',
    MDFCN_DT      DATETIME(6)  NULL                    COMMENT '수정일시',
    MDFR_SN       BIGINT       NULL                    COMMENT '수정자(사용자일련번호)',
    DEL_YN        CHAR(1)      NOT NULL DEFAULT 'N'    COMMENT '삭제여부(soft delete)' CHECK (DEL_YN IN ('Y','N')),
    ACTV_PRTNR_SN BIGINT       GENERATED ALWAYS AS (CASE WHEN DEL_YN = 'N' THEN PRTNR_SN END) VIRTUAL COMMENT '활성 유니크 제약용 생성컬럼(비업무)',
    ACTV_HOUSE_NM VARCHAR(200) GENERATED ALWAYS AS (CASE WHEN DEL_YN = 'N' THEN HOUSE_NM END) VIRTUAL COMMENT '활성 유니크 제약용 생성컬럼(비업무)',
    PRIMARY KEY (HOUSE_SN),
    UNIQUE KEY UK_HOUSE_PRTNR_NM (ACTV_PRTNR_SN, ACTV_HOUSE_NM),
    KEY IX_HOUSE_PRTNR (PRTNR_SN, HOUSE_STTS_CD, DEL_YN),
    KEY IX_HOUSE_STTS (HOUSE_STTS_CD, DEL_YN, HOUSE_SN),
    CONSTRAINT FK_HOUSE_PRTNR FOREIGN KEY (PRTNR_SN) REFERENCES PRTNR_MST (PRTNR_SN),
    CONSTRAINT CK_HOUSE_NM_NOT_BLANK CHECK (CHAR_LENGTH(TRIM(HOUSE_NM)) > 0),
    CONSTRAINT CK_HOUSE_ZIP CHECK (ZIP IS NULL OR ZIP REGEXP '^[0-9]{5}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='고택';

-- ----------------------------------------------------------------------------
-- 객실 (예약가능 객실유형/상품 — 손님 예약·결제 단위. 독채는 ROOM 1개)
-- ----------------------------------------------------------------------------
CREATE TABLE ROOM (
    ROOM_SN          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '객실일련번호',
    HOUSE_SN         BIGINT      NOT NULL                COMMENT '고택일련번호',
    ROOM_NM          VARCHAR(200) NOT NULL               COMMENT '객실명(객실유형명 — 예: 디럭스룸, 안채, 사랑채)',
    ROOM_STTS_CD     VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT '객실상태(DRAFT/ACTIVE/HIDDEN/SUSPENDED)' CHECK (ROOM_STTS_CD IN ('DRAFT','ACTIVE','HIDDEN','SUSPENDED')),
    BSC_PRSN_CNT     INT         NOT NULL DEFAULT 2      COMMENT '기준인원',
    MAX_PRSN_CNT     INT         NOT NULL DEFAULT 2      COMMENT '최대인원',
    DFLT_STOCK_QTY   INT         NOT NULL DEFAULT 1      COMMENT '기본재고수량(같은유형 객실 수 — 날짜별 실판매수량은 후속 재고캘린더)',
    MIN_STAY_NGT_CNT INT         NOT NULL DEFAULT 1      COMMENT '최소숙박박수',
    ROOM_DC          TEXT        NULL                    COMMENT '객실설명',
    REG_DT           DATETIME(6) NOT NULL                COMMENT '등록일시',
    RGTR_SN          BIGINT      NULL                    COMMENT '등록자(사용자일련번호)',
    MDFCN_DT         DATETIME(6) NULL                    COMMENT '수정일시',
    MDFR_SN          BIGINT      NULL                    COMMENT '수정자(사용자일련번호)',
    DEL_YN           CHAR(1)     NOT NULL DEFAULT 'N'    COMMENT '삭제여부(soft delete)' CHECK (DEL_YN IN ('Y','N')),
    ACTV_HOUSE_SN    BIGINT      GENERATED ALWAYS AS (CASE WHEN DEL_YN = 'N' THEN HOUSE_SN END) VIRTUAL COMMENT '활성 유니크 제약용 생성컬럼(비업무)',
    ACTV_ROOM_NM     VARCHAR(200) GENERATED ALWAYS AS (CASE WHEN DEL_YN = 'N' THEN ROOM_NM END) VIRTUAL COMMENT '활성 유니크 제약용 생성컬럼(비업무)',
    PRIMARY KEY (ROOM_SN),
    UNIQUE KEY UK_ROOM_HOUSE_NM (ACTV_HOUSE_SN, ACTV_ROOM_NM),
    KEY IX_ROOM_HOUSE (HOUSE_SN, ROOM_STTS_CD, DEL_YN),
    CONSTRAINT FK_ROOM_HOUSE FOREIGN KEY (HOUSE_SN) REFERENCES HOUSE (HOUSE_SN),
    CONSTRAINT CK_ROOM_NM_NOT_BLANK CHECK (CHAR_LENGTH(TRIM(ROOM_NM)) > 0),
    CONSTRAINT CK_ROOM_PRSN CHECK (BSC_PRSN_CNT >= 1 AND MAX_PRSN_CNT >= BSC_PRSN_CNT),
    CONSTRAINT CK_ROOM_STOCK CHECK (DFLT_STOCK_QTY >= 1),
    CONSTRAINT CK_ROOM_STAY CHECK (MIN_STAY_NGT_CNT >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='객실(예약가능 객실유형)';

-- ----------------------------------------------------------------------------
-- 고택 이미지 (ATCH_FILE 논리참조 + 정렬·대표 지정)
-- ----------------------------------------------------------------------------
CREATE TABLE HOUSE_IMAGE (
    HOUSE_IMG_SN  BIGINT      NOT NULL AUTO_INCREMENT COMMENT '고택이미지일련번호',
    HOUSE_SN      BIGINT      NOT NULL                COMMENT '고택일련번호',
    ATCH_FILE_SN  BIGINT      NOT NULL                COMMENT '첨부파일일련번호(ATCH_FILE 논리참조 — 파일 정리배치 유연성 위해 하드 FK 미설정)',
    SORT_SEQ      INT         NOT NULL                COMMENT '정렬순서',
    RPRS_YN       CHAR(1)     NOT NULL DEFAULT 'N'    COMMENT '대표(썸네일)여부' CHECK (RPRS_YN IN ('Y','N')),
    REG_DT        DATETIME(6) NOT NULL                COMMENT '등록일시',
    RGTR_SN       BIGINT      NULL                    COMMENT '등록자(사용자일련번호)',
    MDFCN_DT      DATETIME(6) NULL                    COMMENT '수정일시',
    MDFR_SN       BIGINT      NULL                    COMMENT '수정자(사용자일련번호)',
    DEL_YN        CHAR(1)     NOT NULL DEFAULT 'N'    COMMENT '삭제여부' CHECK (DEL_YN IN ('Y','N')),
    ACTV_HOUSE_SN     BIGINT  GENERATED ALWAYS AS (CASE WHEN DEL_YN = 'N' THEN HOUSE_SN END) VIRTUAL COMMENT '활성 유니크 제약용 생성컬럼(비업무)',
    ACTV_RPRS_HOUSE_SN BIGINT GENERATED ALWAYS AS (CASE WHEN DEL_YN = 'N' AND RPRS_YN = 'Y' THEN HOUSE_SN END) VIRTUAL COMMENT '대표 1개 제약용 생성컬럼(비업무)',
    ACTV_ATCH_FILE_SN BIGINT  GENERATED ALWAYS AS (CASE WHEN DEL_YN = 'N' THEN ATCH_FILE_SN END) VIRTUAL COMMENT '첨부 재사용 방지용 생성컬럼(비업무)',
    PRIMARY KEY (HOUSE_IMG_SN),
    UNIQUE KEY UK_HOUSE_IMG_SORT (ACTV_HOUSE_SN, SORT_SEQ),
    UNIQUE KEY UK_HOUSE_IMG_RPRS (ACTV_RPRS_HOUSE_SN),
    UNIQUE KEY UK_HOUSE_IMG_ATCH (ACTV_ATCH_FILE_SN),
    KEY IX_HOUSE_IMG_HOUSE (HOUSE_SN, DEL_YN, SORT_SEQ),
    KEY IX_HOUSE_IMG_ATCH (ATCH_FILE_SN),
    CONSTRAINT FK_HOUSE_IMG_HOUSE FOREIGN KEY (HOUSE_SN) REFERENCES HOUSE (HOUSE_SN),
    CONSTRAINT CK_HOUSE_IMG_SORT CHECK (SORT_SEQ >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='고택이미지';

-- ----------------------------------------------------------------------------
-- 객실 이미지 (ATCH_FILE 논리참조 + 정렬·대표 지정)
-- ----------------------------------------------------------------------------
CREATE TABLE ROOM_IMAGE (
    ROOM_IMG_SN   BIGINT      NOT NULL AUTO_INCREMENT COMMENT '객실이미지일련번호',
    ROOM_SN       BIGINT      NOT NULL                COMMENT '객실일련번호',
    ATCH_FILE_SN  BIGINT      NOT NULL                COMMENT '첨부파일일련번호(ATCH_FILE 논리참조 — 파일 정리배치 유연성 위해 하드 FK 미설정)',
    SORT_SEQ      INT         NOT NULL                COMMENT '정렬순서',
    RPRS_YN       CHAR(1)     NOT NULL DEFAULT 'N'    COMMENT '대표(썸네일)여부' CHECK (RPRS_YN IN ('Y','N')),
    REG_DT        DATETIME(6) NOT NULL                COMMENT '등록일시',
    RGTR_SN       BIGINT      NULL                    COMMENT '등록자(사용자일련번호)',
    MDFCN_DT      DATETIME(6) NULL                    COMMENT '수정일시',
    MDFR_SN       BIGINT      NULL                    COMMENT '수정자(사용자일련번호)',
    DEL_YN        CHAR(1)     NOT NULL DEFAULT 'N'    COMMENT '삭제여부' CHECK (DEL_YN IN ('Y','N')),
    ACTV_ROOM_SN      BIGINT  GENERATED ALWAYS AS (CASE WHEN DEL_YN = 'N' THEN ROOM_SN END) VIRTUAL COMMENT '활성 유니크 제약용 생성컬럼(비업무)',
    ACTV_RPRS_ROOM_SN BIGINT  GENERATED ALWAYS AS (CASE WHEN DEL_YN = 'N' AND RPRS_YN = 'Y' THEN ROOM_SN END) VIRTUAL COMMENT '대표 1개 제약용 생성컬럼(비업무)',
    ACTV_ATCH_FILE_SN BIGINT  GENERATED ALWAYS AS (CASE WHEN DEL_YN = 'N' THEN ATCH_FILE_SN END) VIRTUAL COMMENT '첨부 재사용 방지용 생성컬럼(비업무)',
    PRIMARY KEY (ROOM_IMG_SN),
    UNIQUE KEY UK_ROOM_IMG_SORT (ACTV_ROOM_SN, SORT_SEQ),
    UNIQUE KEY UK_ROOM_IMG_RPRS (ACTV_RPRS_ROOM_SN),
    UNIQUE KEY UK_ROOM_IMG_ATCH (ACTV_ATCH_FILE_SN),
    KEY IX_ROOM_IMG_ROOM (ROOM_SN, DEL_YN, SORT_SEQ),
    KEY IX_ROOM_IMG_ATCH (ATCH_FILE_SN),
    CONSTRAINT FK_ROOM_IMG_ROOM FOREIGN KEY (ROOM_SN) REFERENCES ROOM (ROOM_SN),
    CONSTRAINT CK_ROOM_IMG_SORT CHECK (SORT_SEQ >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='객실이미지';

-- ----------------------------------------------------------------------------
-- 편의시설 마스터 (운영자 동적 관리 — 항목/분류/아이콘/정렬/사용여부)
-- 분류는 공통코드 AMENITY_CTGRY 참조(아래 시드). 항목 자체는 운영자가 추가/수정/비활성.
-- ----------------------------------------------------------------------------
CREATE TABLE AMENITY_MST (
    AMENITY_SN       BIGINT      NOT NULL AUTO_INCREMENT COMMENT '편의시설일련번호',
    AMENITY_CTGRY_CD VARCHAR(30) NOT NULL                COMMENT '편의시설분류코드(공통코드 AMENITY_CTGRY)',
    AMENITY_NM       VARCHAR(100) NOT NULL               COMMENT '편의시설명(예: 무료주차, 와이파이, 넷플릭스)',
    ICON_KEY         VARCHAR(100) NULL                   COMMENT '아이콘키(프론트 아이콘 매핑용)',
    SORT_SEQ         INT         NOT NULL DEFAULT 0      COMMENT '정렬순서',
    USE_YN           CHAR(1)     NOT NULL DEFAULT 'Y'    COMMENT '사용여부(비활성 시 신규 선택 불가)' CHECK (USE_YN IN ('Y','N')),
    REG_DT           DATETIME(6) NOT NULL                COMMENT '등록일시',
    RGTR_SN          BIGINT      NULL                    COMMENT '등록자(사용자일련번호)',
    MDFCN_DT         DATETIME(6) NULL                    COMMENT '수정일시',
    MDFR_SN          BIGINT      NULL                    COMMENT '수정자(사용자일련번호)',
    DEL_YN           CHAR(1)     NOT NULL DEFAULT 'N'    COMMENT '삭제여부' CHECK (DEL_YN IN ('Y','N')),
    ACTV_AMENITY_NM  VARCHAR(100) GENERATED ALWAYS AS (CASE WHEN DEL_YN = 'N' THEN AMENITY_NM END) VIRTUAL COMMENT '활성 유니크 제약용 생성컬럼(비업무)',
    PRIMARY KEY (AMENITY_SN),
    UNIQUE KEY UK_AMENITY_NM (ACTV_AMENITY_NM),
    KEY IX_AMENITY_LIST (USE_YN, DEL_YN, AMENITY_CTGRY_CD, SORT_SEQ),
    CONSTRAINT CK_AMENITY_NM_NOT_BLANK CHECK (CHAR_LENGTH(TRIM(AMENITY_NM)) > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='편의시설마스터';

-- ----------------------------------------------------------------------------
-- 고택-편의시설 매핑 (판매자가 자기 고택에 선택. 수정 시 자유 추가/해제)
-- ----------------------------------------------------------------------------
CREATE TABLE HOUSE_AMENITY (
    HOUSE_AMENITY_SN BIGINT      NOT NULL AUTO_INCREMENT COMMENT '고택편의시설일련번호',
    HOUSE_SN         BIGINT      NOT NULL                COMMENT '고택일련번호',
    AMENITY_SN       BIGINT      NOT NULL                COMMENT '편의시설일련번호',
    REG_DT           DATETIME(6) NOT NULL                COMMENT '등록일시',
    RGTR_SN          BIGINT      NULL                    COMMENT '등록자(사용자일련번호)',
    MDFCN_DT         DATETIME(6) NULL                    COMMENT '수정일시',
    MDFR_SN          BIGINT      NULL                    COMMENT '수정자(사용자일련번호)',
    DEL_YN           CHAR(1)     NOT NULL DEFAULT 'N'    COMMENT '삭제여부(선택 해제)' CHECK (DEL_YN IN ('Y','N')),
    ACTV_HOUSE_SN    BIGINT      GENERATED ALWAYS AS (CASE WHEN DEL_YN = 'N' THEN HOUSE_SN END) VIRTUAL COMMENT '활성 유니크 제약용 생성컬럼(비업무)',
    ACTV_AMENITY_SN  BIGINT      GENERATED ALWAYS AS (CASE WHEN DEL_YN = 'N' THEN AMENITY_SN END) VIRTUAL COMMENT '활성 유니크 제약용 생성컬럼(비업무)',
    PRIMARY KEY (HOUSE_AMENITY_SN),
    UNIQUE KEY UK_HOUSE_AMENITY (ACTV_HOUSE_SN, ACTV_AMENITY_SN),
    KEY IX_HOUSE_AMENITY_HOUSE (HOUSE_SN, DEL_YN),
    KEY IX_HOUSE_AMENITY_AMENITY (AMENITY_SN, DEL_YN),
    CONSTRAINT FK_HOUSE_AMENITY_HOUSE FOREIGN KEY (HOUSE_SN) REFERENCES HOUSE (HOUSE_SN),
    CONSTRAINT FK_HOUSE_AMENITY_AMENITY FOREIGN KEY (AMENITY_SN) REFERENCES AMENITY_MST (AMENITY_SN)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='고택편의시설매핑';

-- ----------------------------------------------------------------------------
-- 공통코드: 편의시설 분류 (운영자가 분류도 동적 추가 가능하나, 1차는 공통코드로 시작)
-- ----------------------------------------------------------------------------
INSERT INTO COMM_CD_GROUP (GROUP_CD, GROUP_NM, EXPLN, USE_YN, REG_DT) VALUES
    ('AMENITY_CTGRY', '편의시설분류', '고택 제공 서비스/편의시설 분류(주차/인터넷/미디어/시설/기타)', 'Y', UTC_TIMESTAMP(6));

INSERT INTO COMM_CD (GROUP_CD, CD, CD_NM, SORT_SEQ, USE_YN, REG_DT) VALUES
    ('AMENITY_CTGRY', 'PARKING',  '주차',   1, 'Y', UTC_TIMESTAMP(6)),
    ('AMENITY_CTGRY', 'INTERNET', '인터넷', 2, 'Y', UTC_TIMESTAMP(6)),
    ('AMENITY_CTGRY', 'MEDIA',    '미디어/엔터테인먼트', 3, 'Y', UTC_TIMESTAMP(6)),
    ('AMENITY_CTGRY', 'FACILITY', '시설',   4, 'Y', UTC_TIMESTAMP(6)),
    ('AMENITY_CTGRY', 'ETC',      '기타',   5, 'Y', UTC_TIMESTAMP(6));

-- 편의시설 항목 스타터(운영자가 이후 추가/수정/비활성 — 예시 목록)
INSERT INTO AMENITY_MST (AMENITY_CTGRY_CD, AMENITY_NM, ICON_KEY, SORT_SEQ, USE_YN, REG_DT) VALUES
    ('PARKING',  '무료주차',     'parking',   1, 'Y', UTC_TIMESTAMP(6)),
    ('INTERNET', '무료 와이파이', 'wifi',      2, 'Y', UTC_TIMESTAMP(6)),
    ('MEDIA',    '넷플릭스',     'netflix',   3, 'Y', UTC_TIMESTAMP(6)),
    ('MEDIA',    '디즈니플러스',  'disney',    4, 'Y', UTC_TIMESTAMP(6)),
    ('MEDIA',    'TV',          'tv',        5, 'Y', UTC_TIMESTAMP(6)),
    ('FACILITY', '취사가능',     'kitchen',   6, 'Y', UTC_TIMESTAMP(6)),
    ('FACILITY', '바비큐',       'bbq',       7, 'Y', UTC_TIMESTAMP(6)),
    ('FACILITY', '에어컨',       'aircon',    8, 'Y', UTC_TIMESTAMP(6)),
    ('FACILITY', '냉장고',       'fridge',    9, 'Y', UTC_TIMESTAMP(6)),
    ('ETC',      '반려동물 동반', 'pet',      10, 'Y', UTC_TIMESTAMP(6)),
    ('ETC',      '금연',         'nosmoking', 11, 'Y', UTC_TIMESTAMP(6));

-- ----------------------------------------------------------------------------
-- 권한: 상품관리(파트너) / 상품조회(운영자) / 상품임시조치(운영자급)
-- ----------------------------------------------------------------------------
INSERT INTO AUTH_MST (AUTH_CD, AUTH_NM, USE_YN, REG_DT)
SELECT 'HOUSE_MNG', '상품(고택/객실) 관리 — 등록/수정/삭제(본인 소유)', 'Y', UTC_TIMESTAMP(6)
 WHERE NOT EXISTS (SELECT 1 FROM AUTH_MST WHERE AUTH_CD = 'HOUSE_MNG' AND DEL_YN = 'N');

INSERT INTO AUTH_MST (AUTH_CD, AUTH_NM, USE_YN, REG_DT)
SELECT 'HOUSE_VIEW', '상품 조회(운영자 — 전체 목록/상세)', 'Y', UTC_TIMESTAMP(6)
 WHERE NOT EXISTS (SELECT 1 FROM AUTH_MST WHERE AUTH_CD = 'HOUSE_VIEW' AND DEL_YN = 'N');

INSERT INTO AUTH_MST (AUTH_CD, AUTH_NM, USE_YN, REG_DT)
SELECT 'HOUSE_MODER', '상품 임시조치(운영자급 — 강제 게시중지/내림)', 'Y', UTC_TIMESTAMP(6)
 WHERE NOT EXISTS (SELECT 1 FROM AUTH_MST WHERE AUTH_CD = 'HOUSE_MODER' AND DEL_YN = 'N');

-- 편의시설 카탈로그 관리(전역 기준데이터) — 운영자급 전용(codex: ACNT_MNG/INQR_MODER 와 동일 거버넌스 등급)
INSERT INTO AUTH_MST (AUTH_CD, AUTH_NM, USE_YN, REG_DT)
SELECT 'AMENITY_MNG', '편의시설 카탈로그 관리(추가/수정/비활성)', 'Y', UTC_TIMESTAMP(6)
 WHERE NOT EXISTS (SELECT 1 FROM AUTH_MST WHERE AUTH_CD = 'AMENITY_MNG' AND DEL_YN = 'N');

-- 역할 매핑: HOUSE_MNG→파트너 / HOUSE_VIEW→운영자(직원·수퍼바이저) / HOUSE_MODER·AMENITY_MNG→수퍼바이저
INSERT INTO ROLE_AUTH (ROLE_SN, AUTH_SN, REG_DT)
SELECT R.ROLE_SN, A.AUTH_SN, UTC_TIMESTAMP(6)
  FROM ROLE_MST R
  JOIN AUTH_MST A
    ON (R.ROLE_CD = 'PARTNER'             AND A.AUTH_CD = 'HOUSE_MNG')
    OR (R.ROLE_CD = 'OPERATOR_STAFF'      AND A.AUTH_CD = 'HOUSE_VIEW')
    OR (R.ROLE_CD = 'OPERATOR_SUPERVISOR' AND A.AUTH_CD IN ('HOUSE_VIEW','HOUSE_MODER','AMENITY_MNG'))
 WHERE R.DEL_YN = 'N' AND A.DEL_YN = 'N'
   AND NOT EXISTS (SELECT 1 FROM ROLE_AUTH RA
                    WHERE RA.ROLE_SN = R.ROLE_SN AND RA.AUTH_SN = A.AUTH_SN AND RA.DEL_YN = 'N');

-- ----------------------------------------------------------------------------
-- 메뉴: 운영자 "상품 관리(모니터링)" (파트너 "고택 관리" /house 는 V9 기존). 필요권한=HOUSE_VIEW
-- ----------------------------------------------------------------------------
INSERT INTO MENU_INFO (MENU_CHNL_CD, UPPER_MENU_SN, MENU_NM, URL_ADDR, AUTH_CD, LGN_NEED_YN, SORT_SEQ, USE_YN, REG_DT)
SELECT 'OPERATOR', NULL, '상품 관리', '/houses', 'HOUSE_VIEW', 'Y',
       COALESCE((SELECT MAX(SORT_SEQ) FROM MENU_INFO WHERE MENU_CHNL_CD = 'OPERATOR' AND UPPER_MENU_SN IS NULL AND DEL_YN = 'N'), 0) + 1,
       'Y', UTC_TIMESTAMP(6)
 WHERE NOT EXISTS (SELECT 1 FROM MENU_INFO WHERE MENU_CHNL_CD = 'OPERATOR' AND URL_ADDR = '/houses' AND DEL_YN = 'N');
