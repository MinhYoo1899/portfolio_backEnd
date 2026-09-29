-- =============================================================================
-- V2 : 계정 상세 — 소셜계정 / 프로필(회원·마스터) / 파트너사업자·승인이력 / 약관·동의 / 첨부파일 / 전화번호인증
-- 대상: MySQL 8.0.16 이상(CHECK 제약 enforced) / InnoDB / utf8mb4
-- 명명: 공공데이터 공통표준용어 기반(docs/db-naming-standard.md). 모든 컬럼 COMMENT(논리명).
-- 감사컬럼: REG_DT·RGTR_SN·MDFCN_DT·MDFR_SN·DEL_YN. soft delete(삭제시각=MDFCN_DT).
--           활성 유니크 = 생성컬럼(DEL_YN='N'일 때만 값). 이력(append-only) 테이블은 REG_DT만.
-- codex 감리(조건부 적합) 반영: 약관 유형+버전 평문 유니크(증적 보존), 동의 증적(IP/UA/채널)+최신행 커버링 인덱스,
--           전화인증 소비모델(USE_DT/FK/UK/CHECK), 소셜 식별자 bin collation+사용자당 제공자 1개,
--           파트너 승인 정합성 CHECK+승인자 FK+BRNO 형식 CHECK, 저장키 유니크+보안검사 상태 컬럼,
--           USER_ACNT.MBL_TELNO_HASH 보강(귀속 모델은 V1 USER_ACNT.MBL_TELNO가 담당).
-- codex 2차 감리 반영: opaque 식별자(STRG_KEY/PRVDR_TX_ID) bin collation, 전화번호 해시는 HMAC-SHA256(사전대입 방지),
--           VERIFIED↔완료시각 양방향+USE_DT 정합 CHECK, 동의 최신행 기준 AGRE_DT, 승인이력 상태 CHECK+행위자 FK,
--           PRTNR 승인상태별 컬럼 조합 단일 CHECK(재신청 시 승인흔적 클리어 — 이력은 PRTNR_APRV_HSTRY 보존), FK 명시 인덱스.
-- 이번 범위 제외(후속 V에서): 본인인증이력(IDNTY_VRFC, 포트원 연동 시) /
--           MFA 챌린지·신뢰기기·검증로그(마스터 2차인증 흐름 확정 시) /
--           사업자번호 검증이력(공공API 연동 시) / 기기푸시토큰(앱 푸시 단계) / 파일보안검사 엔진 연동(상태 컬럼만 선반영)
-- =============================================================================

-- ----------------------------------------------------------------------------
-- 사용자계정 보강 — 휴대전화번호 해시(암호화 컬럼은 조회 불가 → 중복확인/검색용).
-- 전화번호 유니크 정책 미확정(중복가입 판단 기본은 CI) → 일반 인덱스만.
-- ----------------------------------------------------------------------------
ALTER TABLE USER_ACNT
    ADD COLUMN MBL_TELNO_HASH CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '휴대전화번호해시(HMAC-SHA256, 정규화 번호 기준 — 조회/중복확인용)' AFTER MBL_TELNO,
    ADD KEY IX_USER_ACNT_MBL_HASH (MBL_TELNO_HASH);

-- ----------------------------------------------------------------------------
-- 첨부파일 (메타데이터. 공개이미지/비공개서류 2계열 — 이미지 정렬·대표 지정은 V3 HOUSE_IMAGE/ROOM_IMAGE 담당)
-- TRGT_TYPE/TRGT_ID 는 다형 논리참조(FK 없음, 앱 정합성. V1 AUDIT_LOG.TRGT_TYPE 명명과 통일)
-- ----------------------------------------------------------------------------
CREATE TABLE ATCH_FILE (
    ATCH_FILE_SN  BIGINT       NOT NULL AUTO_INCREMENT COMMENT '첨부파일일련번호',
    FILE_SCOPE_CD VARCHAR(20)  NOT NULL                COMMENT '파일공개범위코드(PUBLIC_IMAGE/PRIVATE_DOC)',
    TRGT_TYPE     VARCHAR(50)  NULL                    COMMENT '대상유형(다형 논리참조: PRTNR/USER_PRFL 등)',
    TRGT_ID       VARCHAR(50)  NULL                    COMMENT '대상식별자',
    ORGNL_FILE_NM VARCHAR(300) NOT NULL                COMMENT '원본파일명',
    STRG_FILE_NM  VARCHAR(300) NOT NULL                COMMENT '저장파일명(UUID 기반)',
    STRG_TYPE_CD  VARCHAR(20)  NOT NULL DEFAULT 'LOCAL' COMMENT '저장소유형코드(LOCAL/OBJECT_STORAGE)',
    STRG_KEY      VARCHAR(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL COMMENT '저장소키(경로/오브젝트키, 대소문자 구분, 기술필드)',
    URL_ADDR      VARCHAR(500) NULL                    COMMENT '공개URL주소(CDN — PUBLIC_IMAGE만 사용)',
    MIME_TYPE_NM  VARCHAR(100) NULL                    COMMENT 'MIME유형명(기술필드)',
    EXTN_NM       VARCHAR(20)  NULL                    COMMENT '확장자명',
    FILE_SZ       BIGINT       NULL                    COMMENT '파일크기(byte)',
    SCAN_STTS_CD  VARCHAR(20)  NULL                    COMMENT '보안검사상태코드(PENDING/PASSED/FAILED — 검사엔진 연동 전 NULL)',
    SCAN_DT       DATETIME(6)  NULL                    COMMENT '보안검사일시',
    REG_DT        DATETIME(6)  NOT NULL                COMMENT '등록일시',
    RGTR_SN       BIGINT       NULL                    COMMENT '등록자(사용자일련번호)',
    MDFCN_DT      DATETIME(6)  NULL                    COMMENT '수정일시',
    MDFR_SN       BIGINT       NULL                    COMMENT '수정자(사용자일련번호)',
    DEL_YN        CHAR(1)      NOT NULL DEFAULT 'N'    COMMENT '삭제여부' CHECK (DEL_YN IN ('Y','N')),
    PRIMARY KEY (ATCH_FILE_SN),
    UNIQUE KEY UK_ATCH_FILE_STRG_KEY (STRG_TYPE_CD, STRG_KEY),
    KEY IX_ATCH_FILE_TRGT (TRGT_TYPE, TRGT_ID),
    KEY IX_ATCH_FILE_SCOPE (FILE_SCOPE_CD)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='첨부파일';

-- ----------------------------------------------------------------------------
-- 소셜계정 (구글/카카오/애플 — 식별은 제공자코드+제공자측ID만 신뢰, 이메일은 참고용)
-- 제공자측 ID는 대소문자 구분(opaque id) → bin collation. 사용자당 동일 제공자 1개(활성).
-- ----------------------------------------------------------------------------
CREATE TABLE USER_SOCIAL_ACNT (
    USER_SOCIAL_ACNT_SN BIGINT  NOT NULL AUTO_INCREMENT COMMENT '소셜계정일련번호',
    USER_SN        BIGINT       NOT NULL                COMMENT '사용자일련번호',
    PRVDR_CD       VARCHAR(20)  NOT NULL                COMMENT '소셜제공자코드(공통코드 SOCIAL_PRVDR: GOOGLE/KAKAO/APPLE)',
    PRVDR_USER_ID  VARCHAR(191) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL COMMENT '제공자측사용자식별자(sub, 대소문자 구분)',
    EML_ADDR       VARCHAR(150) NULL                    COMMENT '제공자측이메일주소(참고용 — 식별자로 사용 금지)',
    LNKG_DT        DATETIME(6)  NOT NULL                COMMENT '연결일시',
    REG_DT         DATETIME(6)  NOT NULL                COMMENT '등록일시',
    RGTR_SN        BIGINT       NULL                    COMMENT '등록자(사용자일련번호)',
    MDFCN_DT       DATETIME(6)  NULL                    COMMENT '수정일시',
    MDFR_SN        BIGINT       NULL                    COMMENT '수정자(사용자일련번호)',
    DEL_YN         CHAR(1)      NOT NULL DEFAULT 'N'    COMMENT '삭제여부(연결해제)' CHECK (DEL_YN IN ('Y','N')),
    ACTV_PRVDR_CD      VARCHAR(20)  GENERATED ALWAYS AS (CASE WHEN DEL_YN = 'N' THEN PRVDR_CD END) VIRTUAL COMMENT '활성행 유니크 제약용 생성컬럼(비업무)',
    ACTV_PRVDR_USER_ID VARCHAR(191) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin GENERATED ALWAYS AS (CASE WHEN DEL_YN = 'N' THEN PRVDR_USER_ID END) VIRTUAL COMMENT '활성행 유니크 제약용 생성컬럼(비업무)',
    ACTV_USER_SN       BIGINT       GENERATED ALWAYS AS (CASE WHEN DEL_YN = 'N' THEN USER_SN END) VIRTUAL COMMENT '활성행 유니크 제약용 생성컬럼(비업무)',
    PRIMARY KEY (USER_SOCIAL_ACNT_SN),
    UNIQUE KEY UK_USER_SOCIAL_ACNT (ACTV_PRVDR_CD, ACTV_PRVDR_USER_ID),
    UNIQUE KEY UK_USER_SOCIAL_ACNT_USER_PRVDR (ACTV_USER_SN, ACTV_PRVDR_CD),
    KEY IX_USER_SOCIAL_ACNT_USER (USER_SN),
    CONSTRAINT FK_USER_SOCIAL_ACNT_USER FOREIGN KEY (USER_SN) REFERENCES USER_ACNT (USER_SN)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='소셜계정';

-- ----------------------------------------------------------------------------
-- 사용자프로필 (일반회원 부가 — 1:1, PK=USER_SN. 전화번호 귀속·마케팅 동의는 USER_ACNT·TRMS_AGRE가 진실원)
-- ----------------------------------------------------------------------------
CREATE TABLE USER_PRFL (
    USER_SN          BIGINT      NOT NULL                COMMENT '사용자일련번호',
    PRFL_IMG_FILE_SN BIGINT      NULL                    COMMENT '프로필이미지파일일련번호(ATCH_FILE 논리참조)',
    REG_DT           DATETIME(6) NOT NULL                COMMENT '등록일시',
    RGTR_SN          BIGINT      NULL                    COMMENT '등록자(사용자일련번호)',
    MDFCN_DT         DATETIME(6) NULL                    COMMENT '수정일시',
    MDFR_SN          BIGINT      NULL                    COMMENT '수정자(사용자일련번호)',
    DEL_YN           CHAR(1)     NOT NULL DEFAULT 'N'    COMMENT '삭제여부' CHECK (DEL_YN IN ('Y','N')),
    PRIMARY KEY (USER_SN),
    CONSTRAINT FK_USER_PRFL_USER FOREIGN KEY (USER_SN) REFERENCES USER_ACNT (USER_SN)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='사용자프로필';

-- ----------------------------------------------------------------------------
-- 파트너사업자 (사업자 정보 + 마스터 승인 워크플로우 + 정산계좌(민감))
-- BRNO 평문 유지(공개성 정보) + 형식 CHECK. 암호화+해시 전환 여부는 개인정보 정책 확정 시 재검토.
-- ----------------------------------------------------------------------------
CREATE TABLE PRTNR (
    PRTNR_SN       BIGINT       NOT NULL AUTO_INCREMENT COMMENT '파트너일련번호',
    USER_SN        BIGINT       NOT NULL                COMMENT '사용자일련번호(로그인 계정)',
    CONM           VARCHAR(200) NOT NULL                COMMENT '상호(사업자명)',
    BRNO           VARCHAR(10)  NOT NULL                COMMENT '사업자등록번호',
    RPRSV_NM       VARCHAR(100) NULL                    COMMENT '대표자명',
    BRNO_VRFC_STTS VARCHAR(20)  NOT NULL DEFAULT 'REQUESTED' COMMENT '사업자등록번호검증상태(REQUESTED/VERIFIED/FAILED)',
    ADDR           VARCHAR(320) NULL                    COMMENT '사업장주소',
    DADDR          VARCHAR(200) NULL                    COMMENT '사업장상세주소',
    PIC_NM         VARCHAR(100) NULL                    COMMENT '담당자명',
    PIC_MBL_TELNO  VARCHAR(255) NULL                    COMMENT '담당자휴대전화번호(암호화)',
    PRTNR_EML_ADDR VARCHAR(150) NULL                    COMMENT '파트너이메일주소(회사 대표 메일)',
    APRV_STTS      VARCHAR(20)  NOT NULL DEFAULT 'REQUESTED' COMMENT '승인상태(REQUESTED/APPROVED/REJECTED)',
    APRV_USER_SN   BIGINT       NULL                    COMMENT '승인자(사용자일련번호)',
    APRV_DT        DATETIME(6)  NULL                    COMMENT '승인일시(반려 시 반려일시)',
    RJCT_RSN       VARCHAR(500) NULL                    COMMENT '반려사유',
    BANK_CD        VARCHAR(10)  NULL                    COMMENT '정산은행코드(공통코드 BANK)',
    ENACTNO        VARCHAR(255) NULL                    COMMENT '암호화계좌번호(정산계좌)',
    DPSTR_NM       VARCHAR(100) NULL                    COMMENT '예금주명',
    REG_DT         DATETIME(6)  NOT NULL                COMMENT '등록일시',
    RGTR_SN        BIGINT       NULL                    COMMENT '등록자(사용자일련번호)',
    MDFCN_DT       DATETIME(6)  NULL                    COMMENT '수정일시',
    MDFR_SN        BIGINT       NULL                    COMMENT '수정자(사용자일련번호)',
    DEL_YN         CHAR(1)      NOT NULL DEFAULT 'N'    COMMENT '삭제여부' CHECK (DEL_YN IN ('Y','N')),
    ACTV_USER_SN   BIGINT       GENERATED ALWAYS AS (CASE WHEN DEL_YN = 'N' THEN USER_SN END) VIRTUAL COMMENT '활성행 유니크 제약용 생성컬럼(비업무)',
    ACTV_BRNO      VARCHAR(10)  GENERATED ALWAYS AS (CASE WHEN DEL_YN = 'N' THEN BRNO END) VIRTUAL COMMENT '활성행 유니크 제약용 생성컬럼(비업무)',
    PRIMARY KEY (PRTNR_SN),
    UNIQUE KEY UK_PRTNR_USER (ACTV_USER_SN),
    UNIQUE KEY UK_PRTNR_BRNO (ACTV_BRNO),
    KEY IX_PRTNR_APRV_STTS (APRV_STTS),
    KEY IX_PRTNR_USER (USER_SN),
    KEY IX_PRTNR_APRV_USER (APRV_USER_SN),
    CONSTRAINT FK_PRTNR_USER FOREIGN KEY (USER_SN) REFERENCES USER_ACNT (USER_SN),
    CONSTRAINT FK_PRTNR_APRV_USER FOREIGN KEY (APRV_USER_SN) REFERENCES USER_ACNT (USER_SN),
    CONSTRAINT CK_PRTNR_BRNO_FORMAT CHECK (BRNO REGEXP '^[0-9]{10}$'),
    CONSTRAINT CK_PRTNR_APRV CHECK (
        (APRV_STTS = 'REQUESTED' AND APRV_USER_SN IS NULL     AND APRV_DT IS NULL     AND RJCT_RSN IS NULL)
     OR (APRV_STTS = 'APPROVED'  AND APRV_USER_SN IS NOT NULL AND APRV_DT IS NOT NULL AND RJCT_RSN IS NULL)
     OR (APRV_STTS = 'REJECTED'  AND APRV_USER_SN IS NOT NULL AND APRV_DT IS NOT NULL AND RJCT_RSN IS NOT NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='파트너사업자';

-- ----------------------------------------------------------------------------
-- 파트너승인이력 (append-only — 승인/반려/재심사 추적)
-- ----------------------------------------------------------------------------
CREATE TABLE PRTNR_APRV_HSTRY (
    PRTNR_APRV_HSTRY_SN BIGINT  NOT NULL AUTO_INCREMENT COMMENT '파트너승인이력일련번호',
    PRTNR_SN       BIGINT       NOT NULL                COMMENT '파트너일련번호',
    BFR_APRV_STTS  VARCHAR(20)  NULL                    COMMENT '변경전승인상태(최초 신청 시 NULL)',
    AFT_APRV_STTS  VARCHAR(20)  NOT NULL                COMMENT '변경후승인상태',
    RSN            VARCHAR(500) NULL                    COMMENT '사유',
    ACTR_USER_SN   BIGINT       NULL                    COMMENT '행위자(사용자일련번호)',
    REG_DT         DATETIME(6)  NOT NULL                COMMENT '등록일시',
    PRIMARY KEY (PRTNR_APRV_HSTRY_SN),
    KEY IX_PRTNR_APRV_HSTRY (PRTNR_SN, REG_DT),
    KEY IX_PRTNR_APRV_HSTRY_ACTR (ACTR_USER_SN),
    CONSTRAINT FK_PRTNR_APRV_HSTRY_PRTNR FOREIGN KEY (PRTNR_SN) REFERENCES PRTNR (PRTNR_SN),
    CONSTRAINT FK_PRTNR_APRV_HSTRY_ACTR FOREIGN KEY (ACTR_USER_SN) REFERENCES USER_ACNT (USER_SN),
    CONSTRAINT CK_PRTNR_APRV_HSTRY_BFR CHECK (BFR_APRV_STTS IS NULL OR BFR_APRV_STTS IN ('REQUESTED','APPROVED','REJECTED')),
    CONSTRAINT CK_PRTNR_APRV_HSTRY_AFT CHECK (AFT_APRV_STTS IN ('REQUESTED','APPROVED','REJECTED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='파트너승인이력';

-- ----------------------------------------------------------------------------
-- 마스터프로필 (운영자 부가 — 1:1, PK=USER_SN. MFA 상세 테이블은 흐름 확정 후)
-- ----------------------------------------------------------------------------
CREATE TABLE MSTR_PRFL (
    USER_SN    BIGINT       NOT NULL                COMMENT '사용자일련번호',
    DEPT_NM    VARCHAR(100) NULL                    COMMENT '부서명',
    JBPS_NM    VARCHAR(100) NULL                    COMMENT '직위명',
    MFA_USE_YN CHAR(1)      NOT NULL DEFAULT 'N'    COMMENT '2차인증(MFA)사용여부' CHECK (MFA_USE_YN IN ('Y','N')),
    REG_DT     DATETIME(6)  NOT NULL                COMMENT '등록일시',
    RGTR_SN    BIGINT       NULL                    COMMENT '등록자(사용자일련번호)',
    MDFCN_DT   DATETIME(6)  NULL                    COMMENT '수정일시',
    MDFR_SN    BIGINT       NULL                    COMMENT '수정자(사용자일련번호)',
    DEL_YN     CHAR(1)      NOT NULL DEFAULT 'N'    COMMENT '삭제여부' CHECK (DEL_YN IN ('Y','N')),
    PRIMARY KEY (USER_SN),
    CONSTRAINT FK_MSTR_PRFL_USER FOREIGN KEY (USER_SN) REFERENCES USER_ACNT (USER_SN)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='마스터프로필';

-- ----------------------------------------------------------------------------
-- 약관 (유형+버전 단위 발행 — 본문 불변, 개정 시 새 버전 행 추가)
-- 법적 증적: 유형+버전 유니크는 평문(soft delete 후에도 동일 버전 재사용 금지 — 활성유니크 미적용)
-- ----------------------------------------------------------------------------
CREATE TABLE TRMS (
    TRMS_SN      BIGINT       NOT NULL AUTO_INCREMENT COMMENT '약관일련번호',
    TRMS_TYPE_CD VARCHAR(30)  NOT NULL                COMMENT '약관유형코드(공통코드 TRMS_TYPE: SERVICE/PRIVACY/MARKETING/LOCATION/THIRD_PARTY/PARTNER)',
    VER_NO       VARCHAR(20)  NOT NULL                COMMENT '버전번호',
    TRMS_TTL     VARCHAR(256) NOT NULL                COMMENT '약관제목',
    TRMS_CN      MEDIUMTEXT   NOT NULL                COMMENT '약관내용',
    ESNTL_YN     CHAR(1)      NOT NULL DEFAULT 'Y'    COMMENT '필수여부(필수동의/선택동의)' CHECK (ESNTL_YN IN ('Y','N')),
    ENFC_DT      DATETIME(6)  NOT NULL                COMMENT '시행일시',
    USE_YN       CHAR(1)      NOT NULL DEFAULT 'Y'    COMMENT '사용여부' CHECK (USE_YN IN ('Y','N')),
    REG_DT       DATETIME(6)  NOT NULL                COMMENT '등록일시',
    RGTR_SN      BIGINT       NULL                    COMMENT '등록자(사용자일련번호)',
    MDFCN_DT     DATETIME(6)  NULL                    COMMENT '수정일시',
    MDFR_SN      BIGINT       NULL                    COMMENT '수정자(사용자일련번호)',
    DEL_YN       CHAR(1)      NOT NULL DEFAULT 'N'    COMMENT '삭제여부' CHECK (DEL_YN IN ('Y','N')),
    PRIMARY KEY (TRMS_SN),
    UNIQUE KEY UK_TRMS_TYPE_VER (TRMS_TYPE_CD, VER_NO),
    KEY IX_TRMS_SIGNUP_REQ (USE_YN, DEL_YN, ESNTL_YN, ENFC_DT, TRMS_TYPE_CD),
    KEY IX_TRMS_TYPE_ENFC (TRMS_TYPE_CD, ENFC_DT)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='약관';

-- ----------------------------------------------------------------------------
-- 약관동의이력 (append-only — 동의/철회 모두 행 추가, 최신 행이 현재 상태. UK 없음(재동의 허용))
-- 법적 증적: IP/User-Agent/채널 기록. 최신행(현재 동의상태) 판정 기준 = AGRE_DT(동의/철회 발생시각), 동률 시 TRMS_AGRE_SN.
-- ----------------------------------------------------------------------------
CREATE TABLE TRMS_AGRE (
    TRMS_AGRE_SN BIGINT       NOT NULL AUTO_INCREMENT COMMENT '약관동의일련번호',
    USER_SN      BIGINT       NOT NULL                COMMENT '사용자일련번호',
    TRMS_SN      BIGINT       NOT NULL                COMMENT '약관일련번호(유형+버전 특정)',
    AGRE_YN      CHAR(1)      NOT NULL                COMMENT '동의여부(N=철회)' CHECK (AGRE_YN IN ('Y','N')),
    AGRE_DT      DATETIME(6)  NOT NULL                COMMENT '동의일시',
    AGRE_CHNL_CD VARCHAR(20)  NULL                    COMMENT '동의채널코드(WEB/APP/ADMIN)',
    CNTN_IP      VARCHAR(45)  NULL                    COMMENT '접속IP(동의 시점 증적)',
    USER_AGNT    VARCHAR(500) NULL                    COMMENT '사용자에이전트(User-Agent, 기술필드)',
    REG_DT       DATETIME(6)  NOT NULL                COMMENT '등록일시',
    PRIMARY KEY (TRMS_AGRE_SN),
    KEY IX_TRMS_AGRE_USER_LATEST (USER_SN, TRMS_SN, AGRE_DT DESC, TRMS_AGRE_SN DESC, AGRE_YN),
    KEY IX_TRMS_AGRE_TRMS (TRMS_SN),
    CONSTRAINT FK_TRMS_AGRE_USER FOREIGN KEY (USER_SN) REFERENCES USER_ACNT (USER_SN),
    CONSTRAINT FK_TRMS_AGRE_TRMS FOREIGN KEY (TRMS_SN) REFERENCES TRMS (TRMS_SN)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='약관동의이력';

-- ----------------------------------------------------------------------------
-- 휴대전화번호인증 (가입/비밀번호재설정/번호변경 — 가입 전 USER_SN NULL. 외부(포트원) 연동 전에는 자리만)
-- 인증 결과 1회 소비 모델: USE_DT(사용일시) 기록, 외부거래ID 유니크, VERIFIED↔완료시각 정합 CHECK.
-- ----------------------------------------------------------------------------
CREATE TABLE MBL_TELNO_VRFC (
    MBL_TELNO_VRFC_SN BIGINT   NOT NULL AUTO_INCREMENT COMMENT '휴대전화번호인증일련번호',
    USER_SN        BIGINT       NULL                    COMMENT '사용자일련번호(가입 전 NULL)',
    MBL_TELNO      VARCHAR(255) NOT NULL                COMMENT '휴대전화번호(암호화)',
    MBL_TELNO_HASH CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '휴대전화번호해시(HMAC-SHA256, 정규화 번호 기준 — 조회/횟수제한용)',
    VRFC_PRPS_CD   VARCHAR(20)  NOT NULL                COMMENT '인증목적코드(SIGNUP/PSWD_RESET/TELNO_CHG)',
    PRVDR_TX_ID    VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NULL COMMENT '외부인증거래식별자(포트원, 대소문자 구분, 기술필드)',
    VRFC_STTS      VARCHAR(20)  NOT NULL DEFAULT 'REQUESTED' COMMENT '인증상태(REQUESTED/VERIFIED/EXPIRED/FAILED)',
    EXPRY_DT       DATETIME(6)  NULL                    COMMENT '만료일시',
    VRFC_CMPTN_DT  DATETIME(6)  NULL                    COMMENT '검증완료일시',
    USE_DT         DATETIME(6)  NULL                    COMMENT '사용일시(가입 등에 소비된 시각 — 재사용 방지)',
    FAIL_RSN       VARCHAR(200) NULL                    COMMENT '실패사유',
    REG_DT         DATETIME(6)  NOT NULL                COMMENT '등록일시',
    MDFCN_DT       DATETIME(6)  NULL                    COMMENT '수정일시(상태 전이 시각)',
    PRIMARY KEY (MBL_TELNO_VRFC_SN),
    UNIQUE KEY UK_MBL_TELNO_VRFC_PRVDR_TX (PRVDR_TX_ID),
    KEY IX_MBL_TELNO_VRFC_HASH (MBL_TELNO_HASH, REG_DT),
    KEY IX_MBL_TELNO_VRFC_USER (USER_SN),
    CONSTRAINT FK_MBL_TELNO_VRFC_USER FOREIGN KEY (USER_SN) REFERENCES USER_ACNT (USER_SN),
    CONSTRAINT CK_MBL_TELNO_VRFC_STTS CHECK (VRFC_STTS IN ('REQUESTED','VERIFIED','EXPIRED','FAILED')),
    CONSTRAINT CK_MBL_TELNO_VRFC_VERIFIED CHECK (
        (VRFC_STTS = 'VERIFIED' AND VRFC_CMPTN_DT IS NOT NULL)
     OR (VRFC_STTS <> 'VERIFIED' AND VRFC_CMPTN_DT IS NULL)
    ),
    CONSTRAINT CK_MBL_TELNO_VRFC_USE CHECK (
        USE_DT IS NULL
     OR (VRFC_STTS = 'VERIFIED' AND VRFC_CMPTN_DT IS NOT NULL AND USE_DT >= VRFC_CMPTN_DT)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='휴대전화번호인증';
