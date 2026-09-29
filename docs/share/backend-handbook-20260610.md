# 백엔드 개발 핸드북

> **대상**: 백엔드 합류 개발자 ｜ 실행 방법은 [킥오프 문서 §5] 참고 — 여기는 **코드 작성 규칙** 중심.
> 전체 규약 원본: 저장소 `docs/conventions.md`, `docs/architecture.md`(D1~D20)

---

## 1. 패키지 구조 & 새 도메인 추가 위치 (2026-06-12 개편 확정 — 이 구조 외 패키지 신설 금지)

```
egovframework/
├─ com/          ← 전자정부 공통 인프라 (수정 최소화 — 변경 시 팀 공유)
└─ eeum/
    ├─ base/     ← 공통 토대 (response/exception/security/audit) — 도메인 코드 넣기 금지!
    └─ {domain}/ ← 여기에 개발 (예: auth, account, house, reservation)
        ├─ web/                      ← 컨트롤러들 바로 아래 (컨트롤러별 하위 패키지 없음)
        │   ├─ XxxController.java    (여러 개면 나란히)
        │   └─ dto/                  ← 요청/응답 DTO (XxxRequest / XxxResponse) = 프론트 계약
        ├─ vo/                       ← VO = DB·서비스 내부 그릇 — ⚠ 응답으로 반환 금지(민감컬럼!)
        ├─ exception/                ← XxxErrorCode 등 도메인 예외
        └─ service/
            └─ {서비스명}/            ← 서비스별 패키지 (예: auth/, token/)
                ├─ XxxService.java   (인터페이스)
                ├─ dao/  XxxMapper.java   ← DAO(MyBatis 매퍼 인터페이스)
                └─ impl/ XxxServiceImpl.java
```
- **첫 실제 사례 = `eeum/auth` 모듈** — 새 도메인 만들 때 그대로 참고. (구 `controller/{이름}/` 구조는 2026-06-12 web 평면화로 개편 — eGov 표준 정합, codex 검토 통과)
- MyBatis XML: `src/main/resources/egovframework/mapper/eeum/{domain}/XxxMapper_mysql.xml` (**벤더 접미사 `_mysql` 필수** — 스캔 패턴 `*_{DbType}.xml`, namespace = DAO 인터페이스 FQCN)

## 2. 필수 규칙 (위반 시 리뷰 반려)

1. **모든 요청·응답 = DTO/VO** — `Map` 바인딩/반환, `@RequestParam` 나열 금지. GET 검색도 SearchRequest DTO로.
2. **응답은 `ApiResponse`** — `ApiResponse.ok(dto)` / 목록은 `PageResponse.of(items,total,page,pageSize)` 를 result에.
3. **오류는 예외로** — `throw new BusinessException(ErrorCode...)` → GlobalExceptionHandler가 envelope 변환. 컨트롤러에서 오류 응답 직접 조립 금지.
   - 도메인 오류코드는 도메인 패키지의 `exception/`에 `XxxErrorCode enum implements ErrorCode` 추가 (`DOMAIN_REASON` 형식 — 예: `AUTH_INVALID_CREDENTIALS`).
   - **예외 메시지에 내부정보·민감정보 금지** (그대로 클라이언트에 노출됨). 원인 보존은 cause 파라미터로.
4. **트랜잭션**: `@Transactional`은 ServiceImpl public 메서드. 조회는 `readOnly = true`. 컨트롤러 금지.
5. **비즈니스 로직은 Java** — 프로시저/트리거 금지. 금액 계산은 반드시 서버에서(클라 값 불신).
6. **SQL**: `#{}`만 사용(`${}` 금지 — 정렬 컬럼 등 불가피하면 whitelist 검증 후). 모든 조회에 `DEL_YN = 'N'` 조건.
7. **삭제 = soft delete**: `UPDATE ... SET DEL_YN='Y', MDFCN_DT=NOW(6), MDFR_SN=#{actor}`. 물리 DELETE 금지.
8. **감사컬럼 입력**: INSERT 시 `REG_DT=NOW(6), RGTR_SN=로그인사용자`, UPDATE 시 `MDFCN_DT, MDFR_SN`. (공통화 방안 검토 중 — 우선 매퍼에서 직접)
9. **명명**: DB `UPPER_SNAKE_CASE`(공공표준용어 — `docs/db-naming-standard.md` 약어표 확인), Java `camelCase`. MyBatis가 자동 매핑(`USER_SN`→`userSn`).

## 3. 컨트롤러 템플릿 (액션식 URL)

```java
@RestController
@RequestMapping("/samples")          // 복수형 리소스명 (실제론 도메인명)
@RequiredArgsConstructor
public class SampleApiController {

    private final SampleService sampleService;

    @PostMapping("/insert")
    public ResponseEntity<ApiResponse<SampleResponse>> insert(@Valid @RequestBody SampleCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(sampleService.insert(request)));
    }

    @GetMapping("/list")
    public ApiResponse<PageResponse<SampleResponse>> list(@Valid SampleSearchRequest request) {
        return ApiResponse.ok(sampleService.list(request));
    }

    @GetMapping("/detail")
    public ApiResponse<SampleResponse> detail(@RequestParam String sampleSn) { ... }

    @PostMapping("/update")  // body에 식별자 포함
    @PostMapping("/delete")
}
```
(※ 위 클래스명은 설명용 — 실제 코드에 sample/test 명칭 사용 금지)

## 4. DB 변경은 Flyway로만
→ [Flyway·DB 규칙 공유본] 필독. 요지: **적용된 V파일 수정 금지, 새 번호 파일 추가, 전 컬럼 COMMENT.**
- **(2026-06-12 확정) 적용은 수동 명령으로만** — `.\scripts\db-migrate-dev.ps1`(확인) → `-Apply`(적용). **앱 부팅은 DB를 바꾸지 않으며**, 미적용 V파일이 있으면 기동이 거부됩니다(적용을 먼저 하고 켜세요). 개발 DB는 전원이 공유하는 하나 — 적용 전 동료에게 공지 권장.

## 4-2. Redis 사용 규칙 (2026-06-11 구축 완료)

- **개발 Redis**: DB 서버에 구축됨 — 접속정보는 `.env.develop`의 `REDIS_HOST/PORT/USERNAME/PASSWORD` (로컬 설치 불필요, 환경변수만 넣으면 됨).
- 용도: refresh 토큰 세션(구현됨) / 예약 hold(V5 예정) / 공통코드 캐시(예정) / **로그인 실패 잠금(2026-06-15 구현 — `LoginAttemptGuard`)**. **진실원은 항상 MySQL — Redis가 날아가도 데이터 유실이 없어야 함.**
  - ⚠ 로그인 실패 잠금은 보안 통제라 **Redis 장애 시 fail-closed**(로그인 차단 `503 SERVICE_UNAVAILABLE`) — 이건 "캐시 없으면 통과"가 아닌 예외(세션 저장도 Redis라 어차피 로그인 완결 불가). 키: `auth:lgnfail:ai:{hash}`(계정+IP)·`auth:lgnfail:ip:{hash}`(IP), 값은 이메일/IP 원문 대신 SHA-256 해시.
- 규칙 (위반 시 리뷰 반려):
  1. `RedisTemplate` 직접 호출 금지 — 용도별 인터페이스(예: `TokenSessionStore`)를 만들고 구현체만 Redis 의존.
  2. **모든 키에 TTL 필수** (영구 키 금지). 키 패턴은 `{domain}:{purpose}:<id>` — 신규 키는 `docs/redis-strategy.md` §2 키 인벤토리에 등록.
  3. `KEYS` 명령 금지 — 패턴 조회는 `SCAN`.
  4. 값은 문자열/JSON만 (Java 직렬화 금지).
- 전체 전략(메모리 정책·장애 정책·확장 경로): 저장소 `docs/redis-strategy.md`.

## 4-3. 감사로그(AUDIT_LOG_INFO) 기록 규칙 (2026-06-11 구축 완료)

행 단위 감사컬럼(§2-8: REG_DT/RGTR_SN)과 별개로, **"누가 무엇을 했나"의 행위 이력**은 AUDIT_LOG_INFO 테이블에 기록합니다. 공통 모듈은 `egovframework.eeum.base.audit`.

- **기록 대상**: 생성/수정/삭제/승인 등 상태를 바꾸는 행위 + 개인정보 목록·상세 열람. 단순 본인 조회는 제외.
- **기본 방식 = `@Audit` 어노테이션** — ServiceImpl public 메서드에 부착하면 AOP가 자동 기록:
  ```java
  @Audit(actn = "PARTNER_APPROVE", trgtType = "PRTNR", trgtId = "#prtnrSn")
  public void approve(long prtnrSn, long actorUserSn) { ... }
  ```
  - `actn`: `대상_행위` 형식 영대문자 (예: `ACCOUNT_INSERT`).
  - `trgtId`: SpEL — 파라미터는 `#파라미터명`, 생성처럼 실행 후에야 ID가 생기면 `#result.userSn()`.
  - ⚠ AOP 한계: 같은 클래스 내부 호출(self-invocation)은 어노테이션이 안 탑니다 — 그 경우 명시 호출 사용.
- **목록 열람 등 검색조건을 남겨야 할 때 = `AuditRecorder` 명시 호출**: `auditRecorder.recordSuccess("PARTNER_LIST_VIEW {\"page\":1,...}", "PRTNR", null)`. 트랜잭션 밖에서 부르면 즉시 기록되므로 **조회가 성공한 뒤(반환 직전)에 호출**할 것.
- **자동으로 채워지는 것** (직접 넣지 말 것): 행위자(SecurityContext), IP/User-Agent, corrId, 결과(SUCCESS/FAIL).
- **트랜잭션 정합(이해만 하면 됨)**: SUCCESS는 업무 트랜잭션 **커밋 후** 기록(롤백되면 FAIL로 전환), 예외 발생 시 FAIL은 별도 트랜잭션으로 즉시 기록. 감사 기록 실패는 본 업무를 막지 않음(error 로그만).
- **corrId(상관관계 ID)**: 모든 요청에 UUID가 발급돼 응답 헤더 `X-Correlation-Id` + 요청 처리 동기 흐름의 로그 라인 `[cid:...]` + AUDIT_LOG_INFO.CORR_ID에 동일하게 찍힙니다 — 장애 추적 시 프론트가 헤더 값만 알려주면 백엔드 로그·감사행이 한 번에 검색됩니다. (⚠ 기동 로그, `@Async`/스케줄러/별도 executor 로그는 cid가 빈 값 — MDC는 요청 스레드에만 있음)
- ⚠ 로깅 백엔드는 **Log4j2 단일화** — 로그 패턴에서 Logback 식 기본값 문법(`%X{key:-}`)은 동작하지 않습니다(`%X{key}`만). 새 `spring-boot-starter-*` 의존성 추가 시 **`spring-boot-starter-logging` exclusion 필수**(Logback 재유입 차단 — pom의 web/redis 스타터 참고). `logging.pattern.*` 변경은 devtools 자동재시작에 반영되지 않으므로(로깅 시스템 프로퍼티가 JVM 최초 기동 시 세팅돼 동일 JVM에서 유지) **프로세스 완전 재기동**으로 확인할 것.

## 5. 검증 습관

- **검증(테스트) 클래스 명명 = `*Verification`** (test 단어 금지 — 팀 명명 규칙). ⚠ surefire가 `*Verification.java`만 실행하도록 설정돼 있어 `*Test`로 만들면 **실행되지 않습니다**.
- 아키텍처 규칙은 `ArchitectureVerification`(ArchUnit)이 자동 검사 — base→도메인 의존, 컨트롤러→DAO 호출, 도메인 간 DAO 접근이 생기면 `mvn test`에서 빌드 실패합니다.
- **IDE 인코딩(최초 1회)**: IntelliJ `Settings > Editor > File Encodings` — Global/Project `UTF-8`, **Default encoding for properties files = `UTF-8`**, Transparent native-to-ascii conversion 해제. (properties 기본이 ISO-8859-1이라 한글 주석이 깨져 보임 — 깨진 상태로 저장 금지!)
- 컴파일: `mvn compile` (JDK 17 확인!)
- 부팅 + Flyway 적용 확인 → Swagger에서 API 직접 호출
- 주요 작업은 codex 교차검토(팀 룰): `codex exec -c model_reasoning_effort=xhigh ...`

## 6. 배포 / CI·CD (2026-06-11 구축 완료)

- **develop push = 개발서버 자동 배포** ⚠ — push 하면 그대로 서버에 올라갑니다. 서버에 올리면 안 되는 진행 중 작업은 **별도 브랜치**에서 하다가 develop 에 머지하세요(별도 브랜치 push 는 CI 검증만 받음).
- 파이프라인: `ci.yml`(모든 push/PR — 빌드+검증 게이트) / `deploy.yml`(develop push — 서버 러너가 빌드·검증 통과 시에만 jar 교체→재시작→**`/actuator/health` 90초 체크→실패 시 자동 롤백**).
- 개발서버: `http://minoservers.iptime.org:8082` (Swagger `/swagger-ui/index.html`). 배포 상태는 GitHub **Actions 탭**에서 확인.
- 배포 실패 시: Actions 로그 확인 → 서버 `journalctl -u eeum-backend -n 100`, 앱 로그 `/opt/eeum/log/backend.log`.
- 서버 구성·1회 셋업·SELinux 함정 등 상세: 저장소 `deploy/README.md`.
