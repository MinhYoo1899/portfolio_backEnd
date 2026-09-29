-- =============================================================================
-- V18 : 재고/요금 캘린더 — ROOM_CALENDAR (객실 × 숙박일 일자별 재고/가격/마감)
-- 설계(사용자+codex 확정 2026-06-24):
--   재고 동시성 = Redis 임시점유(HOLD, 결제 대기 중 비노출+TTL) + DB 최종확정(결제완료 시 SOLD_QTY 차감, 오버부킹 원천차단).
--   가용수량 = TOTAL_QTY - SOLD_QTY(DB) - HELD(Redis). "행 없으면 판매 불가"(오버셀 방지) — 캘린더에 없는 날짜는 판매 안 함.
--   가격 = 날짜/요일별 다름. 파트너가 기간+요일 일괄 설정(인라인 PRICE_AMT). 가격룰 테이블은 1차 제외.
--   STAY_DATE = KST 숙박일(LocalDate). 감사 시각은 UTC_TIMESTAMP(6)(타임존 표준).
-- 소유권 = ROOM→HOUSE.PRTNR_SN(파트너). 일자별 재고 row 트랜잭션 잠금(FOR UPDATE)으로 최종확정 정합.
-- 캘린더 행은 soft delete 없음 — "판매 중지"는 CLOSED_YN 로 표현(행 삭제 아님).
-- =============================================================================

CREATE TABLE ROOM_CALENDAR (
    ROOM_CALENDAR_SN BIGINT      NOT NULL AUTO_INCREMENT COMMENT '객실캘린더일련번호',
    ROOM_SN          BIGINT      NOT NULL                COMMENT '객실일련번호',
    STAY_DATE        DATE        NOT NULL                COMMENT '숙박일(KST LocalDate)',
    TOTAL_QTY        INT         NOT NULL DEFAULT 0      COMMENT '판매가능 총수량(파트너 설정)',
    SOLD_QTY         INT         NOT NULL DEFAULT 0      COMMENT '확정 판매수량(DB 최종확정 기준 — HELD는 Redis)',
    PRICE_AMT        INT         NOT NULL DEFAULT 0      COMMENT '1박 판매가(KRW 정수)',
    CLOSED_YN        CHAR(1)     NOT NULL DEFAULT 'N'    COMMENT '마감여부(Y=판매중지)' CHECK (CLOSED_YN IN ('Y','N')),
    REG_DT           DATETIME(6) NOT NULL                COMMENT '등록일시',
    RGTR_SN          BIGINT      NULL                    COMMENT '등록자(사용자일련번호)',
    MDFCN_DT         DATETIME(6) NULL                    COMMENT '수정일시',
    MDFR_SN          BIGINT      NULL                    COMMENT '수정자(사용자일련번호)',
    PRIMARY KEY (ROOM_CALENDAR_SN),
    UNIQUE KEY UK_ROOM_CALENDAR (ROOM_SN, STAY_DATE),
    KEY IX_ROOM_CALENDAR_DATE (STAY_DATE),
    CONSTRAINT FK_ROOM_CALENDAR_ROOM FOREIGN KEY (ROOM_SN) REFERENCES ROOM (ROOM_SN),
    CONSTRAINT CK_ROOM_CALENDAR_QTY CHECK (TOTAL_QTY >= 0 AND SOLD_QTY >= 0 AND SOLD_QTY <= TOTAL_QTY),
    CONSTRAINT CK_ROOM_CALENDAR_PRICE CHECK (PRICE_AMT >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='객실 재고/요금 캘린더';
