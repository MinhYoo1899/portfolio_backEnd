# 베이스 골격 & 모듈 아키텍처

> **이 프로젝트의 공통 구조는 다른 프로젝트들이 그 위에 얹어 시작하는 "베이스 골격(base skeleton)"** 이다.
> 또한 기능(특히 **로그인 → 통합 로그인/SSO**)이 나중에 별도 서비스로 분리될 수 있어야 한다.
> → "모듈러 모놀리식으로 시작, 분리·재사용 가능하게" 가 원칙. (codex 검토 반영)
> 표준 규약은 [architecture.md](./architecture.md), 도메인은 [domain-model.md](./domain-model.md).

---

## 1. 모듈러 모놀리식 구성 (D15 — eGov 부트 단일 모듈 + 패키지 경계)

> **결정(D15)**: 전자정부 표준프레임워크 부트는 **단일 모듈(jar)** 구조다. 감리 강제도 없어, 처음부터 물리 멀티모듈로
> 쪼개지 않는다. 대신 **단일 모듈 안에서 패키지 경계(base/auth/domain)를 명확히 + ArchUnit으로 강제**하고,
> 실제로 분리가 필요해질 때(2nd 프로젝트가 base를 재사용) **물리 모듈로 떼어낸다.** ("과설계 금지, 단계적 진화")

```
eeum (단일 eGov 부트 모듈, egovframework.eeum)
 ├─ base/        # 도메인 비의존 공통 골격 (다른 프로젝트가 가져갈 토대)
 │              응답 envelope·예외·ErrorCode·페이징·Security/JWT 추상·RBAC 프레임·감사·Redis추상·external port·로깅/MDC·설정·테스트베이스
 ├─ auth/        # 인증/인가 (나중에 SSO로 분리 후보 → public contract만 외부 노출)
 └─ domain/      # 고택/객실/예약/결제/정산 등 업무 (house/room/reservation/payment/settlement/...)
```

**의존 방향 (패키지 레벨, ArchUnit 강제, 역방향 금지):**
```
domain  ─→  auth(의 public contract)  ─→  base
domain  ─→  base
base    ─→  (도메인 의존 금지)
도메인 A ─→  도메인 B 의 mapper/엔티티 직접 접근 금지 (service 인터페이스/이벤트 경유)
```
- `base`는 어떤 도메인도 참조하지 않는다. 업무 코드(상태/정책)는 `base`에 넣지 않는다(오염 금지).
- **MyBatis 매퍼는 각 기능 패키지가 소유**(`base`엔 매퍼 없음 — 공통 응답·예외·보안타입·Redis추상·유틸만).
- `domain`은 `auth`의 **public contract(인터페이스/DTO)만** 의존(내부 구현 의존 금지) → SSO 분리 비용 방지.
- ArchUnit 예:
  ```java
  @ArchTest static final ArchRule base_no_domain =
      noClasses().that().resideInAPackage("..base..")
        .should().dependOnClassesThat().resideInAnyPackage("..auth..","..domain..");
  ```

> **단계적 진화**: 1차 = 단일 모듈 + 패키지 경계 + ArchUnit. → base 안정화 → (실제 재사용 필요 시) `base`를 물리 모듈/라이브러리로 분리 → (더 나중) auth를 SSO 서비스로 분리.
> 패키지 경계가 깨끗하면 물리 분리는 비용이 작다. **지금 물리 분리하지 않는다.**

> 📌 **표기 안내**: 이하 문서(2~6장)의 `eeum-base`/`eeum-auth`/`eeum-app`은 현재 **단일 모듈 내 패키지**(`egovframework.eeum.base`/`auth`/...)를 의미한다. 물리 분리 시 그대로 모듈명이 된다. Sprint 1 파일 레이아웃도 단일 모듈 기준으로 읽을 것.

---

## 2. base vs 도메인 경계

**`eeum-base`에 둘 것** — "어느 프로젝트에서도 의미가 같은 기술/플랫폼 계약":
응답 envelope · 공통 예외 · ErrorCode 인터페이스 · 페이징 요청/응답 · Security/JWT 추상 계약 ·
RBAC 권한체크 프레임 · 감사 컨텍스트 · Redis 추상화 · external adapter/port 패턴 · 공통 로깅/MDC · 공통 설정 · 테스트 베이스.

**도메인(`eeum-domain-app`)에 남길 것** — "이음 서비스의 업무 의미":
회원 등급 · 파트너 정책 · 고택/객실/예약/결제/정산 상태 · 예약 가용성 계산 · 취소수수료 · 정산주기 · 클라이언트별 화면권한.

> ⚠ **base 오염 금지**: `RESERVATION_STATUS`, `ROOM_TYPE`, `PARTNER_STATUS` 같은 **업무 코드는 base의 공통코드에 넣지 않는다**. 도메인 enum/코드테이블로 분리. base의 공통코드는 "코드 체계 프레임"만 제공.

**안정 계약(stable contract)** — 다른 프로젝트가 의존하므로 함부로 바꾸지 않는다:
`ApiResponse<T>`(=D5 envelope `{resultCode,resultMessage,result}`) · `ErrorCode` · `BusinessException`(=`ApiException`) ·
`PageRequest`/`PageResponse` · `CurrentUser`/`AuthPrincipal` · `PermissionCode` · `TokenVerifier` ·
`CacheClient`/`TokenSessionStore` 추상 · external adapter 예외/응답 규약 · `AuditContext`.

---

## 3. 인증을 SSO로 분리 가능하게 (eeum-auth)

핵심: **"토큰 발급"과 "토큰 검증/인가"를 분리**한다. 지금은 한 모듈이 둘 다 하지만, 검증 측은 처음부터 **리소스 서버처럼** 동작.

```
지금:   eeum-auth 가 login·refresh·token 발급 + 검증
나중:   별도 sso-service 가 발급, Eeum_back 은 JWT 검증 + 권한체크만 (코드 거의 그대로)
```

**JWT 클레임을 OIDC 호환에 가깝게 고정** (전환 시 흔들림 최소):
```json
{ "iss":"https://auth.eeum.com", "sub":"<account_id>",
  "aud":["eeum-user","eeum-partner","eeum-OPERATOR"], "client_type":"USER_APP|PARTNER_WEB|OPERATOR_WEB",
  "roles":["..."], "permissions":["..."], "scope":"reservation:read ...", "iat":..., "exp":... }
```

설계 규칙:
- **인증 주체(account_id)와 도메인 PK를 동일시하지 않는다.** auth 계정(account_id/login_id/password_hash/provider/credential status)과 도메인 프로필(user_profile/partner/OPERATOR)을 분리.
- 애플리케이션 내부는 토큰을 직접 파싱하지 않고 **`CurrentUser`만 신뢰**:
  ```java
  public record CurrentUser(String accountId, String clientType, Set<String> roles, Set<String> permissions) {}
  public interface TokenVerifier { CurrentUser verify(String accessToken); }
  public interface PermissionChecker { boolean hasPermission(CurrentUser user, String permissionCode); }
  ```
- refresh/세션은 `eeum-auth`가 관리하되 **인터페이스는 base**:
  ```java
  public interface TokenSessionStore {
      void save(String accountId, String refreshTokenId, Duration ttl);
      boolean exists(String accountId, String refreshTokenId);
      void revoke(String accountId, String refreshTokenId);
  }
  ```
- **SSO 전환 절차**: ① 클레임 OIDC화 고정 → ② 검증을 `TokenVerifier` 뒤로 → ③ 내부 `InternalJwtIssuer` 사용 → ④ SSO 도입 시 Issuer/JWK 검증으로 교체 → ⑤ account_id↔sub 매핑 유지 → ⑥ 일정기간 dual validation.

---

## 4. 모듈 결합 낮추기 (분리 대비)

- 다른 모듈의 **Mapper 직접 호출 금지**, **Entity/VO 직접 반환 금지**.
- 모듈 간은 **Service 인터페이스/Facade 경유**, 공유 식별자는 **ID 값만** 전달. 예:
  ```java
  public interface AccountReader { AccountSummary getAccount(String accountId); }
  public record AccountSummary(String accountId, String displayName, String status) {}
  ```
- 트랜잭션 경계는 **호출하는 유스케이스 서비스가 소유**.
- **후속 부가처리는 도메인 이벤트**(ApplicationEvent), 반드시 성공해야 하는 동기 처리는 service/facade 호출:
  ```java
  applicationEventPublisher.publishEvent(new ReservationCreatedEvent(reservationId, accountId));
  ```
  (이벤트 남발로 핵심 흐름을 숨기지 말 것.)

**분리 우선순위**: 1)auth/SSO → 2)payment → 3)notification → 4)settlement → (후순위) house/room/reservation core.
예약 core는 트랜잭션·조회 조합이 복잡해 너무 일찍 분리하지 않는다.

---

## 5. base 골격 필수 산출물 (다른 프로젝트가 바로 얹으려면)

| 영역 | 산출물 |
| --- | --- |
| 응답 | `ApiResponse<T>`(D5 envelope), `PageResponse<T>`, `ResultCode` |
| 예외 | `ErrorCode`, `BusinessException`(=ApiException), `GlobalExceptionHandler`, validation 오류 포맷(fieldErrors) |
| 보안 | `CurrentUser`, `AuthPrincipal`, `JwtProperties`, `TokenVerifier`, `PermissionEvaluator`, SecurityFilterChain base |
| RBAC | `PermissionCode`, `RoleCode`, `@RequirePermission`, 권한조회 Port |
| 코드 | `BaseCode` 인터페이스, `CodeResponse`, CodeProvider 규약 |
| 페이징 | `PageRequest`, `SortRequest`(정렬 whitelist), MyBatis paging helper |
| Redis | `CacheClient`, `TokenSessionStore`, `DistributedLock` 추상(구현 선택) |
| 감사 | `AuditContext`, CreatedBy/UpdatedBy 처리, requestId·accountId MDC |
| 로깅 | request logging filter, MDC 키 표준, 외부호출 로깅 규약 |
| 외부연동 | `ExternalClientException`, `ExternalResponse`, adapter/port 패키지 규칙 |
| 설정 | `BaseProperties`, `JwtProperties`, `RedisProperties`, `CorsProperties` |
| 테스트 | Controller/Mapper 테스트 베이스, Security 테스트 헬퍼, fixture 규약 |
| 문서 | 패키지 규약, 모듈 의존 규칙, 응답/에러코드 규약 |

---

## 6. Sprint 1 (base 골격) 구체 산출물

```
eeum-base/src/main/java/com/eeum/base
 ├─ response/ApiResponse.java        # D5 envelope {resultCode,resultMessage,result}
 ├─ response/PageResponse.java
 ├─ exception/ErrorCode.java  exception/BaseErrorCode.java
 ├─ exception/BusinessException.java  exception/GlobalExceptionHandler.java
 ├─ security/CurrentUser.java  security/TokenVerifier.java
 ├─ security/PermissionChecker.java  security/RequirePermission.java
 ├─ redis/TokenSessionStore.java  redis/CacheClient.java
 ├─ audit/AuditContext.java
 ├─ logging/MdcKeys.java
 └─ external/ExternalAdapterException.java

eeum-auth/src/main/java/com/eeum/auth
 ├─ application/AuthService.java  application/AuthServiceImpl.java  application/TokenIssuer.java
 ├─ infrastructure/jwt/InternalJwtTokenIssuer.java  infrastructure/jwt/JwtTokenVerifier.java
 ├─ infrastructure/redis/RedisTokenSessionStore.java
 └─ presentation/AuthController.java

eeum-app/src/main/java/com/eeum
 ├─ EeumApplication.java
 └─ config/SecurityConfig.java
```

**지금 하지 말 것 (YAGNI):** auto-configuration starter 완성 · Maven artifact 외부배포 · Keycloak/OIDC 서버 구축 ·
MSA 물리분리 · Kafka 등 대형 이벤트 인프라 · 공통코드 관리화면 선구현 · 모든 도메인 Port 선설계 · 멀티테넌시 범용화 · 도메인별 DB 분리.

> 1차 목표: **멀티모듈 + base 안정 계약 + auth 분리 가능 인터페이스**까지. 라이브러리화·SSO 분리는 2번째 프로젝트가 실제 재사용할 시점에.
