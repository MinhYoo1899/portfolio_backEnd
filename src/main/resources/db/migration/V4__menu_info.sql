-- =============================================================================
-- V4 : 메뉴 (MENU_INFO) — React SPA 3종(사용자/파트너/마스터)의 동적 메뉴
-- 대상: MySQL 8.0.16 이상 / InnoDB / utf8mb4. 명명: 공공표준(docs/db-naming-standard.md).
-- codex 자문 반영: 메뉴-권한은 단일 AUTHRT_CD(NULL=공개), 채널은 USER_TYPE과 분리(MENU_CHNL),
--           모순 데이터 CHECK(비로그인 메뉴에 권한코드 금지), 조회 인덱스(채널/상위/사용/정렬),
--           활성유니크(채널+URL — 폴더형 메뉴는 URL NULL 허용·중복 무관).
-- 노출 규칙(앱 구현): USE_YN='Y' AND DEL_YN='N' AND (LGN_NEED_YN='N' OR 로그인)
--           AND (AUTHRT_CD IS NULL OR 보유 권한에 포함) + 고아 메뉴(부모 비노출) 제거.
-- 메뉴 데이터는 프론트 라우트 확정 후 입력(운영자 관리) — 개발 샘플은 db/migration-dev.
-- =============================================================================

-- ----------------------------------------------------------------------------
-- 공통코드: 메뉴채널 (SPA 구분 — 사용자유형(USER_TYPE)과 별개 개념)
-- ----------------------------------------------------------------------------
INSERT INTO COM_CD_GROUP (GROUP_CD, GROUP_NM, EXPLN, USE_YN, REG_DT) VALUES
    ('MENU_CHNL', '메뉴채널', '메뉴가 노출되는 SPA 구분(MENU_INFO.MENU_CHNL_CD)', 'Y', NOW(6));

INSERT INTO COM_CD (GROUP_CD, CD, CD_NM, SORT_SEQ, USE_YN, REG_DT) VALUES
    ('MENU_CHNL', 'USER',    '사용자 SPA',  1, 'Y', NOW(6)),
    ('MENU_CHNL', 'PARTNER', '파트너 SPA',  2, 'Y', NOW(6)),
    ('MENU_CHNL', 'MASTER',  '마스터 SPA',  3, 'Y', NOW(6));

-- ----------------------------------------------------------------------------
-- 메뉴 (계층형 — UPPER_MENU_SN 자기참조 논리참조(FK 없음), 부모-자식 채널 일치는 앱/시드 검증)
-- ----------------------------------------------------------------------------
CREATE TABLE MENU_INFO (
    MENU_SN       BIGINT       NOT NULL AUTO_INCREMENT COMMENT '메뉴일련번호',
    MENU_CHNL_CD  VARCHAR(20)  NOT NULL                COMMENT '메뉴채널코드(공통코드 MENU_CHNL: USER/PARTNER/MASTER)',
    UPPER_MENU_SN BIGINT       NULL                    COMMENT '상위메뉴일련번호(계층, NULL=최상위 — 자기참조 논리참조)',
    MENU_NM       VARCHAR(100) NOT NULL                COMMENT '메뉴명',
    URL_ADDR      VARCHAR(255) NULL                    COMMENT 'URL주소(SPA 라우트 경로 — NULL=폴더형 그룹메뉴)',
    ICON_NM       VARCHAR(50)  NULL                    COMMENT '아이콘명(프론트 아이콘 키)',
    AUTHRT_CD     VARCHAR(80)  NULL                    COMMENT '필요권한코드(AUTHRT.AUTHRT_CD 논리참조 — NULL=권한 불요)',
    LGN_NEED_YN   CHAR(1)      NOT NULL DEFAULT 'N'    COMMENT '로그인필요여부' CHECK (LGN_NEED_YN IN ('Y','N')),
    SORT_SEQ      INT          NOT NULL DEFAULT 0      COMMENT '정렬순서(같은 부모 내)',
    USE_YN        CHAR(1)      NOT NULL DEFAULT 'Y'    COMMENT '사용여부' CHECK (USE_YN IN ('Y','N')),
    REG_DT        DATETIME(6)  NOT NULL                COMMENT '등록일시',
    RGTR_SN       BIGINT       NULL                    COMMENT '등록자(사용자일련번호)',
    MDFCN_DT      DATETIME(6)  NULL                    COMMENT '수정일시',
    MDFR_SN       BIGINT       NULL                    COMMENT '수정자(사용자일련번호)',
    DEL_YN        CHAR(1)      NOT NULL DEFAULT 'N'    COMMENT '삭제여부' CHECK (DEL_YN IN ('Y','N')),
    ACTV_MENU_CHNL_CD VARCHAR(20)  GENERATED ALWAYS AS (CASE WHEN DEL_YN = 'N' THEN MENU_CHNL_CD END) VIRTUAL COMMENT '활성행 유니크 제약용 생성컬럼(비업무)',
    ACTV_URL_ADDR     VARCHAR(255) GENERATED ALWAYS AS (CASE WHEN DEL_YN = 'N' THEN URL_ADDR END) VIRTUAL COMMENT '활성행 유니크 제약용 생성컬럼(비업무)',
    PRIMARY KEY (MENU_SN),
    UNIQUE KEY UK_MENU_INFO_CHNL_URL (ACTV_MENU_CHNL_CD, ACTV_URL_ADDR),
    KEY IX_MENU_INFO_LIST (MENU_CHNL_CD, UPPER_MENU_SN, USE_YN, DEL_YN, SORT_SEQ),
    CONSTRAINT CK_MENU_INFO_AUTHRT CHECK (LGN_NEED_YN = 'Y' OR AUTHRT_CD IS NULL)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='메뉴';
