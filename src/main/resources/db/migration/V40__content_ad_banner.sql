-- =============================================================================
-- V40 : 콘텐츠 광고배너(CONTENT_AD_BANNER) + 광고배너 노출위치 공통코드(AD_BANNER_POSITION)
-- 설계(CT-W2 계약 확정본, PM handoff 2026-07-08):
--   authoritative = 화면정의서 v3 광고배너 §1.2 / §2 / CT-09 (퍼블 content.js 폼의 지역/배너형태/1200x300은 충돌 잔재 → 제외).
--   콘텐츠 = 플랫폼(운영자) 소유 자원 → 파트너 소유권 필터 대상 아님, @PreAuthorize 권한통제.
--   status(EXPOSED/HIDDEN/ENDED)는 저장하지 않고 조회 시 (EXPOSE_YN + 노출기간, KST) 산출(ContentBannerStatus 재사용).
--   필드셋 = title/subCopy/imageUrl(선택)/positionCd(필수)/linkTargetCd/linkTargetVal/노출기간/isActive.
--   미도입: adType(띠/박스)·region(지역)·exposeMode·정렬저장(order). POST 기본 EXPOSE_YN='Y', PUT은 EXPOSE_YN 보존.
--   삭제 = soft delete(DEL_YN='Y'), 목록은 DEL_YN='N'만. 정렬 = 노출위치 코드 SORT_SEQ ASC, SN DESC.
--   재사용(신규 seed 없음): 권한 CONTENT_VIEW/CONTENT_MNG(V39), linkTarget 공통코드 CONTENT_LINK_TARGET(V39), 이미지 업로드.
--   신규 seed = AD_BANNER_POSITION(MAIN/SEARCH/RECOMMENDED_HOUSE)만. 명명·감사5컬럼·CK·utf8mb4_0900 (V39 선례).
-- =============================================================================

-- 1) 광고배너 테이블 -----------------------------------------------------------
CREATE TABLE CONTENT_AD_BANNER (
    CONTENT_AD_BANNER_SN BIGINT       NOT NULL AUTO_INCREMENT COMMENT '광고배너일련번호',
    TITLE                VARCHAR(200) NOT NULL                COMMENT '제목',
    SUB_COPY             VARCHAR(200) NULL                    COMMENT '부제',
    IMG_URL              VARCHAR(500) NULL                    COMMENT '광고이미지 URL(선택 — 자체 파일인프라 반환 경로)',
    POSITION_CD          VARCHAR(50)  NOT NULL                COMMENT '노출위치코드(COMM_CD.GROUP_CD=AD_BANNER_POSITION 논리참조)',
    LINK_TARGET_CD       VARCHAR(50)  NULL                    COMMENT '연결대상코드(COMM_CD.GROUP_CD=CONTENT_LINK_TARGET 논리참조 — V39 재사용)',
    LINK_TARGET_VAL      VARCHAR(500) NULL                    COMMENT '연결대상값(PRODUCT=houseId / URL=url / PROMOTION=promotionId)',
    EXPOSE_BGN_DATE      DATE         NULL                    COMMENT '노출시작일(KST)',
    EXPOSE_END_DATE      DATE         NULL                    COMMENT '노출종료일(KST)',
    EXPOSE_YN            CHAR(1)      NOT NULL DEFAULT 'Y'    COMMENT '노출여부(isActive 토글 — POST 기본 Y, PUT 보존, PATCH toggle만 변경)',
    REG_DT               DATETIME(6)  NOT NULL                COMMENT '등록일시',
    RGTR_SN              BIGINT       NULL                    COMMENT '등록자(사용자일련번호)',
    MDFCN_DT             DATETIME(6)  NULL                    COMMENT '수정일시',
    MDFR_SN              BIGINT       NULL                    COMMENT '수정자(사용자일련번호)',
    DEL_YN               CHAR(1)      NOT NULL DEFAULT 'N'    COMMENT '삭제여부(soft delete)',
    PRIMARY KEY (CONTENT_AD_BANNER_SN),
    KEY IX_CONTENT_AD_BANNER_POSITION (POSITION_CD),
    KEY IX_CONTENT_AD_BANNER_EXPOSE (EXPOSE_YN, EXPOSE_BGN_DATE, EXPOSE_END_DATE),
    CONSTRAINT CK_CONTENT_AD_BANNER_EXPOSE_YN CHECK (EXPOSE_YN IN ('Y','N')),
    CONSTRAINT CK_CONTENT_AD_BANNER_DEL       CHECK (DEL_YN IN ('Y','N')),
    CONSTRAINT CK_CONTENT_AD_BANNER_DATE      CHECK (EXPOSE_END_DATE IS NULL OR EXPOSE_BGN_DATE IS NULL OR EXPOSE_END_DATE >= EXPOSE_BGN_DATE)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='콘텐츠 광고배너';

-- 2) 광고배너 노출위치 공통코드 (COMM_CD_GROUP + COMM_CD) — 신규 그룹, 재실행 안전 -------
INSERT INTO COMM_CD_GROUP (GROUP_CD, GROUP_NM, EXPLN, USE_YN, REG_DT, MDFCN_DT)
SELECT 'AD_BANNER_POSITION', '광고배너 노출위치', '광고배너가 노출되는 앱 내 위치(MAIN/SEARCH/RECOMMENDED_HOUSE)', 'Y', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
 WHERE NOT EXISTS (SELECT 1 FROM COMM_CD_GROUP WHERE GROUP_CD = 'AD_BANNER_POSITION' AND DEL_YN = 'N');

INSERT INTO COMM_CD (GROUP_CD, CD, CD_NM, SORT_SEQ, USE_YN, REG_DT, MDFCN_DT)
SELECT 'AD_BANNER_POSITION', 'MAIN', '메인', 1, 'Y', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
 WHERE NOT EXISTS (SELECT 1 FROM COMM_CD WHERE GROUP_CD = 'AD_BANNER_POSITION' AND CD = 'MAIN' AND DEL_YN = 'N');

INSERT INTO COMM_CD (GROUP_CD, CD, CD_NM, SORT_SEQ, USE_YN, REG_DT, MDFCN_DT)
SELECT 'AD_BANNER_POSITION', 'SEARCH', '검색', 2, 'Y', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
 WHERE NOT EXISTS (SELECT 1 FROM COMM_CD WHERE GROUP_CD = 'AD_BANNER_POSITION' AND CD = 'SEARCH' AND DEL_YN = 'N');

INSERT INTO COMM_CD (GROUP_CD, CD, CD_NM, SORT_SEQ, USE_YN, REG_DT, MDFCN_DT)
SELECT 'AD_BANNER_POSITION', 'RECOMMENDED_HOUSE', '추천고택', 3, 'Y', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
 WHERE NOT EXISTS (SELECT 1 FROM COMM_CD WHERE GROUP_CD = 'AD_BANNER_POSITION' AND CD = 'RECOMMENDED_HOUSE' AND DEL_YN = 'N');
