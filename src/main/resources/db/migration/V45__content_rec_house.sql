-- =============================================================================
-- V45 : 콘텐츠 추천고택(CONTENT_REC_HOUSE) + 추천분류 공통코드 — 콘텐츠관리 CT-W4(화면정의서 콘텐츠 §1.4/CT-15~18)
-- 설계(2026-07-08):
--   앱 홈 추천 고택 영역(당일특가/BEST/MD추천) 관리. 배너와 동형(플랫폼 소유·@PreAuthorize·status 조회산출 재사용).
--   houseId=대상 고택(존재+미삭제 검증), recCategory=REC_HOUSE_CATEGORY(TODAY_DEAL/BEST/MD_PICK).
--   생성 exposeMode로 EXPOSE_YN, PUT 전체교체, PATCH toggle, DELETE soft, PATCH order(분류 내 정렬저장).
--   목록 정렬 = SORT_ORDER ASC, SN DESC(분류별 필터). 권한 CONTENT_VIEW/CONTENT_MNG(V39) 재사용.
--   연결대상은 항상 해당 고택(별도 linkTarget 미저장 — houseId 자체가 대상). 명명·감사5·CK·utf8mb4_0900.
-- =============================================================================

-- 1) 추천고택 테이블 -----------------------------------------------------------
CREATE TABLE CONTENT_REC_HOUSE (
    CONTENT_REC_HOUSE_SN BIGINT      NOT NULL AUTO_INCREMENT COMMENT '추천고택일련번호',
    REC_CATEGORY_CD      VARCHAR(50) NOT NULL                COMMENT '추천분류(COMM_CD.GROUP_CD=REC_HOUSE_CATEGORY)',
    HOUSE_SN             BIGINT      NOT NULL                COMMENT '대상 고택(HOUSE 논리참조 — 존재+미삭제)',
    SORT_ORDER           INT         NOT NULL DEFAULT 0      COMMENT '분류 내 표시순서(드래그 정렬 저장)',
    EXPOSE_BGN_DATE      DATE        NULL                    COMMENT '노출시작일(KST)',
    EXPOSE_END_DATE      DATE        NULL                    COMMENT '노출종료일(KST)',
    EXPOSE_YN            CHAR(1)     NOT NULL DEFAULT 'Y'    COMMENT '노출여부(isActive 토글)',
    REG_DT               DATETIME(6) NOT NULL                COMMENT '등록일시',
    RGTR_SN              BIGINT      NULL                    COMMENT '등록자(사용자일련번호)',
    MDFCN_DT             DATETIME(6) NULL                    COMMENT '수정일시',
    MDFR_SN              BIGINT      NULL                    COMMENT '수정자(사용자일련번호)',
    DEL_YN               CHAR(1)     NOT NULL DEFAULT 'N'    COMMENT '삭제여부(soft delete)',
    PRIMARY KEY (CONTENT_REC_HOUSE_SN),
    KEY IX_CONTENT_REC_HOUSE_CAT (REC_CATEGORY_CD, SORT_ORDER),
    KEY IX_CONTENT_REC_HOUSE_HOUSE (HOUSE_SN),
    CONSTRAINT CK_CONTENT_REC_HOUSE_EXPOSE_YN CHECK (EXPOSE_YN IN ('Y','N')),
    CONSTRAINT CK_CONTENT_REC_HOUSE_DEL       CHECK (DEL_YN IN ('Y','N')),
    CONSTRAINT CK_CONTENT_REC_HOUSE_DATE      CHECK (EXPOSE_END_DATE IS NULL OR EXPOSE_BGN_DATE IS NULL OR EXPOSE_END_DATE >= EXPOSE_BGN_DATE)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='콘텐츠 추천고택';

-- 2) 추천분류 공통코드 ---------------------------------------------------------
INSERT INTO COMM_CD_GROUP (GROUP_CD, GROUP_NM, EXPLN, USE_YN, REG_DT, MDFCN_DT)
SELECT 'REC_HOUSE_CATEGORY', '추천고택 분류', '추천고택 상단 분류(TODAY_DEAL/BEST/MD_PICK)', 'Y', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
 WHERE NOT EXISTS (SELECT 1 FROM COMM_CD_GROUP WHERE GROUP_CD = 'REC_HOUSE_CATEGORY' AND DEL_YN = 'N');

INSERT INTO COMM_CD (GROUP_CD, CD, CD_NM, SORT_SEQ, USE_YN, REG_DT, MDFCN_DT)
SELECT 'REC_HOUSE_CATEGORY', 'TODAY_DEAL', '당일 특가', 1, 'Y', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
 WHERE NOT EXISTS (SELECT 1 FROM COMM_CD WHERE GROUP_CD = 'REC_HOUSE_CATEGORY' AND CD = 'TODAY_DEAL' AND DEL_YN = 'N');

INSERT INTO COMM_CD (GROUP_CD, CD, CD_NM, SORT_SEQ, USE_YN, REG_DT, MDFCN_DT)
SELECT 'REC_HOUSE_CATEGORY', 'BEST', 'BEST', 2, 'Y', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
 WHERE NOT EXISTS (SELECT 1 FROM COMM_CD WHERE GROUP_CD = 'REC_HOUSE_CATEGORY' AND CD = 'BEST' AND DEL_YN = 'N');

INSERT INTO COMM_CD (GROUP_CD, CD, CD_NM, SORT_SEQ, USE_YN, REG_DT, MDFCN_DT)
SELECT 'REC_HOUSE_CATEGORY', 'MD_PICK', 'MD 추천', 3, 'Y', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
 WHERE NOT EXISTS (SELECT 1 FROM COMM_CD WHERE GROUP_CD = 'REC_HOUSE_CATEGORY' AND CD = 'MD_PICK' AND DEL_YN = 'N');
