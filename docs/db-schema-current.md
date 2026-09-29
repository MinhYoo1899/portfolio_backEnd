# 현재 DB 스키마 정리

> 기준 파일: `db/migration/` V1(핵심골격) · V2(계정상세) · V3(기준시드) · V4(메뉴) · V5(로그인채널) · V6·V7(명명정비) · V8(운영자 2단계 RBAC) · V9(정식 메뉴 기준 데이터) · V10(문의 게시판) · V11(파일 업로드 권한) · V12(첨부 연결 생명주기) · V13(첨부 공개성) — 전부 적용됨(2026-06-18)
> 기준 DB: MySQL 8.0.16+ / InnoDB / utf8mb4 / `utf8mb4_0900_ai_ci`
> 이 문서는 **현재 실제 Flyway DDL 기준**이다. 전체 목표 ERD는 [erd.md](./erd.md), 명명 규칙은 [db-naming-standard.md](./db-naming-standard.md)를 본다.
> 개발 전용 시드(운영자 계정)는 `db/migration-dev/`(R__) — dev 프로파일에서만 적용, 운영 미유입.
> 메뉴는 V9부터 **정식 기준 데이터**(기획서 메뉴 구조도 확정본, 샘플 시드 폐기) — 변경은 새 V 마이그레이션으로만.

---

## 1. DB 연동 구조

현재 프로젝트는 Spring Boot 표준 `spring.datasource.*`가 아니라 전자정부 표준프레임워크 방식의 `Globals.*` 설정으로 DB를 연결한다.

설정 파일:

```text
src/main/resources/application.properties
```

핵심 설정:

```properties
Globals.DbType=mysql
Globals.mysql.DriverClassName=com.mysql.cj.jdbc.Driver
Globals.mysql.Url=jdbc:mysql://${DB_HOST}:${DB_PORT:3306}/${DB_NAME:eeum}?serverTimezone=UTC&characterEncoding=UTF-8&useSSL=false&allowPublicKeyRetrieval=true
Globals.mysql.UserName=${DB_USERNAME}
Globals.mysql.Password=${DB_PASSWORD}
```

> 타임존: JDBC `serverTimezone=UTC` — 발생 순간(instant)은 UTC로 저장한다([timezone-standard.md](./timezone-standard.md)). 숙박 날짜 등 달력값은 `DATE`/`LocalDate`(KST)로 별도 처리한다.

DataSource 생성:

```text
egovframework.com.config.EgovConfigAppDatasource
```

- `Globals.DbType=mysql`이면 `BasicDataSource` 사용
- 등록 빈 이름: `dataSource`, `egov.dataSource`, `egovDataSource`
- `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`는 환경변수로 주입

---

## 2. Flyway 설정

Flyway는 애플리케이션 부팅 시 자동 적용되도록 켜져 있다.

```properties
spring.flyway.enabled=true
spring.flyway.baseline-on-migrate=true
spring.flyway.locations=classpath:db/migration
spring.flyway.clean-disabled=true
```

현재 마이그레이션 파일 (`src/main/resources/db/migration/`, V1~V13 적용됨):

```text
V1__core_account_rbac_code_log.sql      # 핵심골격(계정/RBAC/공통코드/로그)
V2__account_detail_terms_file.sql       # 계정상세(소셜/프로필/파트너/약관/첨부/전화인증)
V3__seed_base_data.sql                  # 기준 시드
V4__menu_info.sql                       # 메뉴
V5__login_channel_authrt.sql            # 로그인채널
V6__naming_standard_refactor.sql        # 명명정비
V7__mst_operator_naming.sql             # 명명정비(운영자)
V8__operator_two_tier_rbac.sql          # 운영자 2단계 RBAC
V9__menu_master_seed.sql                # 정식 메뉴 기준 데이터
V10__inquiry_board.sql                  # 문의 게시판(INQR_INFO/INQR_ANS_INFO)
V11__file_upload_authority.sql          # 파일 업로드 권한 + 쿼터 인덱스
V12__attachment_lifecycle.sql           # 첨부 연결 생명주기(ATCH_STTS_CD/TEMP_EXPIRE_DT/LINKED_DT)
V13__attachment_visibility.sql          # 첨부 공개성(ATCH_VISBLTY_CD)
```

> 개발 전용 시드(운영자 계정 등)는 `db/migration-dev/`(R__) — dev 프로파일에서만 적용.

운영/공유 DB 원칙:

- 이미 적용된 `V1` 파일은 수정하지 않는다.
- 구조 변경은 새 버전 파일(`V2__...sql`, `V3__...sql`)로 추가한다.
- 삭제/리네임/대형 타입 변경은 expand-contract 방식으로 분리한다.
- `flyway clean`은 `spring.flyway.clean-disabled=true`로 금지되어 있다.

---

## 3. MyBatis 연동 구조

MyBatis 설정:

```text
egovframework.com.config.EgovConfigAppMapper
```

설정 XML:

```text
src/main/resources/egovframework/mapper/config/mapper-config.xml
```

매퍼 XML 스캔 패턴:

```text
classpath*:/egovframework/mapper/**/*_${Globals.DbType}.xml
```

현재 `Globals.DbType=mysql`이므로 실제 매퍼 파일명은 다음 패턴을 따라야 한다.

```text
*_mysql.xml
```

MyBatis 설정상 `mapUnderscoreToCamelCase=true`이므로 DB의 `USER_SN`, `REG_DT` 같은 컬럼은 Java 프로퍼티 `userSn`, `regDt`로 자동 매핑된다.

---

## 4. 현재 V1 테이블 목록

V1은 전체 예약 플랫폼 ERD가 아니라 **기반 스키마**만 만든다.

| 구분 | 테이블 | 용도 |
|---|---|---|
| 공통코드 | `COMM_CD_GROUP` | 공통코드 그룹 |
| 공통코드 | `COMM_CD` | 공통코드 |
| 계정 | `USER_ACNT` | 로그인/인증 계정 |
| 권한 | `ROLE_MST` | 역할 |
| 권한 | `AUTH_MST` | 권한 |
| 권한 | `USER_ROLE` | 사용자-역할 N:M |
| 권한 | `ROLE_AUTH` | 역할-권한 N:M |
| 로그 | `AUDIT_LOG_INFO` | 감사 로그 |
| 로그 | `LOGIN_LOG_INFO` | 로그인 이력 |

---

## 4-2. V2 테이블 목록 (계정 상세 — 2026-06-11 적용)

| 구분 | 테이블 | 용도 | 핵심 제약 |
|---|---|---|---|
| 계정 | `USER_ACNT` (ALTER) | `MBL_TELNO_HASH` 추가 | HMAC-SHA256, CHAR(64) ascii_bin, 일반 인덱스(유니크는 정책 미확정) |
| 파일 | `ATCH_FILE` | 첨부파일 메타(공개이미지/비공개서류/프로필/문의첨부) | UK(STRG_TYPE_CD, STRG_KEY·bin), 보안검사 상태(`NOT_REQUIRED`/PENDING/PASSED/FAILED), 업로더 쿼터 인덱스 `IX_ATCH_FILE_RGTR_REG`(RGTR_SN,REG_DT — V11) · **연결 생명주기 `ATCH_STTS_CD`(TEMP/LINKED)·`TEMP_EXPIRE_DT`·`LINKED_DT`(V12)** · **공개성 `ATCH_VISBLTY_CD`(PUBLIC/PRIVATE — V13)** · 인덱스 `IX_ATCH_FILE_SCOPE_TRGT`·`IX_ATCH_FILE_TEMP_EXPIRE`(V12) |
| 계정 | `USER_SOCIAL_ACNT` | 소셜계정(구글/카카오/애플) | UK(제공자+sub·bin), UK(사용자+제공자) — 모두 활성유니크 |
| 계정 | `USER_DTL_INFO` | 일반회원 프로필(1:1) | PK=USER_SN |
| 파트너 | `PRTNR_MST` | 파트너 사업자+승인+정산계좌 | 활성UK(USER_SN/BRNO), 승인상태 조합 CHECK, BRNO 형식 CHECK |
| 파트너 | `PARTNER_APPROVE_LOG_INFO` | 승인/반려 이력(append-only) | 상태 CHECK, 행위자 FK |
| 계정 | `OPERATOR_DTL_INFO` | 운영자 프로필(1:1)+MFA 플래그 | PK=USER_SN |
| 약관 | `TRMS_MST` | 약관(유형+버전 발행) | **평문 UK(유형+버전)** — 증적 보존, 활성유니크 미적용 |
| 약관 | `TRMS_AGRE_INFO` | 약관 동의/철회 이력(append-only) | UK 없음, 최신행 기준=AGRE_DT, IP/UA/채널 증적 |
| 인증 | `MBL_TELNO_VRFC_INFO` | 휴대전화번호 인증(1회 소비) | UK(PRVDR_TX_ID·bin), 상태↔완료시각·USE_DT CHECK |

- V2에서 의도적으로 미룬 것: 본인인증이력(포트원 연동 시) / MFA 챌린지·신뢰기기(흐름 확정 시) / 사업자번호 검증이력(공공API 연동 시) / 기기푸시토큰 / 파일보안검사 엔진 연동(상태 컬럼만 선반영).
- 운영 절차 확정 필요(codex 권고): HMAC 키 관리·회전 정책, 기존 `USER_ACNT` 전화번호의 해시 백필.

---

## 4-3. V3·V4 (2026-06-11 적용)

**V3 — 기준 시드** (전 환경 공통 데이터, 테이블 추가 없음):
- 공통코드: `USER_TYPE`(MEMBER/PARTNER/OPERATOR), `SOCIAL_PRVDR`, `TRMS_TYPE`, `AGRE_CHNL`
- 역할 3종(MEMBER/PARTNER/OPERATOR — 데이터 주도) + 권한 최소셋(`PRTNR_APRV`, `ACNT_MNG`) + 운영자 매핑
- 약관 초기 버전 3종(SERVICE/PRIVACY 필수, MARKETING 선택 — 본문 자리표시, 확정 본문은 새 버전 발행)
- 가입 유형→기본 역할 매핑은 `application.properties`의 `eeum.account.default-role.*`가 가리킴(역할명 하드코딩 금지 유지)

**V4 — 메뉴** (codex 교차검토 반영):
| 테이블 | 용도 | 핵심 제약 |
|---|---|---|
| `MENU_INFO` | SPA 3종 동적 메뉴(계층형) | 채널코드(MENU_CHNL 공통코드 — USER_TYPE과 별개), 단일 `AUTH_CD`(NULL=공개), `CHECK(LGN_NEED_YN='Y' OR AUTH_CD IS NULL)` 모순 차단, 활성유니크(채널+URL — 폴더형 NULL 허용), 조회 인덱스(채널/상위/USE_YN/DEL_YN/정렬) |
- 노출 규칙(앱): 사용중·미삭제 AND (로그인 불요 OR 로그인) AND (권한 불요 OR 보유) + **조상 체인 전체 노출 가능해야 자식 노출**(고아 제거)

---

## 4-4. V10 테이블 목록 (문의 게시판형 CS Q&A — 2026-06-18 적용)

회원이 작성하고 운영자가 답변하는 게시판형 문의(Q&A). 공개 게시판 + 비밀글 옵션, 운영자 블라인드(게시관리) 지원. 첨부(이미지)는 V12·V13 통합 첨부 토대 위에서 `INQR_ATTACH` 스코프로 연결(하이브리드 공개성). 명명은 행안부 공통표준용어 대조: 문의=`INQR`, 답변=`ANS`, 비공개게시물(비밀글)=`PRVT_PST`, 게시상태=`PSTG_STTS`.

| 구분 | 테이블 | 용도 | 핵심 제약 |
|---|---|---|---|
| 문의 | `INQR_INFO` | 문의글(게시판형) | FK(USER_SN→USER_ACNT), `INQR_TYPE_CD`(공통코드 INQR_TYPE), `PRVT_PST_YN`(비밀글 Y/N), `ANS_STTS_CD`(PENDING/ANSWERED), `PSTG_STTS_CD`(NORMAL/BLINDED)+`PSTG_STTS_RSN`, 조회 인덱스 4종(목록/내문의/상태/유형) |
| 문의 | `INQR_ANS_INFO` | 문의답변(운영자, 복수 허용) | FK(INQR_SN→INQR_INFO, ANS_USER_SN→USER_ACNT), `ANS_CN`, 인덱스 `IX_INQR_ANS(INQR_SN,DEL_YN,REG_DT,ANS_SN)` |

- 공통코드: `INQR_TYPE`(RESERVATION/PAYMENT/ACCOUNT/ETC) 그룹+코드 시드.
- 권한(codex 권고로 분리): `INQR_ANS`(문의 답변 → 역할 OPERATOR_STAFF) · `INQR_MODER`(문의 게시관리/블라인드 → 역할 OPERATOR_SUPERVISOR). 블라인드 처리자/일시는 `AUDIT_LOG_INFO`(@Audit)가 기록.
- 메뉴: 운영자 채널 "문의 관리"(`/inquiries`, 필요권한 `INQR_ANS`) 추가. USER 채널 "문의"는 V9에 이미 존재.

---

## 4-5. V11·V12·V13 (첨부 시스템 보강 — 2026-06-18 적용, `ATCH_FILE` ALTER·데이터)

테이블 신설 없이 `ATCH_FILE`(§4-2)와 권한 데이터를 보강한다. 통합 첨부(공개이미지/프로필/문의 등)를 한 토대에서 수용하기 위한 변경.

**V11 — 파일 업로드 권한(`FILE_UPLOAD`)**:
- 권한 `FILE_UPLOAD` 정의(멱등) + 역할 매핑(OPERATOR_STAFF/OPERATOR_SUPERVISOR/PARTNER). 공개 이미지 업로드를 "로그인한 누구나"에서 운영자·파트너로 제한(저장소 고갈·공개 호스팅 악용 방어).
- `ATCH_FILE.SCAN_STTS_CD` 허용값 코멘트에 `NOT_REQUIRED` 명시(AV 미연동 시 NULL 대신 명시 기록).
- 업로더 쿼터 조회 인덱스 `IX_ATCH_FILE_RGTR_REG (RGTR_SN, REG_DT)` 추가.

**V12 — 첨부 연결 생명주기(TEMP/LINKED)**: `ATCH_FILE`에 컬럼 추가
- `ATCH_STTS_CD` VARCHAR(20) NOT NULL DEFAULT `'LINKED'`(TEMP=업로드만 / LINKED=도메인 연결 — soft delete `DEL_YN`과 분리)
- `TEMP_EXPIRE_DT` DATETIME(6) NULL(TEMP 선업로드 TTL — 미연결 정리 기준)
- `LINKED_DT` DATETIME(6) NULL(도메인 연결 완료 일시)
- 인덱스: `IX_ATCH_FILE_SCOPE_TRGT (FILE_SCOPE_CD, TRGT_TYPE, TRGT_ID, ATCH_STTS_CD, DEL_YN)` 추가, `IX_ATCH_FILE_TEMP_EXPIRE (ATCH_STTS_CD, TEMP_EXPIRE_DT, DEL_YN)` 추가, 기존 `IX_ATCH_FILE_SCOPE` 제거(신규 복합 인덱스가 prefix로 대체). 스코프 값 검증은 앱 레벨(CHECK 미사용 — 새 용도 추가 시 ALTER 회피).

**V13 — 첨부 공개성(visibility, 하이브리드)**: `ATCH_FILE`에 컬럼 추가
- `ATCH_VISBLTY_CD` VARCHAR(20) NOT NULL DEFAULT `'PUBLIC'`(PUBLIC=전체 열람 / PRIVATE=작성자 본인+관리권한 운영자만) — 스코프(`FILE_SCOPE_CD`=용도/검증정책)와 분리된 접근권한 축.
- 정책: 글 공개성이 상한선 — 비밀 문의에는 공개 첨부 불가(공개글에서만 비공개 첨부 선택 가능). 기존 행(공개 이미지/프로필)은 모두 PUBLIC.

---

## 5. 공통 DDL 규칙

현재 V1 DDL은 [db-naming-standard.md](./db-naming-standard.md)의 공공데이터 공통표준용어 기반 명명 규칙을 따른다.

공통 규칙:

- 테이블/컬럼: `UPPER_SNAKE_CASE`
- surrogate PK: `{엔터티}_SN`
- 일시: `DATETIME(6)`
- 여부: `CHAR(1)` + `CHECK (... IN ('Y','N'))`
- 문자셋: `utf8mb4`
- 정렬/검색 인덱스는 명시 인덱스로 정의
- 모든 컬럼에 `COMMENT` 작성

공통 감사 컬럼:

| 컬럼 | 의미 |
|---|---|
| `REG_DT` | 등록일시 |
| `RGTR_SN` | 등록자 사용자일련번호 |
| `MDFCN_DT` | 수정일시 |
| `MDFR_SN` | 수정자 사용자일련번호 |
| `DEL_YN` | 삭제여부 |

삭제 정책:

- 즉시 물리 삭제하지 않는다.
- 삭제는 `DEL_YN='Y'`, `MDFCN_DT=NOW(6)`, `MDFR_SN=actor` 업데이트로 처리한다.
- 삭제시각은 별도 컬럼 없이 `MDFCN_DT`로 갈음한다.
- 개인정보/법정 보존기간 만료 후 purge 또는 익명화는 별도 배치에서 처리한다.

활성 유니크 전략:

- soft delete된 행과 신규 행의 unique 충돌을 피하기 위해 생성 컬럼을 사용한다.
- 예: `DEL_YN='N'`일 때만 원본 값을 생성 컬럼에 넣고, `DEL_YN='Y'`이면 `NULL` 처리한다.
- MySQL unique index에서 `NULL` 중복이 허용되는 특성을 이용한다.

---

## 6. 테이블 상세

### 6.1 `COMM_CD_GROUP` - 공통코드그룹

| 컬럼 | 타입 | 비고 |
|---|---|---|
| `COMM_CD_GROUP_SN` | `BIGINT` | PK, auto increment |
| `GROUP_CD` | `VARCHAR(50)` | 그룹코드 |
| `GROUP_NM` | `VARCHAR(100)` | 그룹명 |
| `EXPLN` | `VARCHAR(500)` | 설명 |
| `USE_YN` | `CHAR(1)` | 사용여부, `Y/N` |
| `REG_DT` | `DATETIME(6)` | 등록일시 |
| `RGTR_SN` | `BIGINT` | 등록자 |
| `MDFCN_DT` | `DATETIME(6)` | 수정일시 |
| `MDFR_SN` | `BIGINT` | 수정자 |
| `DEL_YN` | `CHAR(1)` | 삭제여부, `Y/N` |
| `ACTV_GROUP_CD` | generated | 활성행 유니크용 |

제약/인덱스:

- PK: `COMM_CD_GROUP_SN`
- UK: `UK_COMM_CD_GROUP (ACTV_GROUP_CD)`

### 6.2 `COMM_CD` - 공통코드

| 컬럼 | 타입 | 비고 |
|---|---|---|
| `COMM_CD_SN` | `BIGINT` | PK, auto increment |
| `GROUP_CD` | `VARCHAR(50)` | 그룹코드 |
| `CD` | `VARCHAR(50)` | 코드 |
| `CD_NM` | `VARCHAR(100)` | 코드명 |
| `SORT_SEQ` | `INT` | 정렬순서 |
| `USE_YN` | `CHAR(1)` | 사용여부, `Y/N` |
| `REG_DT` | `DATETIME(6)` | 등록일시 |
| `RGTR_SN` | `BIGINT` | 등록자 |
| `MDFCN_DT` | `DATETIME(6)` | 수정일시 |
| `MDFR_SN` | `BIGINT` | 수정자 |
| `DEL_YN` | `CHAR(1)` | 삭제여부, `Y/N` |
| `ACTV_GROUP_CD` | generated | 활성행 유니크용 |
| `ACTV_CD` | generated | 활성행 유니크용 |

제약/인덱스:

- PK: `COMM_CD_SN`
- UK: `UK_COMM_CD (ACTV_GROUP_CD, ACTV_CD)`
- IDX: `IX_COMM_CD_GROUP_SORT (GROUP_CD, SORT_SEQ)`
- `GROUP_CD`는 `COMM_CD_GROUP.GROUP_CD`를 논리 참조한다. 현재 V1에서는 FK를 걸지 않는다.

### 6.3 `USER_ACNT` - 사용자계정

`USER_ACNT`는 인증/로그인 책임을 가진 계정 테이블이다. 일반회원/파트너/운영자의 도메인 상세 프로필은 후속 V2 이후 별도 테이블로 분리한다.

| 컬럼 | 타입 | 비고 |
|---|---|---|
| `USER_SN` | `BIGINT` | PK, auto increment |
| `EML_ADDR` | `VARCHAR(150)` | 이메일주소, 로그인 ID |
| `ENPSWD` | `VARCHAR(255)` | 암호화비밀번호, 소셜 전용은 `NULL` |
| `USER_TYPE_CD` | `VARCHAR(30)` | 사용자유형코드 |
| `LGN_TYPE` | `VARCHAR(20)` | 로그인유형 |
| `ACNT_STTS` | `VARCHAR(20)` | 계정상태 |
| `USER_NM` | `VARCHAR(50)` | 사용자명 |
| `NCNM` | `VARCHAR(30)` | 별명 |
| `MBL_TELNO` | `VARCHAR(255)` | 휴대전화번호, 암호화 대상 |
| `MBL_TELNO_VRFC_YN` | `CHAR(1)` | 휴대전화번호인증여부 |
| `CI` | `VARCHAR(255)` | CI, 암호화 대상 |
| `CI_HASH` | `VARCHAR(64)` | CI 해시, 중복가입 판단/유니크용 |
| `LAST_LGN_DT` | `DATETIME(6)` | 최종로그인일시 |
| `WHDWL_DT` | `DATETIME(6)` | 탈퇴일시 |
| `REG_DT` | `DATETIME(6)` | 등록일시 |
| `RGTR_SN` | `BIGINT` | 등록자 |
| `MDFCN_DT` | `DATETIME(6)` | 수정일시 |
| `MDFR_SN` | `BIGINT` | 수정자 |
| `DEL_YN` | `CHAR(1)` | 삭제여부 |
| `ACTV_EML_ADDR` | generated | 활성 이메일 유니크용 |
| `ACTV_CI_HASH` | generated | 활성 CI 해시 유니크용 |

제약/인덱스:

- PK: `USER_SN`
- UK: `UK_USER_ACNT_EML (ACTV_EML_ADDR)`
- UK: `UK_USER_ACNT_CI (ACTV_CI_HASH)`
- IDX: `IX_USER_ACNT_TYPE_STTS (USER_TYPE_CD, ACNT_STTS)`

### 6.4 `ROLE_MST` - 역할

| 컬럼 | 타입 | 비고 |
|---|---|---|
| `ROLE_SN` | `BIGINT` | PK, auto increment |
| `ROLE_CD` | `VARCHAR(50)` | 역할코드 |
| `ROLE_NM` | `VARCHAR(100)` | 역할명 |
| `UPPER_ROLE_SN` | `BIGINT` | 상위역할일련번호 |
| `ROLE_STEP` | `INT` | 역할단계 |
| `SORT_SEQ` | `INT` | 정렬순서 |
| `USE_YN` | `CHAR(1)` | 사용여부 |
| `REG_DT` | `DATETIME(6)` | 등록일시 |
| `RGTR_SN` | `BIGINT` | 등록자 |
| `MDFCN_DT` | `DATETIME(6)` | 수정일시 |
| `MDFR_SN` | `BIGINT` | 수정자 |
| `DEL_YN` | `CHAR(1)` | 삭제여부 |
| `ACTV_ROLE_CD` | generated | 활성 역할코드 유니크용 |

제약/인덱스:

- PK: `ROLE_SN`
- UK: `UK_ROLE_CD (ACTV_ROLE_CD)`
- IDX: `IX_ROLE_UPPER (UPPER_ROLE_SN)`

### 6.5 `AUTH_MST` - 권한

| 컬럼 | 타입 | 비고 |
|---|---|---|
| `AUTH_SN` | `BIGINT` | PK, auto increment |
| `AUTH_CD` | `VARCHAR(80)` | 권한코드 |
| `AUTH_NM` | `VARCHAR(100)` | 권한명 |
| `USE_YN` | `CHAR(1)` | 사용여부 |
| `REG_DT` | `DATETIME(6)` | 등록일시 |
| `RGTR_SN` | `BIGINT` | 등록자 |
| `MDFCN_DT` | `DATETIME(6)` | 수정일시 |
| `MDFR_SN` | `BIGINT` | 수정자 |
| `DEL_YN` | `CHAR(1)` | 삭제여부 |
| `ACTV_AUTH_CD` | generated | 활성 권한코드 유니크용 |

제약/인덱스:

- PK: `AUTH_SN`
- UK: `UK_AUTH_CD (ACTV_AUTH_CD)`

### 6.6 `USER_ROLE` - 사용자역할

사용자와 역할의 N:M 매핑 테이블이다. 활성 유니크를 사용하므로 soft delete 후 같은 역할 재부여가 가능하다.

| 컬럼 | 타입 | 비고 |
|---|---|---|
| `USER_ROLE_SN` | `BIGINT` | PK, auto increment |
| `USER_SN` | `BIGINT` | 사용자일련번호 |
| `ROLE_SN` | `BIGINT` | 역할일련번호 |
| `REG_DT` | `DATETIME(6)` | 등록일시 |
| `RGTR_SN` | `BIGINT` | 등록자 |
| `MDFCN_DT` | `DATETIME(6)` | 수정일시 |
| `MDFR_SN` | `BIGINT` | 수정자 |
| `DEL_YN` | `CHAR(1)` | 삭제여부 |
| `ACTV_USER_SN` | generated | 활성 사용자 유니크용 |
| `ACTV_ROLE_SN` | generated | 활성 역할 유니크용 |

제약/인덱스:

- PK: `USER_ROLE_SN`
- UK: `UK_USER_ROLE (ACTV_USER_SN, ACTV_ROLE_SN)`
- IDX: `IX_USER_ROLE_ROLE (ROLE_SN)`
- FK: `USER_SN -> USER_ACNT.USER_SN`
- FK: `ROLE_SN -> ROLE_MST.ROLE_SN`

### 6.7 `ROLE_AUTH` - 역할권한

역할과 권한의 N:M 매핑 테이블이다. 활성 유니크를 사용하므로 soft delete 후 같은 권한 재부여가 가능하다.

| 컬럼 | 타입 | 비고 |
|---|---|---|
| `ROLE_AUTH_SN` | `BIGINT` | PK, auto increment |
| `ROLE_SN` | `BIGINT` | 역할일련번호 |
| `AUTH_SN` | `BIGINT` | 권한일련번호 |
| `REG_DT` | `DATETIME(6)` | 등록일시 |
| `RGTR_SN` | `BIGINT` | 등록자 |
| `MDFCN_DT` | `DATETIME(6)` | 수정일시 |
| `MDFR_SN` | `BIGINT` | 수정자 |
| `DEL_YN` | `CHAR(1)` | 삭제여부 |
| `ACTV_ROLE_SN` | generated | 활성 역할 유니크용 |
| `ACTV_AUTH_SN` | generated | 활성 권한 유니크용 |

제약/인덱스:

- PK: `ROLE_AUTH_SN`
- UK: `UK_ROLE_AUTH (ACTV_ROLE_SN, ACTV_AUTH_SN)`
- IDX: `IX_ROLE_AUTH_AUTH (AUTH_SN)`
- FK: `ROLE_SN -> ROLE_MST.ROLE_SN`
- FK: `AUTH_SN -> AUTH_MST.AUTH_SN`

### 6.8 `AUDIT_LOG_INFO` - 감사로그

감사로그는 행위 추적용 append-only 성격의 테이블이다. 탈퇴/삭제 후에도 감사 증적을 보존해야 하므로 `USER_ACNT` FK는 걸지 않는다.

| 컬럼 | 타입 | 비고 |
|---|---|---|
| `AUDIT_LOG_SN` | `BIGINT` | PK, auto increment |
| `ACTR_USER_SN` | `BIGINT` | 행위자 사용자일련번호 |
| `ACTR_TYPE` | `VARCHAR(20)` | 행위자유형 |
| `ACTN_CN` | `VARCHAR(100)` | 행위내용 |
| `TRGT_TYPE` | `VARCHAR(50)` | 대상유형 |
| `TRGT_ID` | `VARCHAR(50)` | 대상식별자 |
| `PRCS_RSLT` | `VARCHAR(20)` | 처리결과 |
| `CNTN_IP` | `VARCHAR(45)` | 접속IP |
| `USER_AGNT` | `VARCHAR(500)` | User-Agent |
| `CORR_ID` | `VARCHAR(64)` | correlationId |
| `REG_DT` | `DATETIME(6)` | 등록일시 |

제약/인덱스:

- PK: `AUDIT_LOG_SN`
- IDX: `IX_AUDIT_LOG_INFO_ACTR_REG (ACTR_USER_SN, REG_DT)`
- IDX: `IX_AUDIT_LOG_INFO_REG_DT (REG_DT)`
- IDX: `IX_AUDIT_LOG_INFO_CORR (CORR_ID)`

### 6.9 `LOGIN_LOG_INFO` - 로그인이력

로그인 시도/성공/실패 이력을 저장한다.

| 컬럼 | 타입 | 비고 |
|---|---|---|
| `LOGIN_LOG_SN` | `BIGINT` | PK, auto increment |
| `USER_SN` | `BIGINT` | 사용자일련번호 |
| `LGN_ID` | `VARCHAR(150)` | 로그인ID |
| `LGN_TYPE` | `VARCHAR(20)` | 로그인유형 |
| `PRCS_RSLT` | `VARCHAR(20)` | 처리결과 |
| `FAIL_RSN` | `VARCHAR(200)` | 실패사유 |
| `CNTN_IP` | `VARCHAR(45)` | 접속IP |
| `USER_AGNT` | `VARCHAR(500)` | User-Agent |
| `ISTR_NM` | `VARCHAR(100)` | 기기명 |
| `REG_DT` | `DATETIME(6)` | 등록일시 |

제약/인덱스:

- PK: `LOGIN_LOG_SN`
- IDX: `IX_LOGIN_LOG_INFO_USER_REG (USER_SN, REG_DT)`
- IDX: `IX_LOGIN_LOG_INFO_REG_DT (REG_DT)`

---

## 7. 현재 V1 관계 요약

```text
USER_ACNT 1 --< USER_ROLE >-- 1 ROLE_MST
ROLE_MST      1 --< ROLE_AUTH >-- 1 AUTH_MST

COMM_CD_GROUP --(GROUP_CD 논리참조)--< COMM_CD

AUDIT_LOG_INFO  : 사용자 행위 추적, FK 없음
LOGIN_LOG_INFO  : 로그인 이력, FK 없음
```

FK가 있는 관계:

- `USER_ROLE.USER_SN -> USER_ACNT.USER_SN`
- `USER_ROLE.ROLE_SN -> ROLE_MST.ROLE_SN`
- `ROLE_AUTH.ROLE_SN -> ROLE_MST.ROLE_SN`
- `ROLE_AUTH.AUTH_SN -> AUTH_MST.AUTH_SN`

FK를 의도적으로 생략한 관계:

- `COMM_CD.GROUP_CD -> COMM_CD_GROUP.GROUP_CD`
- `AUDIT_LOG_INFO.ACTR_USER_SN -> USER_ACNT.USER_SN`
- `LOGIN_LOG_INFO.USER_SN -> USER_ACNT.USER_SN`

---

## 8. 전체 목표 ERD 대비 현재 범위

현재 V1에 포함된 범위:

- 공통코드
- 사용자계정
- RBAC
- 감사로그
- 로그인이력

현재 V1에 아직 없는 범위:

- 소셜계정
- 일반회원 프로필
- 파트너 사업자
- 운영자 프로필
- 약관/동의
- 파일첨부
- 고택/객실
- 요금/재고
- 예약/결제/환불/정산
- 쿠폰/포인트/후기/찜/문의/알림/콘텐츠

후속 마이그레이션 권장 순서:

| 파일 | 범위 |
|---|---|
| `V2__account_profile_terms_file.sql` | 소셜계정, 프로필, 파트너, 운영자, 약관, 파일 |
| `V3__house_room_product.sql` | 고택, 객실, 이미지, 비품, 시설, 옵션, 정책 |
| `V4__rate_inventory.sql` | 요금제, 요금캘린더, 재고캘린더, 휴무 |
| `V5__reservation_payment.sql` | 예약, 가격스냅샷, 결제, 결제이벤트 |
| `V6__refund.sql` | 환불, 환불정책, PG취소 |
| `V7__settlement.sql` | 정산, 정산항목, 정산이력 |
| `V8__extra.sql` | 쿠폰, 포인트, 후기, 찜, 문의, 알림, 콘텐츠 |

---

## 9. 현재 정리 필요 사항

검토/인수인계 관점에서 아직 정리해야 할 항목:

- [x] ~~[erd.md](./erd.md)의 기존 `USER_ACCOUNT`, `COMMON_CODE`, `CREATED_AT` 계열 표기를 현재 공공 표준명으로 동기화~~ → **결정(2026-06-16): 동기화 안 함.** `erd.md`·`domain-model.md`는 **논리(개념) 모델로 유지**(미구현 엔터티 다수라 물리명 없음). 두 문서 머리말에 "실제 물리명은 db-schema-current.md 우선" 디스클레이머 명시로 갈음.
- [ ] V2 범위 확정: 약관/파일을 V2에 포함할지, 상품 V3와 분리할지 결정
- [ ] `COMM_CD.GROUP_CD` FK 생략 정책을 유지할지 결정
- [ ] 감사로그/로그인이력의 FK 생략 사유를 운영 문서에 반영
- [ ] `REG_DT`, `MDFCN_DT` 값 입력 책임을 DB default로 둘지, 애플리케이션에서 통일할지 결정
- [ ] seed 공통코드 전략 결정: `V` 마이그레이션 vs `R__seed_common_code.sql`
