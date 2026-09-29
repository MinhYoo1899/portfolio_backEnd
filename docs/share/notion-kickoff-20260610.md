# 이음(Eeum) 백엔드 — 프로젝트 셋업 & 진행 공유

> **공유일**: 2026-06-10 ｜ **작성**: 유민호 ｜ **저장소**: `github.com/parangsea/Eeum_back` (브랜치: `develop`)
> 이 페이지는 프로젝트 킥오프~현재까지의 **설계 결정 + 구축 현황 + 시작 방법**을 정리한 공유 문서입니다.
> 상세 문서는 전부 저장소 `docs/` 폴더에 있습니다(맨 아래 목차 참고).

---

## 1. 프로젝트 한눈에 보기

| 항목 | 내용 |
| --- | --- |
| 서비스 | **'이음' 고택(한옥)숙박 예약 플랫폼** (발주: 큐어㈜, 국가유산청/한국관광공사 연계) |
| 핵심 흐름 | 파트너(고택 사업자) → 고택 → 객실 → **예약 → 결제(토스) → 정산** |
| 클라이언트 | React SPA 3개(사용자/파트너/운영자) + 사용자 모바일 앱 → **백엔드 1개가 모두 서빙** |
| 사용자 역할 | 일반회원(앱·웹) / 파트너(고택 관리자, 운영자 승인 후 활성) / 운영자(운영자, 2차 인증) |

---

## 2. 기술 스택 (확정)

| 영역 | 선택 | 비고 |
| --- | --- | --- |
| 런타임 | **Java 17 (LTS)** | 전자정부 부트 5.0.0 기준선 — JDK 21 아님 주의! |
| 프레임워크 | **전자정부 표준프레임워크 부트 5.0.0** | = Spring Boot 3.5 기반 + eGov RTE |
| DB | **MySQL 8** (개발서버 원격, 스키마 `eeum`) | 추후 타 DBMS 전환 가능성 고려(이식성) |
| 영속성 | **MyBatis (XML 매퍼)** | 벤더별 SQL 분리(`*_mysql.xml`) |
| 마이그레이션 | **Flyway** | DB 변경 = 번호 SQL 파일, 부팅 시 자동 적용 |
| 캐시/토큰 | **Redis** | refresh 토큰, 예약 임시점유(hold), 캐시 |
| 인증 | **JWT (jjwt)** + Spring Security | 추후 SSO 분리 대비 설계 |
| 문서 | **Swagger** (springdoc 2.8.6) | `/swagger-ui/index.html` |

---

## 3. 꼭 알아야 할 개발 규칙 ⭐

> 전체 규칙은 저장소 `docs/conventions.md`, `docs/architecture.md`(결정 D1~D20) 참고. 아래는 필수만.

1. **DB 명명 = 행안부 공공데이터 공통표준용어** (예외 없음)
   - 테이블/컬럼 `UPPER_SNAKE_CASE` + 표준 약어: 이름=`NM`, 코드=`CD`, 일시=`DT`, 여부=`YN`, 일련번호=`SN`
   - 감사컬럼(모든 테이블): `REG_DT`(등록일시) `RGTR_SN`(등록자) `MDFCN_DT`(수정일시) `MDFR_SN`(수정자) `DEL_YN`(삭제여부)
   - **모든 컬럼에 COMMENT(논리명) 필수**
2. **Java/JS 변수 = camelCase** (MyBatis가 `USER_SN` → `userSn` 자동 매핑)
3. **모든 요청·응답 파라미터는 DTO/VO** — raw Map·개별 파라미터 나열 금지
4. **삭제는 soft delete** — 물리삭제 금지, `DEL_YN='Y'` 업데이트(삭제시각=MDFCN_DT). 유예기간 후 배치로만 purge
5. **API URL은 액션식** — `/{resource}/insert|list|detail|update|delete` (REST 아님, `/api` 프리픽스 없음)
6. **응답은 공통 envelope** — `{resultCode, resultMessage, result}` (result 안은 타입드 DTO)
7. **비즈니스 로직은 Java(Service)** — DB 프로시저/트리거 금지
8. **Flyway 철칙** — 이미 적용된 V파일 수정 금지, 변경은 새 번호 파일 추가
9. **목록 조회는 서버사이드 페이징** (그리드 연동, `PageResponse`: items/total/page/pageSize/totalPages)

---

## 4. 현재까지 완료된 것 ✅

### 설계 (codex 교차검토 다수 완료)
- 아키텍처 표준 D1~D20 확정, 도메인 모델(고택/객실/예약/결제/정산), ERD, 실무 컨벤션, DB 운영전략(백업/복구) 문서화

### 구축 & 검증
- ✅ 전자정부 부트 5.0.0 베이스 골격 (eGov 공통 인프라 + 데모 제거)
- ✅ **개발서버 MySQL 연동 + Flyway V1 적용** — `eeum` 스키마에 9개 테이블 생성
- ✅ DB 명명 공공표준 전면 적용 (codex 2회 검토 반영: 활성유니크, CI_HASH, CHECK 제약 등)
- ✅ **응답/예외 표준 계층** — envelope, 에러코드, GlobalExceptionHandler(400/401/403/404/405/406/415/500), 401/403 envelope 핸들러
- ✅ **codex 교차검토 3차까지 진행 → 최종 통과**
- ✅ Swagger 구성 (`이음 고택숙박 예약 API`)

### 2026-06-11 추가 완료 ✨
- ✅ **스키마 V2~V4 적용**: 계정상세 9테이블(소셜/프로필/파트너/약관/파일/전화인증) + 기준 시드(공통코드·역할·권한·약관) + 메뉴(`MENU_INFO`)
- ✅ **auth API**: 로그인/refresh(회전)/로그아웃/me — refresh는 웹=HttpOnly 쿠키·앱=body(확정), access 30분/refresh 14일
- ✅ **account/partner API**: 일반회원 가입(약관동의)·본인 조회/수정/탈퇴 + 파트너 가입 신청→운영자 승인/반려(권한코드 RBAC 동작)
- ✅ **menu API**: SPA 3종 동적 메뉴(`GET /menus/list`) — 로그인/권한별 필터
- ✅ **Redis 구축**(DB서버, ACL 보안) — refresh 세션 실증
- ✅ E2E 27건 통과 ｜ 상세 계약: [API 연동 규약]·[인증·권한 흐름 가이드] (06-11 갱신)
- ✅ **자동화 검증 체계**: ArchUnit(패키지 경계·명명 가드) + 상태코드 envelope 검증 — `mvn test` 20건, CI에서 매 push 실행
- ✅ **감사로그(AUDIT_LOG_INFO) 기록 로직**: `@Audit` AOP + 명시 호출 하이브리드, 행위자/IP/corrId 자동 수집, 커밋 정합·자동 롤백 시 FAIL 전환 — 응답헤더↔로그↔DB **corrId 3자 추적 동작**
- ✅ **암호화 저장 표준 확정** (AES-256-GCM/HMAC/파일 envelope — 구현은 별도 단계, `docs/crypto-standard.md`)
- ✅ **CI/CD + 개발서버 배포** 🚀: develop push → 자동 빌드·검증·배포(헬스체크 실패 시 자동 롤백). **개발서버 가동 중**: `http://minoservers.iptime.org:8082` (Swagger 포함) — [개발서버 이용 안내] 참고

### 2026-06-12~15 추가 완료 ✨
- ✅ **DB 명명 2차 정비 (V5~V7)**: 로그인 채널 제한·SUPER 역할(V5), 권한/공통코드 명명 표준화(V6), 기준테이블 `_MST` 접미 + 운영자 MASTER→OPERATOR(V7)
- ✅ **운영자 2단계 RBAC (V8)**: STAFF/SUPERVISOR 분화 — 조회(PRTNR_VIEW)/처리(PRTNR_APRV) 권한 분리
- ✅ **정식 메뉴 기준 데이터 (V9)**: 샘플 시드 폐기, USER/PARTNER/OPERATOR 57행
- ✅ **패키지 구조 개편**: `web`(컨트롤러 평면)+`web/dto`+도메인 루트 `vo` (eGov 표준, ArchUnit 강제)
- ✅ **품질·보안 강화 (B, 06-15)**: 감사·권한 매트릭스 회귀검증(총 45개 자동 테스트), CI 공급망보안(액션 SHA 고정·Dependabot), Swagger 운영 노출차단(secure-by-default), **로그인 무차별대입 방어(Redis)** + **JWT 비밀값 fail-fast**
- ✅ **공통코드 조회 API** (`GET /codes/list?groupCd=`) — 프론트 상태/유형 코드 런타임 제공(공개, 독립검토 통과)
- ✅ **구글·카카오 소셜 로그인** (`POST /auth/social-login`·`/auth/social-signup`, `provider:"GOOGLE"|"KAKAO"`) — OIDC id_token 검증→가입(약관)/로그인→JWT. 실제 구글·카카오 계정으로 신규가입·재로그인 끝단 실증 완료(codex 검토 통과). 공통 OIDC 베이스 추출로 네이버·애플은 키 발급 후 provider만 추가

### 2026-06-16 추가 완료 ✨
- ✅ **파일 첨부 API (공개 이미지, Phase 1)** (`POST /files/insert`·`GET /files/download`·`/files/detail`·`POST /files/delete`) — 멀티파트 업로드(확장자·50MB·매직바이트 검증)→SFTP 저장(chroot 전용계정)→백엔드 경유 스트리밍 다운로드. 개발서버 **실연동 업로드·다운로드·삭제 끝단 검증 완료**. codex 설계+코드 2회 교차검토(세션누수 등 결함 수정 반영). 비공개문서(암호화)는 Phase 2.
  - ✅ **정책 강화(2026-06-18, V11)** — 업로드를 **운영자·파트너 권한(`FILE_UPLOAD`)으로 제한**(일반회원 403), **업로드 쿼터**(1인 24h 50건·500MB)·**해상도 50MP**(픽셀폭탄 차단)·`SCAN_STTS_CD='NOT_REQUIRED'`·멀티파트 한도 정합. codex 정책 상의 + 코드 교차검토 8건 반영, 개발서버 **통합테스트(권한 201/403, 멀티파트, 다운로드 무결성) 통과**.

### 2026-06-18 추가 완료 ✨
- ✅ **통합 첨부 시스템 — 프로필 이미지 + 문의 첨부(하이브리드 공개성)** (V12·V13): `ATCH_FILE` 에 연결 생명주기(`ATCH_STTS_CD` TEMP/LINKED·TTL)·공개성(`ATCH_VISBLTY_CD` PUBLIC/PRIVATE) 축 추가로 용도별 첨부를 한 토대에서 수용.
  - **프로필 이미지** `POST /accounts/profile-image` — 본인 인증만(별도 권한 불요), jpg/jpeg/png·5MB, EXIF/GPS 제거 재인코딩, 업로드 즉시 교체(이전 이미지 자동 정리).
  - **문의 첨부** — 선업로드(`POST /inquiries/files/upload`, 24h 미연결 자동정리)→작성/수정 `attachments` 로 연결, 다운로드(`GET /inquiries/files/download`)는 **하이브리드 권한판정**(공개글+PUBLIC=누구나 / 비밀글·PRIVATE·삭제·블라인드=작성자·운영자만 404). 문의당 5개·1건 10MB·총 30MB.
  - **하이브리드 공개성**: 공개글=PUBLIC/PRIVATE 선택(기본 PUBLIC), 비밀글=PRIVATE 만(공개 차단), 공개→비밀 전환 시 첨부 전부 PRIVATE. 상세 계약: [API 연동 규약] §8-3b·§8-4 참고.

### 현재 DB (V1~V9)
| 구분 | 테이블 |
| --- | --- |
| 공통코드 | `COMM_CD_GROUP`, `COMM_CD` (+시드: USER_TYPE/SOCIAL_PRVDR/TRMS_TYPE/AGRE_CHNL/MENU_CHNL) |
| 계정 | `USER_ACNT`, `USER_SOCIAL_ACNT`, `USER_DTL_INFO`, `OPERATOR_DTL_INFO`, `MBL_TELNO_VRFC_INFO` |
| 파트너 | `PRTNR_MST`, `PARTNER_APPROVE_LOG_INFO` |
| 권한(RBAC) | `ROLE_MST`, `AUTH_MST`, `USER_ROLE`, `ROLE_AUTH` — 역할/권한 100% 데이터 주도 (+기본 역할 3종·권한 시드) |
| 약관/파일 | `TRMS_MST`, `TRMS_AGRE_INFO`, `ATCH_FILE` |
| 메뉴 | `MENU_INFO` (SPA 3종 동적 메뉴) |
| 로그 | `AUDIT_LOG_INFO`(감사), `LOGIN_LOG_INFO`(로그인 이력 — 동작 중) |

> 상세 스키마: 저장소 `docs/db-schema-current.md`

---

## 5. 시작하기

### 사전 준비
1. **JDK 17** 설치 (21 아님!) — `mvn -v`로 Java 17 확인. JAVA_HOME이 21이면 17로 변경
2. Maven 3.9+, Git

### 실행 순서
```bash
# 1. 클론 (develop 브랜치)
git clone https://github.com/parangsea/Eeum_back.git
cd Eeum_back
git checkout develop

# 2. 환경변수 — 저장소의 .env.develop 값 참고
#    (IntelliJ: Run/Debug Configurations > Environment variables 에 입력)
#    DB_HOST / DB_PORT / DB_NAME=eeum / DB_USERNAME / DB_PASSWORD

# 3. 실행 (부팅 시 Flyway가 DB 자동 동기화)
mvn spring-boot:run
```

### 확인
- 부팅 로그: `Started EeumBackendApplication` + Flyway `Successfully applied`(또는 `up to date`)
- Swagger: http://localhost:8080/swagger-ui/index.html
- DB: `USE eeum; SHOW TABLES;` → 19개 테이블 + `flyway_schema_history` (V1~V4)
- 환경변수에 `SPRING_PROFILE=dev` 포함 필수 (개발 시드 적용 — `.env.develop` 참고)

### DB를 바꾸고 싶을 때 (Flyway)
- `src/main/resources/db/migration/`에 **다음 번호 파일 추가**: `V2__설명.sql`
- 앱 재시작하면 전원 자동 적용. **기존 V 파일은 절대 수정 금지**

---

## 6. 프로젝트 구조

```
Eeum_back (단일 모듈 — 모듈러 모놀리식)
├─ src/main/java/egovframework/
│   ├─ com/        ← 전자정부 공통 인프라 (config/jwt/security/cmm)
│   ├─ let/utl/    ← eGov 공통 유틸
│   └─ eeum/       ← ★ 우리 도메인 (여기에 개발)
│       ├─ EeumBackendApplication.java
│       └─ base/   ← 공통 토대: response(envelope/페이징), exception(에러코드/핸들러), security(401/403)
│       └─ (예정) account/ auth/ house/ room/ reservation/ payment/ settlement/ ...
├─ src/main/resources/
│   ├─ application.properties   ← 설정 (시크릿은 환경변수 주입)
│   ├─ db/migration/V1__*.sql   ← Flyway 마이그레이션
│   └─ egovframework/mapper/    ← MyBatis XML (벤더별 *_mysql.xml)
└─ docs/  ← 설계/규약 문서 전체
```

> 패키지 의존 규칙: `base`는 도메인 비의존(다른 프로젝트의 토대로 재사용 예정). 도메인 간 mapper 직접 접근 금지.

---

## 7. 다음 계획 🗺

| 순서 | 작업 | 내용 |
| --- | --- | --- |
| 1 | ~~account/auth 도메인~~ | ✅ **완료(06-11)** — 로그인/refresh/계정 CRUD/파트너 승인/동적 메뉴 |
| 2 | 상품 | 파트너 → 고택 → 객실 + 이미지/비품/시설 (+로그 테이블군 보강 예정) |
| 3 | 요금/재고 (V4) | 요금 캘린더, 재고 캘린더, 휴무 |
| 4 | 예약 (V5) | Redis 임시점유(hold) → 중복예약 방지 |
| 5 | 결제 (V5~6) | 토스페이먼츠, 웹훅 멱등성, 환불 |
| 6 | 정산 (V7) | 파트너별 정산 (수수료 정책은 큐어 협의 후) |

### 외부 확정 대기 (병렬)
- 큐어 협의: 환불 8시간 정책 해석, 정산 수수료율, 객실 재고 단위
- 인프라: 배포 호스팅/클라우드, 이미지 저장소(S3/NCP), PG 키 발급

---

## 8. 저장소 문서 목차 (`docs/`)

| 문서 | 내용 |
| --- | --- |
| `architecture.md` | 아키텍처 표준 — 확정 결정 D1~D20 |
| `domain-model.md` | 고택숙박 도메인 모델 (모듈/테이블/URL 설계) |
| `erd.md` | 전체 목표 ERD (개념 설계) |
| `db-schema-current.md` | **현재 실제 DB 스키마** (V1~V9 기준 — 구현 시 이걸 우선) |
| `db-naming-standard.md` | DB 명명 표준 (공공표준용어 약어표) |
| `flyway-guide.md` | Flyway 사용법 (처음 쓰는 사람용) |
| `conventions.md` | 실무 컨벤션 (API 계약/예외/트랜잭션/테스트) |
| `db-operations.md` | DB 운영 전략 (백업/복구/계정분리) |
| `base-architecture.md` | 베이스 골격 구조 (모듈 경계/SSO 분리 대비) |
| `decisions-pending.md` | 미결정 항목 카탈로그 (결정 주체별) |

**공유용 문서 (`docs/share/`)** — Notion 붙여넣기용:
| 대상 | 문서 | 내용 |
| --- | --- | --- |
| 전체 | `notion-kickoff-20260610.md` | 이 페이지 (프로젝트 셋업 & 진행 공유) |
| **전체** | `dev-server-guide-20260611.md` | **개발서버 이용 안내** (주소/Swagger/테스트계정/자동배포) 🆕 |
| 전체 | `erd-20260610.md` | ERD — 현재 V1 다이어그램(mermaid) + 전체 목표 ERD |
| 전체 | `ddl-v1-20260610.md` | DDL — 현재 적용된 V1 전체 |
| **프론트** | `frontend-api-guide-20260610.md` | **API 연동 규약** (envelope/에러/페이징/포맷 — 필독) |
| **프론트** | `frontend-auth-guide-20260610.md` | **인증·권한 흐름** (로그인/401·403/역할별) |
| **프론트** | `frontend-common-code-guide-20260610.md` | 공통코드 사용 가이드 |
| **프론트** | `screen-api-mapping-20260610.md` | 화면-API 매핑 초안 (SPA 3종) |
| 백엔드 | `backend-handbook-20260610.md` | 합류자 개발 핸드북 (코드 작성 규칙) |
| 백엔드 | `flyway-db-rules-20260610.md` | Flyway·DB 운영 규칙 |
| 기획·PM | `business-flow-states-20260610.md` | 업무 흐름/상태 정의서 (상태머신) |
| 기획·PM | `mvp-release-plan-20260610.md` | MVP 범위 & 릴리즈 계획 (의존성 트래커) |

---