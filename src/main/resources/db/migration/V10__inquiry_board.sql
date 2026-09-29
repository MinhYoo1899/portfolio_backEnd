-- =============================================================================
-- V10 : 게시판형 CS 문의(Q&A) — 문의글 / 답변 + 문의유형 공통코드 + 권한(답변·게시관리)
-- 형태(사용자 확정): 회원이 작성, 운영자가 답변. 공개 게시판 + 비밀글 옵션(C형).
-- 명명(공공표준 CSV 확인): 문의=INQR(문의자명 INQR_NM), 답변=ANS(답변내용 ANS_CN),
--   비밀글=비공개게시물 PRVT_PST(비공개게시물여부 PRVT_PST_YN, 이음동의어 "비밀글여부"),
--   게시=PSTG·상태=STTS·내용=CN·제목=TTL·사유=RSN. (codex 교차검토 + 표준 대조 반영)
-- 블라인드(운영자 비노출)=게시상태 PSTG_STTS_CD(NORMAL/BLINDED) — 처리자/일시는 AUDIT_LOG_INFO(@Audit)가 기록.
-- 첨부(이미지)는 MVP 제외 — Phase 2(파일 private scope)와 함께.
-- =============================================================================

-- ----------------------------------------------------------------------------
-- 문의글
-- ----------------------------------------------------------------------------
CREATE TABLE INQR_INFO (
    INQR_SN       BIGINT       NOT NULL AUTO_INCREMENT COMMENT '문의일련번호',
    USER_SN       BIGINT       NOT NULL                COMMENT '작성자(사용자일련번호) — 소유권 판정 기준',
    INQR_TYPE_CD  VARCHAR(30)  NOT NULL                COMMENT '문의유형코드(공통코드 INQR_TYPE: RESERVATION/PAYMENT/ACCOUNT/ETC)',
    INQR_TTL      VARCHAR(200) NOT NULL                COMMENT '문의제목',
    INQR_CN       TEXT         NOT NULL                COMMENT '문의내용',
    PRVT_PST_YN   CHAR(1)      NOT NULL DEFAULT 'N'    COMMENT '비밀글(비공개게시물)여부(Y=작성자/운영자만 열람)' CHECK (PRVT_PST_YN IN ('Y','N')),
    ANS_STTS_CD   VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT '답변상태(PENDING/ANSWERED)' CHECK (ANS_STTS_CD IN ('PENDING','ANSWERED')),
    PSTG_STTS_CD  VARCHAR(20)  NOT NULL DEFAULT 'NORMAL' COMMENT '게시상태(NORMAL/BLINDED — 운영자 블라인드 처리)' CHECK (PSTG_STTS_CD IN ('NORMAL','BLINDED')),
    PSTG_STTS_RSN VARCHAR(500) NULL                    COMMENT '게시상태사유(블라인드 사유)',
    REG_DT        DATETIME(6)  NOT NULL                COMMENT '등록일시',
    RGTR_SN       BIGINT       NULL                    COMMENT '등록자(사용자일련번호)',
    MDFCN_DT      DATETIME(6)  NULL                    COMMENT '수정일시',
    MDFR_SN       BIGINT       NULL                    COMMENT '수정자(사용자일련번호)',
    DEL_YN        CHAR(1)      NOT NULL DEFAULT 'N'    COMMENT '삭제여부(작성자/운영자 삭제)' CHECK (DEL_YN IN ('Y','N')),
    PRIMARY KEY (INQR_SN),
    KEY IX_INQR_LIST (DEL_YN, PSTG_STTS_CD, REG_DT, INQR_SN),
    KEY IX_INQR_MINE (USER_SN, DEL_YN, REG_DT, INQR_SN),
    KEY IX_INQR_STTS (DEL_YN, PSTG_STTS_CD, ANS_STTS_CD, REG_DT, INQR_SN),
    KEY IX_INQR_TYPE (DEL_YN, PSTG_STTS_CD, INQR_TYPE_CD, REG_DT, INQR_SN),
    CONSTRAINT FK_INQR_INFO_USER FOREIGN KEY (USER_SN) REFERENCES USER_ACNT (USER_SN)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='문의글(게시판형 CS Q&A)';

-- ----------------------------------------------------------------------------
-- 문의답변 (운영자 — 복수 허용)
-- ----------------------------------------------------------------------------
CREATE TABLE INQR_ANS_INFO (
    ANS_SN        BIGINT       NOT NULL AUTO_INCREMENT COMMENT '답변일련번호',
    INQR_SN       BIGINT       NOT NULL                COMMENT '문의일련번호',
    ANS_USER_SN   BIGINT       NOT NULL                COMMENT '답변자(운영자 사용자일련번호)',
    ANS_CN        TEXT         NOT NULL                COMMENT '답변내용',
    REG_DT        DATETIME(6)  NOT NULL                COMMENT '등록일시',
    RGTR_SN       BIGINT       NULL                    COMMENT '등록자(사용자일련번호)',
    MDFCN_DT      DATETIME(6)  NULL                    COMMENT '수정일시',
    MDFR_SN       BIGINT       NULL                    COMMENT '수정자(사용자일련번호)',
    DEL_YN        CHAR(1)      NOT NULL DEFAULT 'N'    COMMENT '삭제여부' CHECK (DEL_YN IN ('Y','N')),
    PRIMARY KEY (ANS_SN),
    KEY IX_INQR_ANS (INQR_SN, DEL_YN, REG_DT, ANS_SN),
    CONSTRAINT FK_INQR_ANS_INQR FOREIGN KEY (INQR_SN) REFERENCES INQR_INFO (INQR_SN),
    CONSTRAINT FK_INQR_ANS_USER FOREIGN KEY (ANS_USER_SN) REFERENCES USER_ACNT (USER_SN)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='문의답변';

-- ----------------------------------------------------------------------------
-- 공통코드: 문의유형 (예약/결제/계정/기타)
-- ----------------------------------------------------------------------------
INSERT INTO COMM_CD_GROUP (GROUP_CD, GROUP_NM, EXPLN, USE_YN, REG_DT) VALUES
    ('INQR_TYPE', '문의유형', 'CS 문의 유형(예약/결제/계정/기타)', 'Y', NOW(6));

INSERT INTO COMM_CD (GROUP_CD, CD, CD_NM, SORT_SEQ, USE_YN, REG_DT) VALUES
    ('INQR_TYPE', 'RESERVATION', '예약', 1, 'Y', NOW(6)),
    ('INQR_TYPE', 'PAYMENT',     '결제', 2, 'Y', NOW(6)),
    ('INQR_TYPE', 'ACCOUNT',     '계정', 3, 'Y', NOW(6)),
    ('INQR_TYPE', 'ETC',         '기타', 4, 'Y', NOW(6));

-- ----------------------------------------------------------------------------
-- 권한: 답변 / 게시관리(블라인드) 분리 (codex 권고)
-- ----------------------------------------------------------------------------
INSERT INTO AUTH_MST (AUTH_CD, AUTH_NM, USE_YN, REG_DT) VALUES
    ('INQR_ANS',   '문의 답변(등록/수정/삭제)', 'Y', NOW(6)),
    ('INQR_MODER', '문의 게시관리(블라인드/부적절글 처리)', 'Y', NOW(6));

-- 답변 권한 → 운영자 일반직원(STAFF)  /  게시관리 권한 → 수퍼바이저(SUPERVISOR)
INSERT INTO ROLE_AUTH (ROLE_SN, AUTH_SN, REG_DT)
SELECT R.ROLE_SN, A.AUTH_SN, NOW(6)
  FROM ROLE_MST R
  JOIN AUTH_MST A
    ON (R.ROLE_CD = 'OPERATOR_STAFF'      AND A.AUTH_CD = 'INQR_ANS')
    OR (R.ROLE_CD = 'OPERATOR_SUPERVISOR' AND A.AUTH_CD = 'INQR_MODER')
 WHERE R.DEL_YN = 'N' AND A.DEL_YN = 'N'
   AND NOT EXISTS (SELECT 1 FROM ROLE_AUTH RA
                    WHERE RA.ROLE_SN = R.ROLE_SN AND RA.AUTH_SN = A.AUTH_SN AND RA.DEL_YN = 'N');

-- ----------------------------------------------------------------------------
-- 메뉴: 운영자 "문의 관리" (USER 채널 "문의"는 V9에 이미 존재). 필요권한=INQR_ANS
-- ----------------------------------------------------------------------------
INSERT INTO MENU_INFO (MENU_CHNL_CD, UPPER_MENU_SN, MENU_NM, URL_ADDR, AUTH_CD, LGN_NEED_YN, SORT_SEQ, USE_YN, REG_DT)
SELECT 'OPERATOR', NULL, '문의 관리', '/inquiries', 'INQR_ANS', 'Y',
       COALESCE((SELECT MAX(SORT_SEQ) FROM MENU_INFO WHERE MENU_CHNL_CD = 'OPERATOR' AND UPPER_MENU_SN IS NULL AND DEL_YN = 'N'), 0) + 1,
       'Y', NOW(6);
