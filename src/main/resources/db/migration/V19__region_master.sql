-- =============================================================================
-- V19 : 지역(REGION) 마스터 + HOUSE 지역코드 컬럼  (구조만 — 시드는 V20으로 분리)
-- 목적(사용자 확정 2026-06-25, codex 2차 교차검토): 손님 공개 브라우즈에서 지역 표시/필터가
--   핵심 동선(디자인: "경상북도 안동시" 지역텍스트 + "충청남도/전라북도/강원도/경상북도 고택" 칩 필터)
--   → 자유텍스트 ADDR만으로는 "시도별 모아보기"가 불가 → 시도/시군구를 구조화한다.
-- 설계(codex 권고+사용자 승인): 별도 REGION 마스터(시도+시군구 2단계, 법정동코드 기반) + HOUSE 코드 FK(명칭은 마스터).
-- 코드체계: 법정동코드 앞자리. 시도=앞 2자리(DEPTH=1), 시군구=앞 5자리(DEPTH=2, 상위=시도, 앞2자리=시도코드).
-- codex DDL 검토(2026-06-25) 반영:
--   [MUST] ① 깊이↔코드길이↔상위코드 CHECK로 시도/시군구 혼입 차단 ② HOUSE 복합 FK(시군구가 그 시도 소속인지 DB 강제)
--          ③ 시도 필터 타는 공개목록 인덱스 ④ 컬럼은 테이블 끝에 추가(중간삽입 락 회피).
--   [SHOULD] UPPER_REGION_CD 선두 인덱스(자기참조 FK 자식 + 시군구 by 시도) + DEL_YN 포함 / 시드는 시도→시군구 분리(V20).
--   [NICE] REGION_CD 숫자 CHECK, SHORT_NM 공백금지 CHECK, FK ON DELETE/UPDATE RESTRICT 명시.
-- 백필(기존 HOUSE 주소→지역코드)·앱 지역필터 활성화는 후속(V20 시드 이후).
-- 명명: 공공표준. 감사컬럼 REG_DT/RGTR_SN/MDFCN_DT/MDFR_SN/DEL_YN, 마스터 USE_YN.
-- =============================================================================

-- ----------------------------------------------------------------------------
-- 지역 마스터 (시도/시군구 2단계 자기참조 계층)
-- ----------------------------------------------------------------------------
CREATE TABLE REGION (
    REGION_CD       VARCHAR(10)  NOT NULL                COMMENT '지역코드(법정동코드 앞자리 — 시도=2자리, 시군구=5자리)',
    UPPER_REGION_CD VARCHAR(10)  NULL                    COMMENT '상위지역코드(시군구→시도). 시도는 NULL',
    REGION_DEPTH    TINYINT      NOT NULL                COMMENT '지역단계(1=시도, 2=시군구)',
    REGION_NM       VARCHAR(50)  NOT NULL                COMMENT '지역명(공식)',
    REGION_SHORT_NM VARCHAR(50)  NULL                    COMMENT '짧은표시명(예: 강원특별자치도→강원)',
    SORT_SEQ        INT          NOT NULL DEFAULT 0      COMMENT '정렬순서',
    USE_YN          CHAR(1)      NOT NULL DEFAULT 'Y'    COMMENT '사용여부(비활성 시 신규 선택 불가)',
    REG_DT          DATETIME(6)  NOT NULL                COMMENT '등록일시',
    RGTR_SN         BIGINT       NULL                    COMMENT '등록자(사용자일련번호)',
    MDFCN_DT        DATETIME(6)  NULL                    COMMENT '수정일시',
    MDFR_SN         BIGINT       NULL                    COMMENT '수정자(사용자일련번호)',
    DEL_YN          CHAR(1)      NOT NULL DEFAULT 'N'    COMMENT '삭제여부',
    PRIMARY KEY (REGION_CD),
    -- 복합 FK(HOUSE 시군구+시도 → 코드+상위코드) 참조 대상 인덱스
    UNIQUE KEY UK_REGION_SELF (REGION_CD, UPPER_REGION_CD),
    -- 시군구 by 시도 조회(드롭다운) + 자기참조 FK 자식 인덱스
    KEY IX_REGION_UPPER (UPPER_REGION_CD, USE_YN, DEL_YN, SORT_SEQ),
    -- 시도 목록 조회
    KEY IX_REGION_DEPTH (REGION_DEPTH, USE_YN, DEL_YN, SORT_SEQ),
    CONSTRAINT FK_REGION_UPPER FOREIGN KEY (UPPER_REGION_CD) REFERENCES REGION (REGION_CD)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT CK_REGION_USE_YN CHECK (USE_YN IN ('Y', 'N')),
    CONSTRAINT CK_REGION_DEL_YN CHECK (DEL_YN IN ('Y', 'N')),
    CONSTRAINT CK_REGION_NM_NOT_BLANK CHECK (CHAR_LENGTH(TRIM(REGION_NM)) > 0),
    CONSTRAINT CK_REGION_SHORT_NM_NOT_BLANK CHECK (REGION_SHORT_NM IS NULL OR CHAR_LENGTH(TRIM(REGION_SHORT_NM)) > 0),
    CONSTRAINT CK_REGION_CD_NUM CHECK (REGION_CD REGEXP '^[0-9]+$'),
    -- 깊이↔코드길이↔상위코드 정합: 시도=2자리/상위NULL, 시군구=5자리/상위NOTNULL/앞2자리=상위코드
    CONSTRAINT CK_REGION_HIER CHECK (
        (REGION_DEPTH = 1 AND CHAR_LENGTH(REGION_CD) = 2 AND UPPER_REGION_CD IS NULL)
        OR
        (REGION_DEPTH = 2 AND CHAR_LENGTH(REGION_CD) = 5 AND UPPER_REGION_CD IS NOT NULL AND LEFT(REGION_CD, 2) = UPPER_REGION_CD)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='지역(시도/시군구) 마스터';

-- ----------------------------------------------------------------------------
-- HOUSE 지역코드 컬럼 추가 (테이블 끝에 추가 — 중간삽입 리빌드/락 회피)
--   nullable: 기존 행 무손상. 공개(ACTIVE) 전환 시 서비스에서 시도/시군구 필수검증.
-- ----------------------------------------------------------------------------
ALTER TABLE HOUSE
    ADD COLUMN SIDO_REGION_CD    VARCHAR(10) NULL COMMENT '시도지역코드(REGION 참조)',
    ADD COLUMN SIGUNGU_REGION_CD VARCHAR(10) NULL COMMENT '시군구지역코드(REGION 참조)',
    -- 시군구가 있으면 시도는 반드시 있어야 함
    ADD CONSTRAINT CK_HOUSE_REGION_HIER CHECK (SIGUNGU_REGION_CD IS NULL OR SIDO_REGION_CD IS NOT NULL),
    -- 시도 단독 유효성
    ADD CONSTRAINT FK_HOUSE_SIDO_REGION FOREIGN KEY (SIDO_REGION_CD) REFERENCES REGION (REGION_CD)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    -- 시군구가 "그 시도 소속"인지 DB가 강제(시군구 NULL이면 미검사)
    ADD CONSTRAINT FK_HOUSE_SIGUNGU_IN_SIDO FOREIGN KEY (SIGUNGU_REGION_CD, SIDO_REGION_CD) REFERENCES REGION (REGION_CD, UPPER_REGION_CD)
        ON DELETE RESTRICT ON UPDATE RESTRICT;

-- 단독 시도 FK 자식 인덱스
CREATE INDEX IX_HOUSE_SIDO_REGION ON HOUSE (SIDO_REGION_CD);
-- 복합 FK 자식 인덱스(시군구,시도)
CREATE INDEX IX_HOUSE_SIGUNGU_REGION ON HOUSE (SIGUNGU_REGION_CD, SIDO_REGION_CD);
-- 공개 목록: ACTIVE만 + 시도/시군구 필터 + 정렬키(HOUSE_SN). 시도 단독 필터도 선두 prefix로 탐.
CREATE INDEX IX_HOUSE_REGION_PUBLIC ON HOUSE (HOUSE_STTS_CD, DEL_YN, SIDO_REGION_CD, SIGUNGU_REGION_CD, HOUSE_SN);
