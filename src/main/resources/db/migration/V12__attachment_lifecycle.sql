-- =============================================================================
-- V12 : 통합 첨부 시스템 토대 — ATCH_FILE 연결 생명주기(TEMP/LINKED) + 스코프 확장 준비 (2026-06-18)
--   용도별 첨부(PUBLIC_IMAGE 숙소 / PROFILE_IMAGE 프로필 / REVIEW_IMAGE 후기 / INQR_ATTACH 문의)의 공통 토대.
--   선업로드-후연결(문의)·즉시연결(프로필)을 한 테이블에서 수용: 연결 생명주기는 ATCH_STTS_CD, 삭제는 DEL_YN 으로 분리.
--   소유자는 기존 RGTR_SN(등록자=업로더) 재사용. 스코프 값 검증은 앱 레벨(CHECK 미사용 — 새 용도 추가 시 ALTER 회피).
--   타임존 표준(docs/timezone-standard.md): instant 는 DATETIME(6) UTC.
-- =============================================================================

-- 1) 연결 생명주기 컬럼 추가
ALTER TABLE ATCH_FILE
    ADD COLUMN ATCH_STTS_CD   VARCHAR(20) NOT NULL DEFAULT 'LINKED'
        COMMENT '첨부연결상태코드(TEMP=업로드만/LINKED=도메인 연결) — soft delete(DEL_YN)와 분리' AFTER SCAN_DT,
    ADD COLUMN TEMP_EXPIRE_DT DATETIME(6) NULL
        COMMENT '임시파일 만료일시(TEMP 선업로드 TTL — 미연결 정리 기준)' AFTER ATCH_STTS_CD,
    ADD COLUMN LINKED_DT      DATETIME(6) NULL
        COMMENT '도메인 연결 완료 일시' AFTER TEMP_EXPIRE_DT;

-- 2) 기존 행 보정 — 이미 연결된 공개 이미지이므로 LINKED (DEFAULT 로 채워지나 명시 보정)
UPDATE ATCH_FILE SET ATCH_STTS_CD = 'LINKED' WHERE ATCH_STTS_CD IS NULL OR ATCH_STTS_CD = '';

-- 3) 인덱스
--    - 도메인별 첨부 조회: 스코프+대상+상태+미삭제
--    - TTL 정리 스캔: 상태(TEMP)+만료+미삭제
ALTER TABLE ATCH_FILE
    ADD INDEX IX_ATCH_FILE_SCOPE_TRGT  (FILE_SCOPE_CD, TRGT_TYPE, TRGT_ID, ATCH_STTS_CD, DEL_YN),
    ADD INDEX IX_ATCH_FILE_TEMP_EXPIRE (ATCH_STTS_CD, TEMP_EXPIRE_DT, DEL_YN);

-- 4) 기존 단일 스코프 인덱스 제거 — 신규 복합 인덱스(선두 FILE_SCOPE_CD)가 prefix 로 대체(중복 제거)
ALTER TABLE ATCH_FILE DROP INDEX IX_ATCH_FILE_SCOPE;
