-- =============================================================================
-- V53 : 운영자 알림 설정(OPERATOR_NOTI_SETTING) — 운영자 본인 알림 채널 on/off (1:1, PK=USER_SN)
--   토글 4종: 예약(RSVT, 기본 Y)·문의(INQR, 기본 Y)·정산(STLM, 기본 N)·마케팅(MKTG, 기본 N).
--   행 부재 = 기본값(서비스가 반환, 최초 조회 시 INSERT 안 함). PUT 은 upsert(INSERT ... ON DUPLICATE KEY UPDATE).
--   ⚠ 알림함(NOTIFICATION) 적재는 후속 슬라이스 — 여기선 설정 저장소만. ROLLBACK 불가(DDL 롤포워드 정책).
-- =============================================================================
CREATE TABLE OPERATOR_NOTI_SETTING (
    USER_SN       BIGINT      NOT NULL                 COMMENT '사용자일련번호(운영자, PK)',
    RSVT_NOTI_YN  CHAR(1)     NOT NULL DEFAULT 'Y'     COMMENT '예약 알림 수신여부'   CHECK (RSVT_NOTI_YN IN ('Y','N')),
    INQR_NOTI_YN  CHAR(1)     NOT NULL DEFAULT 'Y'     COMMENT '문의 알림 수신여부'   CHECK (INQR_NOTI_YN IN ('Y','N')),
    STLM_NOTI_YN  CHAR(1)     NOT NULL DEFAULT 'N'     COMMENT '정산 알림 수신여부'   CHECK (STLM_NOTI_YN IN ('Y','N')),
    MKTG_NOTI_YN  CHAR(1)     NOT NULL DEFAULT 'N'     COMMENT '마케팅 알림 수신여부' CHECK (MKTG_NOTI_YN IN ('Y','N')),
    REG_DT        DATETIME(6) NOT NULL                 COMMENT '등록일시',
    MDFCN_DT      DATETIME(6) NOT NULL                 COMMENT '수정일시',
    RGTR_SN       BIGINT      NOT NULL                 COMMENT '등록자(사용자일련번호)',
    MDFR_SN       BIGINT      NULL                     COMMENT '수정자(사용자일련번호)',
    DEL_YN        CHAR(1)     NOT NULL DEFAULT 'N'     COMMENT '삭제여부' CHECK (DEL_YN IN ('Y','N')),
    PRIMARY KEY (USER_SN),
    CONSTRAINT FK_OPERATOR_NOTI_SETTING_USER FOREIGN KEY (USER_SN) REFERENCES USER_ACNT (USER_SN)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='운영자 알림 설정';
