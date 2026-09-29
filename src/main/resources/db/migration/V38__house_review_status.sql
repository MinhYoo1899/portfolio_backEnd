-- =============================================================================
-- V38 : 상품(고택) 검수요청 상태(REVIEW_REQUESTED) 도입
--   ① HOUSE_STTS_CD CHECK 확장(+REVIEW_REQUESTED)  ② HOUSE_REVIEW 권한 신설(SUPERVISOR)
--   ③ HOUSE_REVIEW_HSTRY 검수이력 테이블(반려사유 감사/조회 — AUDIT_LOG_INFO 엔 payload 컬럼 없음)
-- 결정(2026-07-06, 사용자/Codex): 직접 ACTIVE 발행 유지(병행) · HOUSE_REVIEW 신설 · 운영자 콘솔 한정 ·
--   REVIEW_REQUESTED 상태에서 콘텐츠 수정 시 DRAFT 자동복귀.
-- ⚠ ROLLBACK 불가(DDL 롤포워드 정책). dev schema 실제 CHECK 명은 무명(V17 인라인)이므로 동적 조회 후 drop.
-- =============================================================================

-- ----------------------------------------------------------------------------
-- 1) HOUSE_STTS_CD CHECK 확장 — 기존 무명 CHECK 동적 조회 drop → 명명 CK_HOUSE_STTS 재생성
--    (V17 인라인 무명 CHECK 는 MySQL8 자동명(house_chk_N)이라 이름 하드코딩 금지 → information_schema 조회)
-- ----------------------------------------------------------------------------
SET @ck_name := (
    SELECT tc.CONSTRAINT_NAME
      FROM information_schema.TABLE_CONSTRAINTS tc
      JOIN information_schema.CHECK_CONSTRAINTS cc
        ON cc.CONSTRAINT_SCHEMA = tc.CONSTRAINT_SCHEMA
       AND cc.CONSTRAINT_NAME   = tc.CONSTRAINT_NAME
     WHERE tc.TABLE_SCHEMA     = DATABASE()
       AND tc.TABLE_NAME       = 'HOUSE'
       AND tc.CONSTRAINT_TYPE  = 'CHECK'
       AND cc.CHECK_CLAUSE LIKE '%HOUSE_STTS_CD%'
     LIMIT 1
);
SET @drop_sql := IF(@ck_name IS NOT NULL,
    CONCAT('ALTER TABLE HOUSE DROP CHECK `', @ck_name, '`'),
    'DO 0');
PREPARE drop_stmt FROM @drop_sql;
EXECUTE drop_stmt;
DEALLOCATE PREPARE drop_stmt;

ALTER TABLE HOUSE
    ADD CONSTRAINT CK_HOUSE_STTS
    CHECK (HOUSE_STTS_CD IN ('DRAFT','ACTIVE','HIDDEN','SUSPENDED','REVIEW_REQUESTED'));

-- ----------------------------------------------------------------------------
-- 2) 권한: HOUSE_REVIEW 신설 → OPERATOR_SUPERVISOR 만 부여(STAFF 미부여). NOT EXISTS 가드(재실행/부분수습 대비).
-- ----------------------------------------------------------------------------
INSERT INTO AUTH_MST (AUTH_CD, AUTH_NM, USE_YN, REG_DT, MDFCN_DT)
SELECT 'HOUSE_REVIEW', '상품 검수(승인/반려)', 'Y', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
 WHERE NOT EXISTS (SELECT 1 FROM AUTH_MST WHERE AUTH_CD = 'HOUSE_REVIEW' AND DEL_YN = 'N');

INSERT INTO ROLE_AUTH (ROLE_SN, AUTH_SN, REG_DT, MDFCN_DT)
SELECT R.ROLE_SN, A.AUTH_SN, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
  FROM ROLE_MST R
  JOIN AUTH_MST A ON A.AUTH_CD = 'HOUSE_REVIEW' AND A.DEL_YN = 'N'
 WHERE R.ROLE_CD = 'OPERATOR_SUPERVISOR' AND R.DEL_YN = 'N'
   AND NOT EXISTS (SELECT 1 FROM ROLE_AUTH RA
                    WHERE RA.ROLE_SN = R.ROLE_SN AND RA.AUTH_SN = A.AUTH_SN AND RA.DEL_YN = 'N');

-- ----------------------------------------------------------------------------
-- 3) 검수이력 (append-only) — 반려사유 등 감사/조회 저장. PRTNR_APRV_HSTRY 패턴 계승.
--    행위(RVW_ACTN_CD): REQUEST(검수요청)/APPROVE(승인)/REJECT(반려)/REVERT(수정에 의한 자동 DRAFT 복귀).
-- ----------------------------------------------------------------------------
CREATE TABLE HOUSE_REVIEW_HSTRY (
    HOUSE_REVIEW_HSTRY_SN BIGINT      NOT NULL AUTO_INCREMENT COMMENT '고택검수이력일련번호',
    HOUSE_SN              BIGINT      NOT NULL COMMENT '고택일련번호',
    RVW_ACTN_CD           VARCHAR(20) NOT NULL COMMENT '검수행위(REQUEST/APPROVE/REJECT/REVERT)'
                          CHECK (RVW_ACTN_CD IN ('REQUEST','APPROVE','REJECT','REVERT')),
    BFR_STTS_CD           VARCHAR(20) NOT NULL COMMENT '변경전 고택상태',
    AFT_STTS_CD           VARCHAR(20) NOT NULL COMMENT '변경후 고택상태',
    ACTR_USER_SN          BIGINT      NULL     COMMENT '행위자(사용자일련번호)',
    RJCT_RSN              VARCHAR(500) NULL    COMMENT '반려사유(REJECT 시 필수, 그 외 NULL)',
    REG_DT                DATETIME(6) NOT NULL COMMENT '등록일시',
    PRIMARY KEY (HOUSE_REVIEW_HSTRY_SN),
    KEY IX_HOUSE_REVIEW_HSTRY_HOUSE (HOUSE_SN, REG_DT),
    CONSTRAINT FK_HOUSE_REVIEW_HSTRY_HOUSE FOREIGN KEY (HOUSE_SN) REFERENCES HOUSE (HOUSE_SN)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='고택검수이력';
