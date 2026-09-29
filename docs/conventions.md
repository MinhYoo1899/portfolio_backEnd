# 개발 컨벤션 (팀 표준 약속)

> codex 최종 검토에서 지목된 "실무 기본 약속" P0를 합리적 기본값으로 확정. 착수 전 합의용.
> 표준 결정은 [architecture.md](./architecture.md) D1~D19, 본 문서는 그 하위 실무 규약. **이의 있으면 언제든 override.**

## 0. 자바 패키지 구조 (사용자 확정 — 2026-06-12 개편, 모든 도메인 모듈 공통 · **타 프로젝트 재사용 표준**)

> **이 구조를 벗어난 패키지 신설 금지.** 변경이 필요하면 먼저 사용자와 합의 후 본 절을 갱신한다.
> (2026-06-12 개편: `controller/{컨트롤러명}/` 중첩 제거 → `web` 평면화. eGov 표준({기능}.web/.service)과 정합 — codex 교차검토 통과)

```
egovframework.eeum.{업무}                ← 도메인 모듈 (예: auth, account, house …)
├─ web/                                 ← 웹 계층: 컨트롤러들을 바로 아래에 (컨트롤러별 하위 패키지 없음)
│   ├─ XxxController.java
│   ├─ YyyController.java               (도메인에 컨트롤러 여러 개면 나란히)
│   └─ dto/                             ← 요청/응답 DTO = 외부(프론트) 계약 (XxxRequest/XxxResponse)
├─ vo/                                  ← VO = DB·서비스 내부 데이터 그릇 (매퍼 result/parameter)
│                                          ⚠ web 밖에 두는 이유: 서비스→웹 역참조 방지 + VO 를 응답으로 노출(민감컬럼 유출) 방지
├─ exception/                           ← 모듈 예외처리 (ErrorCode 등)
└─ service/
    └─ {서비스명}/                       ← 서비스별 패키지 (예: auth = AuthService용)
        ├─ XxxService.java              (인터페이스)
        ├─ dao/                         ← DAO(MyBatis 매퍼 인터페이스)
        │   └─ XxxMapper.java
        └─ impl/
            └─ XxxServiceImpl.java      (구현 + 기술 컴포넌트 구현체)
```

- **DTO vs VO 구분(중요)**: DTO = 프론트와 약속한 계약(바뀌면 공지 필요, 외부 노출 가능한 것만) / VO = 내부 운반용(ENPSWD 같은 민감 컬럼 포함 가능 — **절대 응답으로 반환 금지**, 반드시 DTO 로 변환).
- 공통부는 2곳 고정: eGov 원본 공통 = `egovframework.com.*`(가급적 손대지 않음), 우리 공통 = `egovframework.eeum.base.*`(response/exception/security/audit).
- **도메인 간 의존 정책**: 다른 도메인의 **service 인터페이스·VO 사용은 허용**, **DAO(매퍼) 직접 접근은 금지** (예: account가 auth의 `TokenSessionStore` 사용 OK). `base`는 프로젝트 내 어떤 패키지에도 의존 금지. — `ArchitectureVerification`(ArchUnit)이 자동 검사(웹→DAO 금지 규칙은 `..web..` 기준).
- 매퍼 XML: `src/main/resources/egovframework/mapper/eeum/{업무}/XxxMapper_{DbType}.xml` (namespace = DAO FQCN — **VO/DTO 이동 시 parameterType/resultType 경로 주의**).
- 적용 사례(첫 모듈): `egovframework.eeum.auth` — web(AuthController + dto), vo(AuthUser·AuthAccountVO·LoginLogInfoVO), exception, service/auth(+dao/impl), service/token(+impl).
- DTO 가 많아지면 `web/dto/house`, `web/dto/room` 처럼 dto 하위만 추가 분리(컨트롤러별 패키지로 회귀 금지).

## 1. API 계약

### 파라미터 = DTO/VO 필수 (예외 없음)
- 모든 요청·응답 파라미터는 **DTO 또는 VO로 구성**해서 받고 반환한다. `@RequestParam` 개별 나열·`Map` 바인딩·raw `Map` 반환 **금지**.
- 컨트롤러 시그니처: 입력 `XxxRequest`/`XxxSearchRequest`(GET query도 검색 DTO/VO 바인딩), 출력 `XxxResponse`.
- eGov `ResultVO.result`(Map)를 쓰더라도 그 안엔 **타입드 DTO/VO**를 담는다(Map에 흩뿌리기 금지).

### ResultCode 체계
- 형식: **`{DOMAIN}_{REASON}`** 대문자 스네이크. 성공은 `SUCCESS`.
- 예: `SUCCESS`, `VALIDATION_ERROR`, `AUTH_INVALID_CREDENTIALS`, `AUTH_TOKEN_EXPIRED`, `RESERVATION_NOT_FOUND`, `RESERVATION_SOLD_OUT`, `PAYMENT_ALREADY_APPROVED`, `PARTNER_NOT_APPROVED`.
- 숫자코드 혼용 금지. `ResultCode`는 base의 enum/상수로 카탈로그 관리(도메인별 추가).

### HTTP status ↔ 예외 매핑 (envelope와 병행, D5)
| 상황 | HTTP | resultCode 예 |
|---|---|---|
| 조회/수정/삭제 성공 | 200 | SUCCESS |
| 생성(insert) 성공 | **201** | SUCCESS |
| 입력 검증 실패 | 400 | VALIDATION_ERROR |
| 인증 필요/실패 | 401 | AUTH_* |
| 권한 없음 | 403 | FORBIDDEN |
| 리소스 없음 | 404 | *_NOT_FOUND |
| 업무 충돌(중복/상태) | 409 | *_CONFLICT / *_ALREADY_* |
| 요청 과다 | 429 | RATE_LIMITED |
| 서버 오류 | 500 | INTERNAL_ERROR |

### 직렬화 규칙
- **날짜/시간**: API는 ISO-8601. 날짜 `yyyy-MM-dd`. **발생 시각(instant)은 UTC `Z` 노출** — 응답 DTO는 `Instant`로 `"2026-06-18T05:50:49.692060Z"` 형태(오프셋 없는 `LocalDateTime` 노출 금지). 변환은 DTO 경계(`from()`에서 `ldt.toInstant(ZoneOffset.UTC)`)에서만 — VO/매퍼는 `LocalDateTime` 유지. **JVM/DB 세션 = `UTC` 통일**(저장 `UTC_TIMESTAMP`), 숙박 날짜는 KST `LocalDate`. 화면의 KST 변환은 프론트 책임. 상세 `docs/timezone-standard.md`.
- **ID**: DB `BIGINT` → 응답 JSON **문자열**(D9). 요청도 문자열 허용.
- **boolean**: API는 JSON `true/false`만. DB `Y/N`(`DEL_YN` 등)은 **API에 노출 금지**(매핑 변환).
- **enum**: 문자열 코드(대문자 스네이크). ordinal 금지.
- **null/빈값**: 입력 문자열은 **trim**. 선택 필드의 빈 문자열은 null로 정규화. 응답 DTO는 **null 필드 포함**(계약 안정성).

## 2. 예외 / 에러

- 예외 계층(base): `BusinessException`(추상) ← `UnauthorizedException(401)`, `ForbiddenException(403)`, `NotFoundException(404)`, `ConflictException(409)`, `ValidationException(400)`, `ExternalApiException(502/503)`, `SystemException(500)`. 각자 `ResultCode` 보유.
- `GlobalExceptionHandler`가 envelope+status로 변환.
- **fieldErrors**: validation 실패 시 `result.fieldErrors: [{field, message}]`. **거절값(rejectedValue)은 미노출**(보안).
- **외부연동 실패 분류**: 결제 승인=동기실패+웹훅 보정 / 알림=outbox 재처리 / 사업자검증=동기실패. `ExternalApiException`으로 통일.
- **타임아웃/재시도**: connect 2s / read 5s 기본. 재시도는 **멱등 작업만**(GET·조회·멱등 POST), exponential backoff, 최대 2회, non-retryable(4xx) 즉시 실패.

## 3. 트랜잭션 / 동시성

- `@Transactional`: **Controller 금지, ServiceImpl public 메서드 기준**. 조회는 `readOnly=true`.
- **재고 동시성**: Redis hold(선점) + DB `UNIQUE`/`VERSION` 낙관락 + 원자적 update. 최종 정합성은 DB.
- **멱등성**: 예약생성·결제승인·취소요청은 멱등키(클라 `Idempotency-Key` 헤더 또는 서버 orderId). Redis에 키+결과 저장(TTL), 중복 요청은 **저장된 결과 재사용**. `PAYMENT.IDEMPOTENCY_KEY` UNIQUE.

## 4. 테스트

- 네이밍: **`*Verification`** (test 단어 금지 — 2026-06-11 명명 규칙 통일. surefire가 `*Verification.java`만 실행하며, `@Test` 보유 클래스의 명명 위반은 ArchUnit이 빌드 실패 처리). 위치는 `src/test`.
- 통합 테스트 DB: **Testcontainers (MySQL8 + Redis)**. (H2 미사용 — 문법 차이)
- **빌드 게이트(PR 필수)**: compile + test + **ArchUnit** + **Flyway validate** (+ 정적분석 P1).
- 커버리지 목표(P1): 핵심 use case/branch 중심, 60~70%부터.

## 5. 문서 / API

- **springdoc-openapi(Swagger)**: 모든 엔드포인트 `operationId`, request/response 예시, 주요 에러 예시 필수.
- API 버저닝: URL 무버전 유지(액션식). breaking change는 신규 엔드포인트/필드 추가 우선(deprecate 후 제거).

## 6. 설정 / 운영

- **환경변수**: 민감값(DB/JWT/PG/SFTP/Redis)은 환경변수 주입. testBoot 호환 위해 `DB_HOST/DB_PORT/DB_NAME/DB_USERNAME/DB_PASSWORD` 유지, 앱 설정은 `APP_*`/`EEUM_*` prefix. `.env`는 gitignore(미커밋).
- **profile**: `local/dev/stg/prod` — `application-{profile}.yml`.
- **fail-fast**: 필수 설정은 `@ConfigurationProperties` + validation → 누락 시 부팅 실패.
- **시크릿**: Git 금지, 운영은 secret manager 또는 CI secret(호스팅 확정 후).
- **타임존**: JVM `-Duser.timezone=UTC`, JDBC `serverTimezone=UTC`(instant=UTC 저장, 숙박 날짜=KST `LocalDate` — `docs/timezone-standard.md`).
- health: Actuator `/health`(liveness 가볍게, readiness는 DB/Redis 포함 — P1), graceful shutdown(P1).

## 7. 보안

- **로그 마스킹**: password, JWT/refresh token, phone, email, name, CI, 계좌, PG 승인정보 → 마스킹 필수.
- **입력 한도**: request body·query 길이 상한, `pageSize` 최대 100, 문자열 필드 max length 명시.
- **업로드**: 크기/확장자/MIME(Tika) 검증, 저장명 UUID, 공개이미지=object storage+CDN / 비공개=권한+검사.
- 보안헤더(P1): HSTS, X-Content-Type-Options, frame-options, CSP. rate limit 키(P1): login/auth/예약/결제 별도 한도.

## 8. 빌드 / 배포

- 빌드: Maven 멀티모듈. boot jar는 `eeum-app`에서 생성.
- **CI 단계**: build → test → (정적분석) → **Flyway validate** → package.
- **마이그레이션-배포 순서**: Flyway **선실행** → 앱 배포. 실패 시 롤백이 아니라 **보정 마이그레이션(롤포워드)**.
- 버전 스킴(P1): SemVer 또는 날짜기반(릴리즈 시 확정).

## 9. 협업 (P1 — 구현 중 PR 템플릿/ADR로 보강)
- 포매터: Spotless(+google-java-format 또는 Spring Java Format) 중 택1.
- 정적분석: SpotBugs/Checkstyle(+선택 Sonar).
- 커밋: Conventional Commits. 브랜치: GitHub Flow(장기 feature branch 지양). PR: 최소 1 승인 + 테스트 증빙. CODEOWNERS로 base/auth/domain/migration 소유자 지정.

---
> P0(1~8)는 착수 전 확정. P1/P2(9 및 각 항목 표기)는 구현하면서 PR 템플릿·ADR로 보강.
