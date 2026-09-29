-- =============================================================================
-- V39 : 콘텐츠 메인배너(CONTENT_MAIN_BANNER) + 콘텐츠 권한(CONTENT_VIEW/CONTENT_MNG)
--        + linkTarget 공통코드(CONTENT_LINK_TARGET: PRODUCT/URL/PROMOTION)
-- 설계(CT-W0 계약 확정본, PM 조건부승인 반영 2026-07-07):
--   콘텐츠 = 플랫폼(운영자) 소유 자원 → 파트너 소유권 필터 대상 아님, @PreAuthorize 권한통제.
--   status(EXPOSED/HIDDEN/ENDED)는 저장하지 않고 조회 시 (EXPOSE_YN + 노출기간, KST 기준일) 산출.
--   순서저장 API 현 단계 제외(SORT_ORDER 컬럼만 예약). 삭제 API 제외(DEL_YN 컬럼만 예약).
--   권한: CONTENT_VIEW(조회) / CONTENT_MNG(등록·수정·토글·업로드) — 둘 다 STAFF↑(V27 MEMBER 패턴 미러).
--   linkTarget: PRODUCT(houseId 존재+미삭제) / URL(http|https) / PROMOTION(문자열 필수만, 대상검증 보류).
--   명명 = CONTENT_MAIN_BANNER(full word), 감사 5컬럼·CK·utf8mb4_0900 (V36/V27 선례). 권한/코드 seed 재실행 안전(NOT EXISTS).
-- =============================================================================

-- 1) 메인배너 테이블 -----------------------------------------------------------
CREATE TABLE CONTENT_MAIN_BANNER (
    CONTENT_MAIN_BANNER_SN BIGINT       NOT NULL AUTO_INCREMENT COMMENT '메인배너일련번호',
    SORT_ORDER             INT          NOT NULL DEFAULT 0      COMMENT '표시순서(현단계 정렬저장 제외 — 서버 기본/초기값)',
    IMG_URL                VARCHAR(500) NOT NULL                COMMENT '배경이미지 URL(자체 파일인프라 반환 경로)',
    MAIN_COPY              VARCHAR(200) NOT NULL                COMMENT '메인카피',
    SUB_COPY               VARCHAR(200) NULL                    COMMENT '서브카피',
    LINK_TARGET_CD         VARCHAR(50)  NULL                    COMMENT '연결대상코드(COMM_CD.GROUP_CD=CONTENT_LINK_TARGET 논리참조)',
    LINK_TARGET_VAL        VARCHAR(500) NULL                    COMMENT '연결대상값(PRODUCT=houseId / URL=url / PROMOTION=promotionId)',
    EXPOSE_BGN_DATE        DATE         NULL                    COMMENT '노출시작일(KST)',
    EXPOSE_END_DATE        DATE         NULL                    COMMENT '노출종료일(KST)',
    EXPOSE_YN              CHAR(1)      NOT NULL DEFAULT 'Y'    COMMENT '노출여부(isActive 토글)',
    REG_DT                 DATETIME(6)  NOT NULL                COMMENT '등록일시',
    RGTR_SN                BIGINT       NULL                    COMMENT '등록자(사용자일련번호)',
    MDFCN_DT               DATETIME(6)  NULL                    COMMENT '수정일시',
    MDFR_SN                BIGINT       NULL                    COMMENT '수정자(사용자일련번호)',
    DEL_YN                 CHAR(1)      NOT NULL DEFAULT 'N'    COMMENT '삭제여부(소프트 — API는 현단계 제외)',
    PRIMARY KEY (CONTENT_MAIN_BANNER_SN),
    KEY IX_CONTENT_MAIN_BANNER_EXPOSE (EXPOSE_YN, EXPOSE_BGN_DATE, EXPOSE_END_DATE),
    CONSTRAINT CK_CONTENT_MAIN_BANNER_EXPOSE_YN CHECK (EXPOSE_YN IN ('Y','N')),
    CONSTRAINT CK_CONTENT_MAIN_BANNER_DEL       CHECK (DEL_YN IN ('Y','N')),
    CONSTRAINT CK_CONTENT_MAIN_BANNER_DATE      CHECK (EXPOSE_END_DATE IS NULL OR EXPOSE_BGN_DATE IS NULL OR EXPOSE_END_DATE >= EXPOSE_BGN_DATE)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='콘텐츠 메인배너';

-- 2) 콘텐츠 권한 (AUTH_MST + ROLE_AUTH) — V27 MEMBER 권한 패턴 미러 ----------------
INSERT INTO AUTH_MST (AUTH_CD, AUTH_NM, USE_YN, REG_DT)
SELECT 'CONTENT_VIEW', '콘텐츠 조회(운영자 — 배너/팝업 등 목록·상세)', 'Y', UTC_TIMESTAMP(6)
 WHERE NOT EXISTS (SELECT 1 FROM AUTH_MST WHERE AUTH_CD = 'CONTENT_VIEW' AND DEL_YN = 'N');

INSERT INTO AUTH_MST (AUTH_CD, AUTH_NM, USE_YN, REG_DT)
SELECT 'CONTENT_MNG', '콘텐츠 관리(운영자 — 등록/수정/노출토글/이미지업로드)', 'Y', UTC_TIMESTAMP(6)
 WHERE NOT EXISTS (SELECT 1 FROM AUTH_MST WHERE AUTH_CD = 'CONTENT_MNG' AND DEL_YN = 'N');

INSERT INTO ROLE_AUTH (ROLE_SN, AUTH_SN, REG_DT)
SELECT R.ROLE_SN, A.AUTH_SN, UTC_TIMESTAMP(6)
  FROM ROLE_MST R
  JOIN AUTH_MST A
    ON (R.ROLE_CD = 'OPERATOR_STAFF'      AND A.AUTH_CD IN ('CONTENT_VIEW','CONTENT_MNG'))
    OR (R.ROLE_CD = 'OPERATOR_SUPERVISOR' AND A.AUTH_CD IN ('CONTENT_VIEW','CONTENT_MNG'))
 WHERE R.DEL_YN = 'N' AND A.DEL_YN = 'N'
   AND NOT EXISTS (SELECT 1 FROM ROLE_AUTH RA
                    WHERE RA.ROLE_SN = R.ROLE_SN AND RA.AUTH_SN = A.AUTH_SN AND RA.DEL_YN = 'N');

-- 3) linkTarget 공통코드 (COMM_CD_GROUP + COMM_CD) — 신규 그룹, 재실행 안전 ---------
INSERT INTO COMM_CD_GROUP (GROUP_CD, GROUP_NM, EXPLN, USE_YN, REG_DT, MDFCN_DT)
SELECT 'CONTENT_LINK_TARGET', '콘텐츠 연결대상', '콘텐츠(배너/팝업 등) 연결 대상 유형(PRODUCT/URL/PROMOTION)', 'Y', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
 WHERE NOT EXISTS (SELECT 1 FROM COMM_CD_GROUP WHERE GROUP_CD = 'CONTENT_LINK_TARGET' AND DEL_YN = 'N');

INSERT INTO COMM_CD (GROUP_CD, CD, CD_NM, SORT_SEQ, USE_YN, REG_DT, MDFCN_DT)
SELECT 'CONTENT_LINK_TARGET', 'PRODUCT', '상품(고택) 상세', 1, 'Y', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
 WHERE NOT EXISTS (SELECT 1 FROM COMM_CD WHERE GROUP_CD = 'CONTENT_LINK_TARGET' AND CD = 'PRODUCT' AND DEL_YN = 'N');

INSERT INTO COMM_CD (GROUP_CD, CD, CD_NM, SORT_SEQ, USE_YN, REG_DT, MDFCN_DT)
SELECT 'CONTENT_LINK_TARGET', 'URL', '외부 URL', 2, 'Y', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
 WHERE NOT EXISTS (SELECT 1 FROM COMM_CD WHERE GROUP_CD = 'CONTENT_LINK_TARGET' AND CD = 'URL' AND DEL_YN = 'N');

INSERT INTO COMM_CD (GROUP_CD, CD, CD_NM, SORT_SEQ, USE_YN, REG_DT, MDFCN_DT)
SELECT 'CONTENT_LINK_TARGET', 'PROMOTION', '기획전 페이지', 3, 'Y', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
 WHERE NOT EXISTS (SELECT 1 FROM COMM_CD WHERE GROUP_CD = 'CONTENT_LINK_TARGET' AND CD = 'PROMOTION' AND DEL_YN = 'N');
