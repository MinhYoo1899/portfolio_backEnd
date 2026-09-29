-- =============================================================================
-- V50 : DM 발송(DM_MESSAGE) + 권한(DM_VIEW/DM_MNG)
-- 설계(2026-07-08, 화면정의서 DM §1/§2/§4 확정값):
--   채널 = 알림톡(ALIMTALK)/문자(SMS) 2종. 상태 라이프사이클 DM_STTS_CD = SENT/SCHEDULED/DRAFT/FAILED(저장·전이).
--     작성: saveAsDraft→DRAFT / sendTiming=SCHEDULED→SCHEDULED / NOW→SENT(즉시발송·스텁).
--     cron(1분): SCHEDULED 중 예약시각 도래분 → SENT(스텁 발송). 실 전송은 벤더(알림톡/SMS) 미선정 → 기록만.
--   대상 = 마케팅 동의 회원(ALL=전체, SELECTED=선택). 인원수(TARGET_CNT)는 발송시점 산정(현 단계 요청값/0 기록).
--   이미지 = 자체 파일인프라 경로(선택). 권한 DM_VIEW/DM_MNG(STAFF↑). 명명·감사5·CK·utf8mb4_0900.
-- =============================================================================

CREATE TABLE DM_MESSAGE (
    DM_MESSAGE_SN    BIGINT       NOT NULL AUTO_INCREMENT COMMENT 'DM일련번호',
    TITLE            VARCHAR(200) NOT NULL                COMMENT '제목',
    CONTENT          MEDIUMTEXT   NULL                    COMMENT '내용(발송 시 필수, 임시저장 시 선택)',
    CHANNEL_CD       VARCHAR(20)  NOT NULL                COMMENT '채널(ALIMTALK/SMS)',
    IMG_URL          VARCHAR(500) NULL                    COMMENT '이미지 URL(선택, 자체 파일인프라 경로)',
    TARGET_TYPE_CD   VARCHAR(20)  NOT NULL DEFAULT 'ALL'  COMMENT '대상(ALL=전체 동의회원/SELECTED=선택)',
    TARGET_CNT       INT          NULL                    COMMENT '발송 인원(발송시점 산정)',
    SEND_TIMING_CD   VARCHAR(20)  NOT NULL DEFAULT 'NOW'  COMMENT '발송시점(NOW/SCHEDULED)',
    SCHEDULED_AT     DATETIME(6)  NULL                    COMMENT '예약 발송 일시',
    SENT_AT          DATETIME(6)  NULL                    COMMENT '발송 완료 일시',
    DM_STTS_CD       VARCHAR(20)  NOT NULL                COMMENT '상태(SENT/SCHEDULED/DRAFT/FAILED)',
    FAIL_RSN         VARCHAR(500) NULL                    COMMENT '실패 사유',
    REG_DT           DATETIME(6)  NOT NULL                COMMENT '등록일시',
    RGTR_SN          BIGINT       NULL                    COMMENT '등록자',
    MDFCN_DT         DATETIME(6)  NULL                    COMMENT '수정일시',
    MDFR_SN          BIGINT       NULL                    COMMENT '수정자',
    DEL_YN           CHAR(1)      NOT NULL DEFAULT 'N'    COMMENT '삭제여부(soft delete)',
    PRIMARY KEY (DM_MESSAGE_SN),
    KEY IX_DM_STTS (DM_STTS_CD),
    KEY IX_DM_SCHEDULED (DM_STTS_CD, SCHEDULED_AT),
    CONSTRAINT CK_DM_DEL     CHECK (DEL_YN IN ('Y','N')),
    CONSTRAINT CK_DM_CHANNEL CHECK (CHANNEL_CD IN ('ALIMTALK','SMS')),
    CONSTRAINT CK_DM_TARGET  CHECK (TARGET_TYPE_CD IN ('ALL','SELECTED')),
    CONSTRAINT CK_DM_TIMING  CHECK (SEND_TIMING_CD IN ('NOW','SCHEDULED')),
    CONSTRAINT CK_DM_STTS    CHECK (DM_STTS_CD IN ('SENT','SCHEDULED','DRAFT','FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='DM 발송';

-- 권한 (AUTH_MST + ROLE_AUTH) — CONTENT 패턴 미러(STAFF↑) -------------------------
INSERT INTO AUTH_MST (AUTH_CD, AUTH_NM, USE_YN, REG_DT)
SELECT 'DM_VIEW', 'DM 조회(운영자)', 'Y', UTC_TIMESTAMP(6)
 WHERE NOT EXISTS (SELECT 1 FROM AUTH_MST WHERE AUTH_CD = 'DM_VIEW' AND DEL_YN = 'N');

INSERT INTO AUTH_MST (AUTH_CD, AUTH_NM, USE_YN, REG_DT)
SELECT 'DM_MNG', 'DM 관리(운영자 — 발송/예약/임시저장/삭제)', 'Y', UTC_TIMESTAMP(6)
 WHERE NOT EXISTS (SELECT 1 FROM AUTH_MST WHERE AUTH_CD = 'DM_MNG' AND DEL_YN = 'N');

INSERT INTO ROLE_AUTH (ROLE_SN, AUTH_SN, REG_DT)
SELECT R.ROLE_SN, A.AUTH_SN, UTC_TIMESTAMP(6)
  FROM ROLE_MST R
  JOIN AUTH_MST A
    ON (R.ROLE_CD = 'OPERATOR_STAFF'      AND A.AUTH_CD IN ('DM_VIEW','DM_MNG'))
    OR (R.ROLE_CD = 'OPERATOR_SUPERVISOR' AND A.AUTH_CD IN ('DM_VIEW','DM_MNG'))
 WHERE R.DEL_YN = 'N' AND A.DEL_YN = 'N'
   AND NOT EXISTS (SELECT 1 FROM ROLE_AUTH RA
                    WHERE RA.ROLE_SN = R.ROLE_SN AND RA.AUTH_SN = A.AUTH_SN AND RA.DEL_YN = 'N');
