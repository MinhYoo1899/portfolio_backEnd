-- =============================================================================
-- V1 : 핵심 골격 — 공통코드 / 사용자계정(인증) / RBAC / 감사·로그
-- 대상: MySQL 8 / InnoDB / utf8mb4
-- 명명: 공공데이터 공통표준용어 기반(docs/db-naming-standard.md). 모든 컬럼 COMMENT(논리명).
-- 감사컬럼: REG_DT(등록일시)·RGTR_SN(등록자)·MDFCN_DT(수정일시)·MDFR_SN(수정자)·DEL_YN(삭제여부).
--           soft delete(삭제시각=MDFCN_DT). 활성 유니크 = 생성컬럼(DEL_YN='N'일 때만 값).
-- codex 검토 반영: N:M 활성유니크, CI는 CI_HASH 유니크, Y/N CHECK, ENPSWD(255), 복합인덱스.
-- =============================================================================

-- ----------------------------------------------------------------------------
-- 공통코드그룹
-- ----------------------------------------------------------------------------
CREATE TABLE COM_CD_GROUP (
    COM_CD_GROUP_SN BIGINT      NOT NULL AUTO_INCREMENT COMMENT '공통코드그룹일련번호',
    GROUP_CD     VARCHAR(50)  NOT NULL                COMMENT '그룹코드',
    GROUP_NM     VARCHAR(100) NOT NULL                COMMENT '그룹명',
    EXPLN        VARCHAR(500) NULL                    COMMENT '설명',
    USE_YN       CHAR(1)      NOT NULL DEFAULT 'Y'    COMMENT '사용여부' CHECK (USE_YN IN ('Y','N')),
    REG_DT       DATETIME(6)  NOT NULL                COMMENT '등록일시',
    RGTR_SN      BIGINT       NULL                    COMMENT '등록자(사용자일련번호)',
    MDFCN_DT     DATETIME(6)  NULL                    COMMENT '수정일시',
    MDFR_SN      BIGINT       NULL                    COMMENT '수정자(사용자일련번호)',
    DEL_YN       CHAR(1)      NOT NULL DEFAULT 'N'    COMMENT '삭제여부' CHECK (DEL_YN IN ('Y','N')),
    ACTV_GROUP_CD VARCHAR(50) GENERATED ALWAYS AS (CASE WHEN DEL_YN = 'N' THEN GROUP_CD END) VIRTUAL COMMENT '활성행 유니크 제약용 생성컬럼(비업무)',
    PRIMARY KEY (COM_CD_GROUP_SN),
    UNIQUE KEY UK_COM_CD_GROUP (ACTV_GROUP_CD)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='공통코드그룹';

-- ----------------------------------------------------------------------------
-- 공통코드 (GROUP_CD 는 COM_CD_GROUP.GROUP_CD 논리참조 — FK 없음, 앱 정합성)
-- ----------------------------------------------------------------------------
CREATE TABLE COM_CD (
    COM_CD_SN  BIGINT       NOT NULL AUTO_INCREMENT COMMENT '공통코드일련번호',
    GROUP_CD   VARCHAR(50)  NOT NULL                COMMENT '그룹코드',
    CD         VARCHAR(50)  NOT NULL                COMMENT '코드',
    CD_NM      VARCHAR(100) NOT NULL                COMMENT '코드명',
    SORT_SEQ   INT          NOT NULL DEFAULT 0      COMMENT '정렬순서',
    USE_YN     CHAR(1)      NOT NULL DEFAULT 'Y'    COMMENT '사용여부' CHECK (USE_YN IN ('Y','N')),
    REG_DT     DATETIME(6)  NOT NULL                COMMENT '등록일시',
    RGTR_SN    BIGINT       NULL                    COMMENT '등록자(사용자일련번호)',
    MDFCN_DT   DATETIME(6)  NULL                    COMMENT '수정일시',
    MDFR_SN    BIGINT       NULL                    COMMENT '수정자(사용자일련번호)',
    DEL_YN     CHAR(1)      NOT NULL DEFAULT 'N'    COMMENT '삭제여부' CHECK (DEL_YN IN ('Y','N')),
    ACTV_GROUP_CD VARCHAR(50) GENERATED ALWAYS AS (CASE WHEN DEL_YN = 'N' THEN GROUP_CD END) VIRTUAL COMMENT '활성행 유니크 제약용 생성컬럼(비업무)',
    ACTV_CD       VARCHAR(50) GENERATED ALWAYS AS (CASE WHEN DEL_YN = 'N' THEN CD END) VIRTUAL COMMENT '활성행 유니크 제약용 생성컬럼(비업무)',
    PRIMARY KEY (COM_CD_SN),
    UNIQUE KEY UK_COM_CD (ACTV_GROUP_CD, ACTV_CD),
    KEY IX_COM_CD_GROUP_SORT (GROUP_CD, SORT_SEQ)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='공통코드';

-- ----------------------------------------------------------------------------
-- 사용자계정 (인증/로그인 — USER_SN = 보편 행위주체 식별자)
-- ----------------------------------------------------------------------------
CREATE TABLE USER_ACNT (
    USER_SN        BIGINT       NOT NULL AUTO_INCREMENT COMMENT '사용자일련번호',
    EML_ADDR       VARCHAR(150) NULL                    COMMENT '이메일주소(로그인ID)',
    ENPSWD         VARCHAR(255) NULL                    COMMENT '암호화비밀번호(소셜전용 NULL)',
    USER_TYPE_CD   VARCHAR(30)  NOT NULL                COMMENT '사용자유형코드(공통코드 USER_TYPE)',
    LGN_TYPE       VARCHAR(20)  NOT NULL DEFAULT 'EMAIL' COMMENT '로그인유형(EMAIL/SOCIAL/MIXED)',
    ACNT_STTS      VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT '계정상태(PENDING/ACTIVE/REJECTED/SUSPENDED/WITHDRAWN)',
    USER_NM        VARCHAR(50)  NULL                    COMMENT '사용자명',
    NCNM           VARCHAR(30)  NULL                    COMMENT '별명(닉네임)',
    MBL_TELNO      VARCHAR(255) NULL                    COMMENT '휴대전화번호(암호화)',
    MBL_TELNO_VRFC_YN CHAR(1)   NOT NULL DEFAULT 'N'    COMMENT '휴대전화번호인증여부' CHECK (MBL_TELNO_VRFC_YN IN ('Y','N')),
    CI             VARCHAR(255) NULL                    COMMENT '본인인증연계정보(CI, 암호화)',
    CI_HASH        VARCHAR(64)  NULL                    COMMENT '본인인증연계정보 해시(SHA-256, 중복가입 판단/유니크용)',
    LAST_LGN_DT    DATETIME(6)  NULL                    COMMENT '최종로그인일시',
    WHDWL_DT       DATETIME(6)  NULL                    COMMENT '탈퇴일시',
    REG_DT         DATETIME(6)  NOT NULL                COMMENT '등록일시',
    RGTR_SN        BIGINT       NULL                    COMMENT '등록자(사용자일련번호)',
    MDFCN_DT       DATETIME(6)  NULL                    COMMENT '수정일시',
    MDFR_SN        BIGINT       NULL                    COMMENT '수정자(사용자일련번호)',
    DEL_YN         CHAR(1)      NOT NULL DEFAULT 'N'    COMMENT '삭제여부' CHECK (DEL_YN IN ('Y','N')),
    ACTV_EML_ADDR  VARCHAR(150) GENERATED ALWAYS AS (CASE WHEN DEL_YN = 'N' THEN EML_ADDR END) VIRTUAL COMMENT '활성행 유니크 제약용 생성컬럼(비업무)',
    ACTV_CI_HASH   VARCHAR(64)  GENERATED ALWAYS AS (CASE WHEN DEL_YN = 'N' THEN CI_HASH END) VIRTUAL COMMENT '활성행 유니크 제약용 생성컬럼(비업무)',
    PRIMARY KEY (USER_SN),
    UNIQUE KEY UK_USER_ACNT_EML (ACTV_EML_ADDR),
    UNIQUE KEY UK_USER_ACNT_CI (ACTV_CI_HASH),
    KEY IX_USER_ACNT_TYPE_STTS (USER_TYPE_CD, ACNT_STTS)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='사용자계정';

-- ----------------------------------------------------------------------------
-- 역할 (RBAC — 데이터 주도, 역할명/권한 하드코딩 금지)
-- ----------------------------------------------------------------------------
CREATE TABLE ROLE (
    ROLE_SN        BIGINT       NOT NULL AUTO_INCREMENT COMMENT '역할일련번호',
    ROLE_CD        VARCHAR(50)  NOT NULL                COMMENT '역할코드',
    ROLE_NM        VARCHAR(100) NOT NULL                COMMENT '역할명',
    UPPER_ROLE_SN  BIGINT       NULL                    COMMENT '상위역할일련번호(계층, NULL=최상위)',
    ROLE_STEP      INT          NOT NULL DEFAULT 1      COMMENT '역할단계(계층수준)',
    SORT_SEQ       INT          NOT NULL DEFAULT 0      COMMENT '정렬순서',
    USE_YN         CHAR(1)      NOT NULL DEFAULT 'Y'    COMMENT '사용여부' CHECK (USE_YN IN ('Y','N')),
    REG_DT         DATETIME(6)  NOT NULL                COMMENT '등록일시',
    RGTR_SN        BIGINT       NULL                    COMMENT '등록자(사용자일련번호)',
    MDFCN_DT       DATETIME(6)  NULL                    COMMENT '수정일시',
    MDFR_SN        BIGINT       NULL                    COMMENT '수정자(사용자일련번호)',
    DEL_YN         CHAR(1)      NOT NULL DEFAULT 'N'    COMMENT '삭제여부' CHECK (DEL_YN IN ('Y','N')),
    ACTV_ROLE_CD   VARCHAR(50)  GENERATED ALWAYS AS (CASE WHEN DEL_YN = 'N' THEN ROLE_CD END) VIRTUAL COMMENT '활성행 유니크 제약용 생성컬럼(비업무)',
    PRIMARY KEY (ROLE_SN),
    UNIQUE KEY UK_ROLE_CD (ACTV_ROLE_CD),
    KEY IX_ROLE_UPPER (UPPER_ROLE_SN)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='역할';

-- ----------------------------------------------------------------------------
-- 권한
-- ----------------------------------------------------------------------------
CREATE TABLE AUTHRT (
    AUTHRT_SN   BIGINT       NOT NULL AUTO_INCREMENT COMMENT '권한일련번호',
    AUTHRT_CD   VARCHAR(80)  NOT NULL                COMMENT '권한코드(예: APPLICATION_READ_ALL)',
    AUTHRT_NM   VARCHAR(100) NOT NULL                COMMENT '권한명',
    USE_YN      CHAR(1)      NOT NULL DEFAULT 'Y'    COMMENT '사용여부' CHECK (USE_YN IN ('Y','N')),
    REG_DT      DATETIME(6)  NOT NULL                COMMENT '등록일시',
    RGTR_SN     BIGINT       NULL                    COMMENT '등록자(사용자일련번호)',
    MDFCN_DT    DATETIME(6)  NULL                    COMMENT '수정일시',
    MDFR_SN     BIGINT       NULL                    COMMENT '수정자(사용자일련번호)',
    DEL_YN      CHAR(1)      NOT NULL DEFAULT 'N'    COMMENT '삭제여부' CHECK (DEL_YN IN ('Y','N')),
    ACTV_AUTHRT_CD VARCHAR(80) GENERATED ALWAYS AS (CASE WHEN DEL_YN = 'N' THEN AUTHRT_CD END) VIRTUAL COMMENT '활성행 유니크 제약용 생성컬럼(비업무)',
    PRIMARY KEY (AUTHRT_SN),
    UNIQUE KEY UK_AUTHRT_CD (ACTV_AUTHRT_CD)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='권한';

-- ----------------------------------------------------------------------------
-- 사용자역할 (사용자 N:M 역할, 활성유니크로 재부여 허용)
-- ----------------------------------------------------------------------------
CREATE TABLE USER_ROLE (
    USER_ROLE_SN BIGINT      NOT NULL AUTO_INCREMENT COMMENT '사용자역할일련번호',
    USER_SN      BIGINT      NOT NULL                COMMENT '사용자일련번호',
    ROLE_SN      BIGINT      NOT NULL                COMMENT '역할일련번호',
    REG_DT       DATETIME(6) NOT NULL                COMMENT '등록일시',
    RGTR_SN      BIGINT      NULL                    COMMENT '등록자(사용자일련번호)',
    MDFCN_DT     DATETIME(6) NULL                    COMMENT '수정일시',
    MDFR_SN      BIGINT      NULL                    COMMENT '수정자(사용자일련번호)',
    DEL_YN       CHAR(1)     NOT NULL DEFAULT 'N'    COMMENT '삭제여부' CHECK (DEL_YN IN ('Y','N')),
    ACTV_USER_SN BIGINT      GENERATED ALWAYS AS (CASE WHEN DEL_YN = 'N' THEN USER_SN END) VIRTUAL COMMENT '활성행 유니크 제약용 생성컬럼(비업무)',
    ACTV_ROLE_SN BIGINT      GENERATED ALWAYS AS (CASE WHEN DEL_YN = 'N' THEN ROLE_SN END) VIRTUAL COMMENT '활성행 유니크 제약용 생성컬럼(비업무)',
    PRIMARY KEY (USER_ROLE_SN),
    UNIQUE KEY UK_USER_ROLE (ACTV_USER_SN, ACTV_ROLE_SN),
    KEY IX_USER_ROLE_ROLE (ROLE_SN),
    CONSTRAINT FK_USER_ROLE_USER FOREIGN KEY (USER_SN) REFERENCES USER_ACNT (USER_SN),
    CONSTRAINT FK_USER_ROLE_ROLE FOREIGN KEY (ROLE_SN) REFERENCES ROLE (ROLE_SN)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='사용자역할';

-- ----------------------------------------------------------------------------
-- 역할권한 (역할 N:M 권한, 활성유니크로 재부여 허용)
-- ----------------------------------------------------------------------------
CREATE TABLE ROLE_AUTHRT (
    ROLE_AUTHRT_SN BIGINT      NOT NULL AUTO_INCREMENT COMMENT '역할권한일련번호',
    ROLE_SN        BIGINT      NOT NULL                COMMENT '역할일련번호',
    AUTHRT_SN      BIGINT      NOT NULL                COMMENT '권한일련번호',
    REG_DT         DATETIME(6) NOT NULL                COMMENT '등록일시',
    RGTR_SN        BIGINT      NULL                    COMMENT '등록자(사용자일련번호)',
    MDFCN_DT       DATETIME(6) NULL                    COMMENT '수정일시',
    MDFR_SN        BIGINT      NULL                    COMMENT '수정자(사용자일련번호)',
    DEL_YN         CHAR(1)     NOT NULL DEFAULT 'N'    COMMENT '삭제여부' CHECK (DEL_YN IN ('Y','N')),
    ACTV_ROLE_SN   BIGINT      GENERATED ALWAYS AS (CASE WHEN DEL_YN = 'N' THEN ROLE_SN END) VIRTUAL COMMENT '활성행 유니크 제약용 생성컬럼(비업무)',
    ACTV_AUTHRT_SN BIGINT      GENERATED ALWAYS AS (CASE WHEN DEL_YN = 'N' THEN AUTHRT_SN END) VIRTUAL COMMENT '활성행 유니크 제약용 생성컬럼(비업무)',
    PRIMARY KEY (ROLE_AUTHRT_SN),
    UNIQUE KEY UK_ROLE_AUTHRT (ACTV_ROLE_SN, ACTV_AUTHRT_SN),
    KEY IX_ROLE_AUTHRT_AUTHRT (AUTHRT_SN),
    CONSTRAINT FK_ROLE_AUTHRT_ROLE FOREIGN KEY (ROLE_SN) REFERENCES ROLE (ROLE_SN),
    CONSTRAINT FK_ROLE_AUTHRT_AUTHRT FOREIGN KEY (AUTHRT_SN) REFERENCES AUTHRT (AUTHRT_SN)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='역할권한';

-- ----------------------------------------------------------------------------
-- 감사로그 (행위 추적, append-only — 탈퇴 후에도 보존 위해 FK 생략)
-- ----------------------------------------------------------------------------
CREATE TABLE AUDIT_LOG (
    AUDIT_LOG_SN   BIGINT       NOT NULL AUTO_INCREMENT COMMENT '감사로그일련번호',
    ACTR_USER_SN   BIGINT       NULL                    COMMENT '행위자(사용자일련번호)',
    ACTR_TYPE      VARCHAR(20)  NULL                    COMMENT '행위자유형',
    ACTN_CN        VARCHAR(100) NOT NULL                COMMENT '행위내용(액션)',
    TRGT_TYPE      VARCHAR(50)  NULL                    COMMENT '대상유형',
    TRGT_ID        VARCHAR(50)  NULL                    COMMENT '대상식별자',
    PRCS_RSLT      VARCHAR(20)  NULL                    COMMENT '처리결과',
    CNTN_IP        VARCHAR(45)  NULL                    COMMENT '접속IP',
    USER_AGNT      VARCHAR(500) NULL                    COMMENT '사용자에이전트(User-Agent, 기술필드)',
    CORR_ID        VARCHAR(64)  NULL                    COMMENT '상관관계식별자(correlationId, 기술필드)',
    REG_DT         DATETIME(6)  NOT NULL                COMMENT '등록일시',
    PRIMARY KEY (AUDIT_LOG_SN),
    KEY IX_AUDIT_LOG_ACTR_REG (ACTR_USER_SN, REG_DT),
    KEY IX_AUDIT_LOG_REG_DT (REG_DT),
    KEY IX_AUDIT_LOG_CORR (CORR_ID)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='감사로그';

-- ----------------------------------------------------------------------------
-- 로그인이력
-- ----------------------------------------------------------------------------
CREATE TABLE LGN_HSTRY (
    LGN_HSTRY_SN BIGINT       NOT NULL AUTO_INCREMENT COMMENT '로그인이력일련번호',
    USER_SN      BIGINT       NULL                    COMMENT '사용자일련번호',
    LGN_ID       VARCHAR(150) NULL                    COMMENT '로그인ID',
    LGN_TYPE     VARCHAR(20)  NULL                    COMMENT '로그인유형',
    PRCS_RSLT    VARCHAR(20)  NOT NULL                COMMENT '처리결과(SUCCESS/FAIL)',
    FAIL_RSN     VARCHAR(200) NULL                    COMMENT '실패사유',
    CNTN_IP      VARCHAR(45)  NULL                    COMMENT '접속IP',
    USER_AGNT    VARCHAR(500) NULL                    COMMENT '사용자에이전트(User-Agent, 기술필드)',
    ISTR_NM      VARCHAR(100) NULL                    COMMENT '기기명',
    REG_DT       DATETIME(6)  NOT NULL                COMMENT '등록일시',
    PRIMARY KEY (LGN_HSTRY_SN),
    KEY IX_LGN_HSTRY_USER_REG (USER_SN, REG_DT),
    KEY IX_LGN_HSTRY_REG_DT (REG_DT)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='로그인이력';
