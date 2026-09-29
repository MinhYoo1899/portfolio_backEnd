# Eeum 백엔드 아키텍처 (운영 기준 문서)

> 신규 운영 프로젝트의 **단일 기준 문서**. 모든 모듈은 여기 정의된 표준을 따른다.
> 설계 원칙 한 줄 요약:
> **"패키지·타입 안정성은 Spring Boot 현대 방식으로, URL·ServiceImpl·응답코드·Mapper XML·공통코드는 전자정부(eGov) 친화 방식으로."**

참고 기반: 검증된 `testBoot` 프로젝트 패턴 + 전자정부 표준프레임워크(eGovFrame) 관례.

---

## 1. 기술 스택

| 영역 | 선택 | 비고 |
| --- | --- | --- |
| 언어/런타임 | **Java 17 (LTS)** | 전자정부 표준프레임워크 부트 5.0.0 기준선 (21 미사용) |
| 프레임워크 | **전자정부 표준프레임워크 부트 5.0.0** (= Spring Boot 기반; 버전은 `egovframe-boot-starter-parent`가 결정) + eGov RTE | Web(MVC), Security, Actuator, Validation |
| 영속성 | MyBatis (`mybatis-spring-boot-starter` 3.0.4) | **XML 매퍼** |
| DB | MySQL 8 / InnoDB / utf8mb4 | |
| 인증 | jjwt 0.12.6 + Spring Security | Bearer JWT(access) + Refresh |
| 캐시/토큰 | **Redis** (spring-data-redis) | refresh 토큰·캐시 저장(거의 확정). `TokenStore` 추상화로 교체 가능 |
| 빌드 | Maven | |
| 문서 | springdoc-openapi (Swagger UI) | |

---

## 2. 확정 설계 결정 (Decision Record)

| # | 항목 | 결정 | 비고 |
| --- | --- | --- | --- |
| D1 | 패키지 루트 | **`egovframework.eeum`** (eGov 공통 인프라는 `egovframework.com.*` 그대로 유지) | eGov 부트 베이스 채택 → 단일 루트 통일(공공 재사용 친화). 우리 도메인 = `egovframework.eeum.{member,reservation,...}` |
| D2 | 구조 | **기능 단위(feature-based)**, 공통은 `global/` 분리 | 인수인계·장애추적 유리 |
| D3 | 서비스 레이어 | **`XxxService`(interface) + `XxxServiceImpl`** | eGov 감리/인수인계 친화 |
| D4 | URL 컨벤션 | **`/{resource}/insert\|list\|detail\|update\|delete`**, `/api` 프리픽스 없음 | 액션식 |
| D5 | 응답 포맷 | **공통 envelope `{resultCode, resultMessage, result}`**, `result` 안은 typed DTO | SI/연계/감리 친화 |
| D6 | 명명 규칙(필수) | DB 테이블/컬럼 `UPPER_SNAKE_CASE` · **Java/JavaScript 변수 `camelCase`** · JSON 키 camelCase · MyBatis `map-underscore-to-camel-case` | **예외 없는 필수 조건.** 커스텀 매핑 코드 없음 |
| D7 | 공통코드 | `COMM_CD_GROUP` + `COMM_CD` 2테이블 | 상태값 일원 관리 |
| D8 | 감사(audit) 컬럼 | 모든 업무 테이블에 **`REG_DT`(등록일시)·`RGTR_SN`(등록자)·`MDFCN_DT`(수정일시)·`MDFR_SN`(수정자)·`DEL_YN`(삭제여부)** | soft delete 기본. 삭제시각은 `MDFCN_DT`로 갈음. 모든 컬럼에 COMMENT(논리명) 필수 |
| D9 | ID 직렬화 | DB `BIGINT` → 응답 DTO에서 **문자열** 직렬화 | 프론트 계약(`id: string`) |
| D10 | 서비스 도메인 | **'이음' 고택(한옥)숙박 예약 플랫폼** | 파트너→고택→객실→예약→결제→정산. 발주 큐어㈜ |
| D11 | 권한 모델 | **완전 데이터 주도 RBAC** (`ROLE_MST`+`USER_ROLE`+`AUTH_MST`+`ROLE_AUTH`, 역할 계층 지원) | 역할명 코드 하드코딩 금지. 사용자/공급자/관리자 또는 상위/하위관리자 등 **미확정 구조 모두 수용** |
| D12 | 인증 방식 | **Bearer JWT(access 단기) + Refresh 토큰**, refresh는 **Redis 저장**/회전·폐기 | 멀티 SPA + 모바일 앱. `TokenStore` 인터페이스로 추상화 |
| D13 | 멀티 클라이언트 | 단일 백엔드/DB + **3개 React SPA(사용자·파트너·운영자) + 사용자 모바일 앱** | 각 SPA origin별 CORS 목록화. 역할별 컨트롤러 분리/서비스 공유. (통폐합 시 SPA 2개 가능) |
| D14 | 배포 전제 | **Redis 채택으로 다중 인스턴스 준비** + 단일 인스턴스로도 동작 | 스케줄러 다중화는 후속(ShedLock) |
| D15 | 베이스 골격 | **모듈러 모놀리식**: eGov 부트 **단일 모듈** + 패키지 경계 `base`/`auth`/`domain`(ArchUnit 강제). 물리 모듈 분리는 추후(2nd 프로젝트가 base 재사용 시) | 전자정부 표준 단일구조 친화 + base 재사용/분리 가능성 보존. (감리 강제 없음 확인) |
| D16 | 인증 분리 대비 | **auth를 리소스서버처럼**: 토큰 OIDC 호환 클레임(iss/sub/aud/scope), `TokenVerifier` 추상, 인증주체(account_id)와 도메인 PK 분리, 내부는 `CurrentUser`만 신뢰 | 통합로그인(SSO) 분리 시 흔들림 최소화 |
| D17 | 비즈니스 로직 위치 | **Java(Service) 레이어에 둔다. DB 프로시저/트리거 미사용** | 추적·테스트·DB 이식성 유리 (사용자 확정) |
| D18 | 목록 페이지네이션 | **서버사이드 페이징 기본**(offset/page + `total` 반환), 정렬·필터도 서버. 무한스크롤만 커서 기반 | 프론트 React 그리드 라이브러리 연동 (사용자 확정) |
| D19 | 삭제 정책 | **즉시 물리삭제 금지 → `DEL_YN='Y'` 업데이트(soft delete)**. 삭제시각=`MDFCN_DT`. 유예기간(MDFCN_DT 기준) 경과분만 **퇴거(purge) 배치로 물리삭제/익명화**, 그 전까지 DB 보관 | 개인정보 파기·익명화 정책 준수, `DATA_PURGE_LOG` 증적 (사용자 확정) |
| D20 | 실무 컨벤션 | API계약(ResultCode·status매핑·ISO8601·KST·ID문자열·enum문자열)·예외계층·트랜잭션/멱등·테스트(*Test/*IT,Testcontainers)·CI게이트·설정/보안 규약 | 상세 [conventions.md](./conventions.md). 착수 전 P0 확정 |

> 금지: 클래스/변수/테이블/컬럼에 `test`, `imsi`, `sample`, `temp` 등 임시성 명칭 사용 금지.

---

## 3. 패키지 구조 (2026-06-12 확정 표준 — 상세·이유는 `conventions.md` §0)

> 초기 초안(com.eeum/global/...)은 D1(패키지 루트=egovframework.eeum) 확정과 2026-06-12 구조 개편으로 대체됨.

```
egovframework
├─ EeumBackendApplication          @SpringBootApplication (매퍼 스캔은 EgovConfigAppMapper — egovframework.eeum)
├─ com/                            eGov 공통 인프라 (config/jwt/security/cmm — 수정 최소화)
└─ eeum/
   ├─ base/                        공통 토대 (response/exception/security/audit) — 프로젝트 내 의존 금지(ArchUnit)
   └─ {domain}/                    auth · account · menu · (예정) house, reservation …
      ├─ web/                      컨트롤러들 바로 아래 + dto/(요청·응답 = 프론트 계약)
      ├─ vo/                       DB·서비스 내부 그릇 — 응답으로 노출 금지(민감컬럼)
      ├─ exception/                도메인 ErrorCode
      └─ service/{서비스명}/        인터페이스 + dao/(MyBatis 매퍼) + impl/(구현)
```

리소스:
```
src/main/resources/
├─ application.properties (+ application-dev.properties)
├─ egovframework/mapper/eeum/{domain}/XxxMapper_mysql.xml   (namespace = DAO FQCN, 벤더 접미 필수)
└─ db/migration/ V1__…sql ~        (Flyway — 수동 적용: scripts/db-migrate-dev.ps1)
   db/migration-dev/ R__…sql       (개발 전용 시드)
```

---

## 4. 레이어링

```
Controller (HTTP, @Valid, 상태코드, ApiResponse 래핑)
   → Service (interface)  →  ServiceImpl (비즈니스 규칙, @Transactional, 권한)
      → Mapper (MyBatis XML, SQL)
         → MySQL
```

- **DTO ↔ 도메인 분리**: 응답 DTO가 프론트 계약(id 문자열, ISO 날짜) 책임. 도메인은 DB 친화(Long id, LocalDateTime).
- **에러 단일화**: 서비스/컨트롤러는 `ApiException`을 던지고, `GlobalExceptionHandler`가 envelope + HTTP 상태코드로 변환.

---

## 5. URL 컨벤션 (액션식)

리소스별 base path 하위에 액션을 붙인다. `/api` 프리픽스 없음.

```
POST /{resource}/insert     생성   → 201 Created
GET  /{resource}/list       목록   → 200 OK  (page/pageSize/keyword 등 query)
GET  /{resource}/detail     단건   → 200 OK  (?id= query)
POST /{resource}/update     수정   → 200 OK
POST /{resource}/delete     삭제   → 200 OK  (envelope 일관성 위해 204 대신 200)
```

예시(member):
```
POST /members/insert
GET  /members/list?page=1&pageSize=20&keyword=홍길동&statusCode=ACTIVE
GET  /members/detail?memberId=100
POST /members/update
POST /members/delete
```

- 조회는 `GET` + query parameter, 변경(insert/update/delete)은 `POST` + JSON body로 통일.
- 단건/목록 계약을 분리: 단건은 `/detail`, 목록은 `/list`.

---

## 6. 공통 응답 포맷

모든 응답은 envelope로 감싼다. `result` 안은 반드시 typed DTO.

성공:
```json
{ "resultCode": "SUCCESS", "resultMessage": "정상 처리되었습니다.", "result": { "...": "..." } }
```

목록(`result`가 PageResponse):
```json
{ "resultCode": "SUCCESS", "resultMessage": "정상 조회되었습니다.",
  "result": { "items": [], "total": 0, "page": 1, "pageSize": 20, "totalPages": 0 } }
```

오류:
```json
{ "resultCode": "VALIDATION_ERROR", "resultMessage": "입력값이 올바르지 않습니다.", "result": null }
```

이중 코드 체계 원칙:
- **HTTP status** = 기술적 성공/실패 (400/401/403/404/409/500)
- **resultCode** = 업무/프론트 분기 코드 (`SUCCESS`, `VALIDATION_ERROR`, `NOT_FOUND` 등)
- **resultMessage** = 사용자/운영자 노출 메시지

```java
public record ApiResponse<T>(String resultCode, String resultMessage, T result) {
    public static <T> ApiResponse<T> success(T result) {
        return new ApiResponse<>("SUCCESS", "정상 처리되었습니다.", result);
    }
    public static <T> ApiResponse<T> success(String message, T result) {
        return new ApiResponse<>("SUCCESS", message, result);
    }
}

public record PageResponse<T>(List<T> items, long total, int page, int pageSize, int totalPages) {}
```

### 목록/그리드 페이지네이션 (서버사이드 기본)
- 모든 목록·그리드 조회는 **서버사이드 페이징**이 기본. (프론트는 무료 라이선스 React 그리드 라이브러리 사용 예정.)
- 방식: **offset/page 기반** — 요청 `page`(1-base)·`pageSize`·`sort`·필터(query), 응답 `PageResponse`(`items`+`total`+`totalPages`).
  그리드의 "총 N건 / 페이지 번호"를 위해 **`total` 카운트를 함께 반환**한다.
- 정렬·검색·필터도 **서버에서 처리**(정렬은 컬럼 whitelist). 클라이언트 전체 로드 후 클라 페이징 금지(대용량 DoS·성능).
- **예외 = 무한 스크롤**: 이 경우만 **커서(cursor) 기반**(마지막 키 이후 N건)을 별도 제공. 기본은 offset/page.
- `pageSize` 상한(예: 100)으로 과도 조회 차단.
```

---

## 7. 명명 규칙

- DB 테이블/컬럼: `UPPER_SNAKE_CASE` (예: `MEMBER_ID`, `PASSWORD_HASH`, `STATUS_CODE`).
- Java 프로퍼티: `lowerCamelCase` (예: `memberId`, `passwordHash`, `statusCode`).
- 변환: MyBatis `map-underscore-to-camel-case: true` (커스텀 코드 없음).
- 테이블명 주의: `USER`는 예약어 위험 → `USER_ACNT`. 단독 `CODE/GROUP` 금지 → `COMM_CD`, `COMM_CD_GROUP`(공공표준 약어).

표준 audit 컬럼 (모든 업무 테이블):
```
REG_DT(등록일시), RGTR_SN(등록자), MDFCN_DT(수정일시), MDFR_SN(수정자), DEL_YN(삭제여부 'N'/'Y')   -- 삭제시각은 MDFCN_DT로 갈음. 모든 컬럼 COMMENT 필수
```

도메인 모듈 후보(이음 서비스): `auth`, `member`, `organization`, `content`/`notice`, `inquiry`, `attachment`, `code`(공통코드), `audit-log`, `notification`, `consent`.

---

## 8. 첫 스프린트 범위 (골격 확정)

순서: **global → code(공통코드) → auth → member → attachment(골격)**

1. `global` — `ApiResponse`, `PageResponse`, `ResultCode`, `ErrorCode`, `ApiException`, `GlobalExceptionHandler`, `SecurityConfig` 골격, validation 오류 포맷.
2. `code` — `COMM_CD_GROUP`/`COMM_CD` 테이블 + `/codes/list`, `/code-groups/list`.
3. `auth` — `/auth/login`, `/auth/refresh`, `/auth/logout`, JWT 발급/검증, BCrypt.
4. `member` — `/members/{insert|list|detail|update|delete}` 풀스택 (표준 참조 구현).
5. `attachment` — 테이블/도메인/Mapper 골격만. AES-GCM·SFTP는 2차.

> 첫 스프린트의 목표는 기능 수가 아니라 **"앞으로 모든 모듈이 따라갈 표준"** 을 굳히는 것.
> `member` 모듈을 표준 레퍼런스 구현으로 삼는다.
