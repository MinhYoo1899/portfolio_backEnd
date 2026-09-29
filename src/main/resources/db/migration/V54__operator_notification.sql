-- =============================================================================
-- V54 : 운영자 알림함(OPERATOR_NOTIFICATION) — 수신자(운영자)별 1행. ③a 코어(도메인 훅은 ③b).
--   타입 8종: PARTNER_AUTH/PRODUCT_REVIEW(항상) · RESERVATION/INQUIRY/SETTLEMENT/COUPON/NOTICE/DM(설정 게이팅, ②).
--   중복방지: UNIQUE(RCVR_USER_SN, NOTI_TYPE_CD, EVENT_KEY) — 같은 (수신자+유형) 에 같은 eventKey 를 두 번 적재하지 않음(멱등 INSERT).
--   읽음=본인 행만, 90일 조회·보관. LINK_URL=서버 allowlist 산출값만. 감사5컬럼(REG/MDFCN/RGTR/MDFR/DEL_YN).
--   ROLLBACK 불가(DDL 롤포워드 정책).
-- =============================================================================
CREATE TABLE OPERATOR_NOTIFICATION (
    OPERATOR_NOTI_SN BIGINT       NOT NULL AUTO_INCREMENT COMMENT '운영자알림일련번호',
    RCVR_USER_SN     BIGINT       NOT NULL                COMMENT '수신자(운영자) 사용자일련번호',
    NOTI_TYPE_CD     VARCHAR(20)  NOT NULL                COMMENT '알림유형(PARTNER_AUTH/PRODUCT_REVIEW/RESERVATION/INQUIRY/SETTLEMENT/COUPON/NOTICE/DM)'
                     CHECK (NOTI_TYPE_CD IN ('PARTNER_AUTH','PRODUCT_REVIEW','RESERVATION','INQUIRY','SETTLEMENT','COUPON','NOTICE','DM')),
    EVENT_KEY        VARCHAR(100) NOT NULL                COMMENT '이벤트키(수신자별 중복방지 — 같은 이벤트 재적재 차단)',
    NOTI_TTL         VARCHAR(200) NOT NULL                COMMENT '제목',
    NOTI_BODY        VARCHAR(1000) NULL                   COMMENT '내용',
    TRGT_TYPE_CD     VARCHAR(30)  NOT NULL                COMMENT '대상 엔티티 유형(유형이 소유 — HOUSE/PARTNER/RESERVATION 등)',
    TRGT_ID          VARCHAR(64)  NOT NULL                COMMENT '대상 엔티티 식별자(숫자)',
    LINK_URL         VARCHAR(300) NOT NULL                COMMENT '이동 링크(서버 allowlist 매핑값만)',
    READ_YN          CHAR(1)      NOT NULL DEFAULT 'N'    COMMENT '읽음여부' CHECK (READ_YN IN ('Y','N')),
    READ_DT          DATETIME(6)  NULL                    COMMENT '읽은일시',
    REG_DT           DATETIME(6)  NOT NULL                COMMENT '등록일시',
    MDFCN_DT         DATETIME(6)  NOT NULL                COMMENT '수정일시',
    RGTR_SN          BIGINT       NOT NULL                COMMENT '등록자(이벤트 유발 행위자 사용자일련번호)',
    MDFR_SN          BIGINT       NULL                    COMMENT '수정자(읽음 처리자 사용자일련번호)',
    DEL_YN           CHAR(1)      NOT NULL DEFAULT 'N'    COMMENT '삭제여부' CHECK (DEL_YN IN ('Y','N')),
    PRIMARY KEY (OPERATOR_NOTI_SN),
    UNIQUE KEY UK_OPERATOR_NOTI_EVENT (RCVR_USER_SN, NOTI_TYPE_CD, EVENT_KEY),
    KEY IX_OPERATOR_NOTI_RCVR (RCVR_USER_SN, READ_YN, REG_DT),
    KEY IX_OPERATOR_NOTI_REG (REG_DT),
    CONSTRAINT FK_OPERATOR_NOTI_RCVR FOREIGN KEY (RCVR_USER_SN) REFERENCES USER_ACNT (USER_SN)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='운영자 알림함';
