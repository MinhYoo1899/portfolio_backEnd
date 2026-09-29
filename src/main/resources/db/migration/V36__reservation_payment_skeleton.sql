-- =============================================================================
-- V36 : 예약(Reservation) 도메인 스키마 + 결제(Payment) 골격
-- 설계(사용자+codex 확정 2026-07-01, reservation-domain-design / backlog-product-schema-checklist):
--   예약=[CHECK_IN_DATE, CHECK_OUT_DATE) 반개구간(체크아웃일은 숙박 안 함). 숙박일=KST LocalDate, 감사/만료 시각=UTC(6).
--   재고 최종확정 = ROOM_CALENDAR.SOLD_QTY 조건부 UPDATE(오버부킹은 CK_ROOM_CALENDAR_QTY 최후 가드). HOLD=Redis(advisory, DB 무저장).
--   금액=정수 KRW + 통화코드 + 스냅샷(가격 변동 무관 증적) — 총액/객실소계/일자별 단가(RESERVATION_ITEM_NIGHT) 3단.
--   멱등: ORDER_NO 전역 UNIQUE / IDEMPOTENCY_KEY 는 (RESERVER_USER_SN, IDEMPOTENCY_KEY) 예약자별 UNIQUE(Redis scope=userSn 차원 정합, 전역 아님). IDOR 소유권 컬럼(PRTNR_SN/RESERVER_USER_SN 등) 스키마에 고정.
--   PII: RESERVATION_GUEST 분리(전화 AES-GCM TypeHandler) + RESERVATION_AGRE 동의 스냅샷 + PII_PURGE_DUE_DT.
--   상태머신: 예약 HOLD/PAYMENT_PENDING/RESERVED/USED/CANCELLED/EXPIRED, 결제 READY/APPROVED/FAILED/CANCELLED/REFUNDED.
--   결제는 골격만(테이블+상태값+UNIQUE) — 실 PG(Toss) 연동·웹훅 멱등(PAYMENT_EVENT)은 후속 슬라이스에서 추가.
-- 명명: 예약=RESERVATION(ROOM/HOUSE 선례 full), 동의=AGRE, 만료=EXPRY, 승인=APRV, 파기=PII_PURGE. 감사 5컬럼·_SN 통일.
-- =============================================================================

-- 1) 예약 헤더 ---------------------------------------------------------------
CREATE TABLE RESERVATION (
    RESERVATION_SN      BIGINT      NOT NULL AUTO_INCREMENT COMMENT '예약일련번호',
    ORDER_NO            VARCHAR(40) NOT NULL                COMMENT '주문번호(사용자 노출·결제 대응)',
    IDEMPOTENCY_KEY     VARCHAR(80) NULL                    COMMENT '멱등키(생성 재시도 중복예약 차단 — 예약자별 UNIQUE(RESERVER_USER_SN 스코프), 전역 아님. 기술필드)',
    RESERVATION_STTS_CD VARCHAR(20) NOT NULL DEFAULT 'HOLD' COMMENT '예약상태코드(HOLD/PAYMENT_PENDING/RESERVED/USED/CANCELLED/EXPIRED)',
    PRTNR_SN            BIGINT      NOT NULL                COMMENT '파트너일련번호(소유권 — 판매자)',
    HOUSE_SN            BIGINT      NOT NULL                COMMENT '고택일련번호',
    ROOM_SN             BIGINT      NOT NULL                COMMENT '객실일련번호',
    RESERVER_USER_SN    BIGINT      NOT NULL                COMMENT '예약자(사용자일련번호 — EEUM 회원)',
    CHECK_IN_DATE       DATE        NOT NULL                COMMENT '체크인 숙박일(KST, 포함)',
    CHECK_OUT_DATE      DATE        NOT NULL                COMMENT '체크아웃일(KST, 미포함 — 반개구간)',
    NGT_CNT             INT         NOT NULL                COMMENT '박수',
    GUEST_CNT           INT         NOT NULL DEFAULT 1      COMMENT '투숙인원수',
    TOTAL_AMT           INT         NOT NULL                COMMENT '총결제금액(KRW 정수, 스냅샷)',
    CRNC_CD             CHAR(3)     NOT NULL DEFAULT 'KRW'  COMMENT '통화코드',
    HOLD_EXPRY_DT       DATETIME(6) NULL                    COMMENT '홀드만료일시(UTC — HOLD/PAYMENT_PENDING 한정)',
    HOLD_ID             VARCHAR(64) NULL                    COMMENT '소프트홀드 식별자(Redis ZSET member 접두 — release 대상)',
    REG_DT              DATETIME(6) NOT NULL                COMMENT '등록일시',
    RGTR_SN             BIGINT      NULL                    COMMENT '등록자(사용자일련번호)',
    MDFCN_DT            DATETIME(6) NULL                    COMMENT '수정일시',
    MDFR_SN             BIGINT      NULL                    COMMENT '수정자(사용자일련번호)',
    DEL_YN              CHAR(1)     NOT NULL DEFAULT 'N'    COMMENT '삭제여부',
    PRIMARY KEY (RESERVATION_SN),
    UNIQUE KEY UK_RESERVATION_ORDER_NO (ORDER_NO),
    UNIQUE KEY UK_RESERVATION_IDEMPOTENCY (RESERVER_USER_SN, IDEMPOTENCY_KEY),
    KEY IX_RESERVATION_RESERVER (RESERVER_USER_SN),
    KEY IX_RESERVATION_PRTNR (PRTNR_SN),
    KEY IX_RESERVATION_ROOM (ROOM_SN),
    KEY IX_RESERVATION_STTS (RESERVATION_STTS_CD),
    CONSTRAINT FK_RESERVATION_ROOM     FOREIGN KEY (ROOM_SN)          REFERENCES ROOM (ROOM_SN),
    CONSTRAINT FK_RESERVATION_HOUSE    FOREIGN KEY (HOUSE_SN)         REFERENCES HOUSE (HOUSE_SN),
    CONSTRAINT FK_RESERVATION_PRTNR    FOREIGN KEY (PRTNR_SN)         REFERENCES PRTNR_MST (PRTNR_SN),
    CONSTRAINT FK_RESERVATION_RESERVER FOREIGN KEY (RESERVER_USER_SN) REFERENCES USER_ACNT (USER_SN),
    CONSTRAINT CK_RESERVATION_DATE  CHECK (CHECK_OUT_DATE > CHECK_IN_DATE),
    CONSTRAINT CK_RESERVATION_NGT   CHECK (NGT_CNT > 0),
    CONSTRAINT CK_RESERVATION_GUEST CHECK (GUEST_CNT > 0),
    CONSTRAINT CK_RESERVATION_AMT   CHECK (TOTAL_AMT >= 0),
    CONSTRAINT CK_RESERVATION_STTS  CHECK (RESERVATION_STTS_CD IN ('HOLD','PAYMENT_PENDING','RESERVED','USED','CANCELLED','EXPIRED')),
    CONSTRAINT CK_RESERVATION_DEL   CHECK (DEL_YN IN ('Y','N'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='예약(헤더)';

-- 2) 예약 객실(항목) — MVP 1객실이나 다객실 확장 대비 -----------------------
CREATE TABLE RESERVATION_ITEM (
    RESERVATION_ITEM_SN BIGINT       NOT NULL AUTO_INCREMENT COMMENT '예약항목일련번호',
    RESERVATION_SN      BIGINT       NOT NULL                COMMENT '예약일련번호',
    ROOM_SN             BIGINT       NOT NULL                COMMENT '객실일련번호',
    ROOM_NM_SNAP        VARCHAR(100) NOT NULL                COMMENT '객실명 스냅샷(예약시점)',
    ITEM_AMT            INT          NOT NULL                COMMENT '객실 소계금액(KRW 정수, 스냅샷)',
    ROOM_QTY            INT          NOT NULL DEFAULT 1      COMMENT '예약 객실수량',
    REG_DT              DATETIME(6)  NOT NULL                COMMENT '등록일시',
    RGTR_SN             BIGINT       NULL                    COMMENT '등록자(사용자일련번호)',
    MDFCN_DT            DATETIME(6)  NULL                    COMMENT '수정일시',
    MDFR_SN             BIGINT       NULL                    COMMENT '수정자(사용자일련번호)',
    DEL_YN              CHAR(1)      NOT NULL DEFAULT 'N'    COMMENT '삭제여부',
    PRIMARY KEY (RESERVATION_ITEM_SN),
    KEY IX_RESERVATION_ITEM_RSV (RESERVATION_SN),
    CONSTRAINT FK_RESERVATION_ITEM_RSV  FOREIGN KEY (RESERVATION_SN) REFERENCES RESERVATION (RESERVATION_SN),
    CONSTRAINT FK_RESERVATION_ITEM_ROOM FOREIGN KEY (ROOM_SN)        REFERENCES ROOM (ROOM_SN),
    CONSTRAINT CK_RESERVATION_ITEM_AMT CHECK (ITEM_AMT >= 0),
    CONSTRAINT CK_RESERVATION_ITEM_QTY CHECK (ROOM_QTY > 0),
    CONSTRAINT CK_RESERVATION_ITEM_DEL CHECK (DEL_YN IN ('Y','N'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='예약 객실(항목)';

-- 3) 예약 일자별 단가 스냅샷 — codex 함정④(총액만 저장 금지) 방지 --------------
CREATE TABLE RESERVATION_ITEM_NIGHT (
    RESERVATION_ITEM_NIGHT_SN BIGINT      NOT NULL AUTO_INCREMENT COMMENT '예약일자단가일련번호',
    RESERVATION_ITEM_SN       BIGINT      NOT NULL                COMMENT '예약항목일련번호',
    STAY_DATE                 DATE        NOT NULL                COMMENT '숙박일(KST)',
    NIGHT_PRICE_AMT           INT         NOT NULL                COMMENT '1박 단가(KRW 정수, 예약시점 스냅샷)',
    NIGHT_QTY                 INT         NOT NULL DEFAULT 1      COMMENT '해당 숙박일 점유수량',
    REG_DT                    DATETIME(6) NOT NULL                COMMENT '등록일시',
    RGTR_SN                   BIGINT      NULL                    COMMENT '등록자(사용자일련번호)',
    MDFCN_DT                  DATETIME(6) NULL                    COMMENT '수정일시',
    MDFR_SN                   BIGINT      NULL                    COMMENT '수정자(사용자일련번호)',
    DEL_YN                    CHAR(1)     NOT NULL DEFAULT 'N'    COMMENT '삭제여부',
    PRIMARY KEY (RESERVATION_ITEM_NIGHT_SN),
    UNIQUE KEY UK_RESERVATION_ITEM_NIGHT (RESERVATION_ITEM_SN, STAY_DATE),
    CONSTRAINT FK_RESERVATION_ITEM_NIGHT_ITEM FOREIGN KEY (RESERVATION_ITEM_SN) REFERENCES RESERVATION_ITEM (RESERVATION_ITEM_SN),
    CONSTRAINT CK_RESERVATION_ITEM_NIGHT_PRICE CHECK (NIGHT_PRICE_AMT >= 0),
    CONSTRAINT CK_RESERVATION_ITEM_NIGHT_QTY   CHECK (NIGHT_QTY > 0),
    CONSTRAINT CK_RESERVATION_ITEM_NIGHT_DEL   CHECK (DEL_YN IN ('Y','N'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='예약 일자별 단가 스냅샷';

-- 4) 예약 게스트(PII 분리) — 전화번호 AES-GCM TypeHandler(GuestMblTelno) ------
--    GUEST_NM 은 이름(crypto-standard §1 암호화 제외 대상)이라 평문. GUEST_MBL_TELNO 는 암호문(512=GCM+Base64 여유).
--    전화 검색 필요 시 GUEST_MBL_TELNO_HASH(HMAC, CHAR(64) ascii_bin) 컬럼은 후속 ALTER 로 추가(crypto-standard §3).
CREATE TABLE RESERVATION_GUEST (
    RESERVATION_GUEST_SN BIGINT       NOT NULL AUTO_INCREMENT COMMENT '예약게스트일련번호',
    RESERVATION_SN       BIGINT       NOT NULL                COMMENT '예약일련번호',
    GUEST_TYPE_CD        VARCHAR(20)  NOT NULL                COMMENT '게스트유형코드(RESERVER=예약자/OCCUPANT=투숙자)',
    GUEST_NM             VARCHAR(100) NULL                    COMMENT '게스트명(평문 — 암호화 제외 대상)',
    GUEST_MBL_TELNO      VARCHAR(512) NULL                    COMMENT '게스트 휴대전화번호(AES-256-GCM 암호문)',
    PII_PURGE_DUE_DT     DATETIME(6)  NULL                    COMMENT '개인정보 파기예정일시(UTC)',
    REG_DT               DATETIME(6)  NOT NULL                COMMENT '등록일시',
    RGTR_SN              BIGINT       NULL                    COMMENT '등록자(사용자일련번호)',
    MDFCN_DT             DATETIME(6)  NULL                    COMMENT '수정일시',
    MDFR_SN              BIGINT       NULL                    COMMENT '수정자(사용자일련번호)',
    DEL_YN               CHAR(1)      NOT NULL DEFAULT 'N'    COMMENT '삭제여부',
    PRIMARY KEY (RESERVATION_GUEST_SN),
    KEY IX_RESERVATION_GUEST_RSV (RESERVATION_SN),
    CONSTRAINT FK_RESERVATION_GUEST_RSV FOREIGN KEY (RESERVATION_SN) REFERENCES RESERVATION (RESERVATION_SN),
    CONSTRAINT CK_RESERVATION_GUEST_TYPE CHECK (GUEST_TYPE_CD IN ('RESERVER','OCCUPANT')),
    CONSTRAINT CK_RESERVATION_GUEST_DEL  CHECK (DEL_YN IN ('Y','N'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='예약 게스트(개인정보 분리)';

-- 5) 예약 동의 스냅샷(약관버전·동의시각·목적·항목) ----------------------------
--    약관 스냅샷 무결성 우선(약관 행 변경/삭제돼도 동의 증적 보존) → TRMS_SN 강제 FK 미부여, 버전번호 스냅샷 보관.
CREATE TABLE RESERVATION_AGRE (
    RESERVATION_AGRE_SN BIGINT       NOT NULL AUTO_INCREMENT COMMENT '예약동의일련번호',
    RESERVATION_SN      BIGINT       NOT NULL                COMMENT '예약일련번호',
    TRMS_SN             BIGINT       NULL                    COMMENT '약관일련번호(스냅샷 참조 — 강제 FK 없음)',
    TRMS_VER_NO         VARCHAR(20)  NULL                    COMMENT '약관버전번호(동의시점 스냅샷)',
    AGRE_PRPS_CD        VARCHAR(40)  NOT NULL                COMMENT '동의목적코드(예: RESERVATION_FULFILLMENT/THIRD_PARTY_PROVISION)',
    AGRE_ITEM_CN        VARCHAR(500) NULL                    COMMENT '동의(수집)항목내용 스냅샷',
    AGRE_DT             DATETIME(6)  NOT NULL                COMMENT '동의일시(UTC)',
    REG_DT              DATETIME(6)  NOT NULL                COMMENT '등록일시',
    RGTR_SN             BIGINT       NULL                    COMMENT '등록자(사용자일련번호)',
    MDFCN_DT            DATETIME(6)  NULL                    COMMENT '수정일시',
    MDFR_SN             BIGINT       NULL                    COMMENT '수정자(사용자일련번호)',
    DEL_YN              CHAR(1)      NOT NULL DEFAULT 'N'    COMMENT '삭제여부',
    PRIMARY KEY (RESERVATION_AGRE_SN),
    KEY IX_RESERVATION_AGRE_RSV (RESERVATION_SN),
    CONSTRAINT FK_RESERVATION_AGRE_RSV FOREIGN KEY (RESERVATION_SN) REFERENCES RESERVATION (RESERVATION_SN),
    CONSTRAINT CK_RESERVATION_AGRE_DEL CHECK (DEL_YN IN ('Y','N'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='예약 개인정보 동의 스냅샷';

-- 6) 결제(골격) — 상태값+UNIQUE(orderNo/paymentKey/txId)만. 로직/웹훅은 후속 --
CREATE TABLE PAYMENT (
    PAYMENT_SN       BIGINT       NOT NULL AUTO_INCREMENT COMMENT '결제일련번호',
    RESERVATION_SN   BIGINT       NOT NULL                COMMENT '예약일련번호',
    ORDER_NO         VARCHAR(40)  NOT NULL                COMMENT '주문번호(RESERVATION.ORDER_NO 대응)',
    PAYMENT_STTS_CD  VARCHAR(20)  NOT NULL DEFAULT 'READY' COMMENT '결제상태코드(READY/APPROVED/FAILED/CANCELLED/REFUNDED)',
    PG_PRVDR_CD      VARCHAR(20)  NULL                    COMMENT 'PG제공자코드(예: TOSS)',
    PAYMENT_KEY      VARCHAR(200) NULL                    COMMENT 'PG 결제키(외부 기술식별자)',
    PG_TX_ID         VARCHAR(200) NULL                    COMMENT 'PG 거래식별자(외부 기술식별자)',
    PAY_AMT          INT          NOT NULL DEFAULT 0      COMMENT '결제금액(KRW 정수)',
    CRNC_CD          CHAR(3)      NOT NULL DEFAULT 'KRW'  COMMENT '통화코드',
    APRV_DT          DATETIME(6)  NULL                    COMMENT '승인일시(UTC)',
    REG_DT           DATETIME(6)  NOT NULL                COMMENT '등록일시',
    RGTR_SN          BIGINT       NULL                    COMMENT '등록자(사용자일련번호)',
    MDFCN_DT         DATETIME(6)  NULL                    COMMENT '수정일시',
    MDFR_SN          BIGINT       NULL                    COMMENT '수정자(사용자일련번호)',
    DEL_YN           CHAR(1)      NOT NULL DEFAULT 'N'    COMMENT '삭제여부',
    PRIMARY KEY (PAYMENT_SN),
    UNIQUE KEY UK_PAYMENT_KEY (PAYMENT_KEY),
    UNIQUE KEY UK_PAYMENT_TX (PG_TX_ID),
    KEY IX_PAYMENT_RSV (RESERVATION_SN),
    CONSTRAINT FK_PAYMENT_RSV FOREIGN KEY (RESERVATION_SN) REFERENCES RESERVATION (RESERVATION_SN),
    CONSTRAINT CK_PAYMENT_AMT  CHECK (PAY_AMT >= 0),
    CONSTRAINT CK_PAYMENT_STTS CHECK (PAYMENT_STTS_CD IN ('READY','APPROVED','FAILED','CANCELLED','REFUNDED')),
    CONSTRAINT CK_PAYMENT_DEL  CHECK (DEL_YN IN ('Y','N'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='결제(골격 — 실 PG 연동 전)';
