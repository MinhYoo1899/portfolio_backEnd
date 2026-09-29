# Flyway 실전 사용 가이드 (처음 쓰는 사람용)

> 이 프로젝트(전자정부 표준프레임워크 부트 5.0.0 = Spring Boot 3.5 + MySQL8 + Maven, 원격 개발서버 DB) 기준 실전 사용법.
> 한 줄 개념: **DB 구조 변경을 번호 붙인 SQL 파일로 만들고, 수동 명령으로 순서대로 적용한다 — 앱 부팅은 검사만.**
> 명령은 `mvnw`(래퍼 포함됨). 설정 파일은 **application.properties**(yml 아님).

---

## 1. 설치 (1회, 골격 만들 때 자동으로 들어감)

`pom.xml`에 의존성 2개 추가 (Spring Boot가 버전 관리):
```xml
<dependency>
  <groupId>org.flywaydb</groupId>
  <artifactId>flyway-core</artifactId>
</dependency>
<dependency>
  <groupId>org.flywaydb</groupId>
  <artifactId>flyway-mysql</artifactId>
</dependency>
```
> 이건 골격 만들 때 내가 넣어둔다. 직접 할 일 아님.

---

## 2. 파일 두는 곳 + 이름 규칙 (이것만 외우면 됨)

```
src/main/resources/db/migration/
  V1__core_account_rbac_code_log.sql
  V2__account_profile_terms_file.sql
  V3__house_room_product.sql
```

이름 규칙: **`V` + 번호 + `__`(언더스코어 2개) + 설명 + `.sql`**
- `V`는 대문자, 번호는 1,2,3… (또는 `V20260610_1` 같은 날짜형도 가능)
- `__` 는 반드시 언더스코어 **2개**
- 설명은 영어 소문자+언더스코어 권장

현재 프로젝트의 실제 V1:

```text
src/main/resources/db/migration/V1__core_account_rbac_code_log.sql
```

V1 적용 범위:

- 공통코드: `COMM_CD_GROUP`, `COMM_CD`
- 계정: `USER_ACNT`
- RBAC: `ROLE_MST`, `AUTH_MST`, `USER_ROLE`, `ROLE_AUTH`
- 로그: `AUDIT_LOG_INFO`, `LOGIN_LOG_INFO`

현재 V1 스키마 상세는 [db-schema-current.md](./db-schema-current.md)를 본다.

파일 안은 그냥 평범한 SQL:
```sql
-- V2__account_profile_terms_file.sql
CREATE TABLE USER_PROFILE (
    USER_PROFILE_SN BIGINT      NOT NULL AUTO_INCREMENT COMMENT '사용자프로필일련번호',
    USER_SN         BIGINT      NOT NULL COMMENT '사용자일련번호',
    REG_DT          DATETIME(6) NOT NULL COMMENT '등록일시',
    PRIMARY KEY (USER_PROFILE_SN)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

---

## 3. 어떻게 실행되나 — ★방식 B(수동 적용)로 확정 (2026-06-12)

> **확정 정책**: ① 개발자 전원이 **하나의 공유 개발 DB**를 바라본다(개인 로컬 DB 안 씀)
> ② **앱 구동 ≠ DB 변경** — 앱 부팅은 DB를 절대 바꾸지 않고, 적용은 수동 명령으로만 한다.
> (이유: 공유 DB 환경에서 "아무나 앱 켤 때 DB가 바뀌는" 것은 위험)

### 부팅 시: 검사만 (validate)
앱을 켜면 Flyway가 **검사만** 한다 — `FlywayValidateOnlyConfig`(egovframework/com/config)가 적용 동작을 검사로 바꿔놓음.
- 파일과 DB가 일치하면 → 정상 기동
- **미적용 파일이 있거나 체크섬이 안 맞으면 → 기동 거부**(fail fast). 코드와 DB가 어긋난 채 서비스되는 사고 방지.
- 기동 거부되면: 아래 수동 적용을 먼저 실행한 뒤 다시 켠다.

### 적용은: 수동 명령으로만
```powershell
.\scripts\db-migrate-dev.ps1          # 적용 "예정분 확인"만 (DB 변경 없음 — 먼저 이걸로 확인)
.\scripts\db-migrate-dev.ps1 -Apply   # 실제 적용
```
스크립트는 `.env.develop`의 DB 접속정보를 읽어 `mvnw -Pmigrate-dev flyway:info|migrate`를 실행한다
(Maven 프로파일 `migrate-dev`는 pom.xml에 정의 — 개발 시드 포함이므로 **운영 DB에 사용 금지**).

알아둘 동작 2가지:
- **R__(개발 시드) 파일을 고친 경우에도** 수동 적용을 먼저 해야 부팅된다 (내용 변경 = 미적용분으로 간주).
- **DB가 코드보다 앞선 경우**(구버전 앱 + 신버전 DB)는 기동 허용 — 확정 정책(2026-06-12). 배포 실패로
  롤백된 구버전이 정상 기동하기 위함이며, 코드가 쓰는 것은 DB에 전부 있으므로 안전.

현재 설정(`src/main/resources/application.properties`):
```properties
spring.flyway.enabled=true            # 켜져 있지만 동작은 validate만(위 Config가 강제)
spring.flyway.baseline-on-migrate=true
spring.flyway.locations=classpath:db/migration
spring.flyway.clean-disabled=true
```

> (참고 — 방식 A: 부팅 시 자동 적용) 과거 사용하던 방식. `FlywayValidateOnlyConfig`를 제거하면 돌아가지만,
> 공유 DB 정책상 **사용하지 않기로 확정**됨.

> 원격 개발서버 DB여도 상관없음 — 접속 JDBC 주소(환경변수 `DB_HOST` 등)로 연결된다.

---

## 4. 평소 작업 흐름 (이게 핵심)

**테이블/컬럼을 바꾸고 싶을 때:**
1. `db/migration/`에 **다음 번호 새 파일**을 만든다. (기존 파일 절대 수정 X)
   ```sql
   -- V10__add_memo_to_user_acnt.sql  (예시 — 다음 번호. 현재 V9까지 사용 중)
   ALTER TABLE USER_ACNT ADD COLUMN ETC_MEMO VARCHAR(200);
   ```
2. `.\scripts\db-migrate-dev.ps1`로 적용 예정분 확인 → `-Apply`로 적용.
3. 끝. 공유 개발 DB 하나가 바뀌므로 전원에게 즉시 반영됨. (적용 전에 새 코드를 켜면 기동 거부됨 — 적용이 먼저)

**그게 전부입니다.** 평소엔 "새 SQL 파일 추가"만 하면 됩니다.

---

## 5. 절대 규칙 (딱 2개)

1. **이미 적용된 파일은 절대 수정하지 않는다.** 바꾸려면 새 번호 파일을 추가.
   - (Flyway가 파일 내용의 체크섬을 기억함. 고치면 "체크섬 안 맞음" 하고 앱이 안 켜짐 — 안전장치.)
2. **번호는 겹치지 않게, 순서대로.** 협업 시 같은 번호 충돌 주의(머지 시 번호 조정).

---

## 6. 자주 쓰는 명령 치트시트

```powershell
.\scripts\db-migrate-dev.ps1           # 적용 현황·예정분 확인 (flyway:info — DB 변경 없음)
.\scripts\db-migrate-dev.ps1 -Apply    # 미적용분 실행 (flyway:migrate)
.\mvnw -Pmigrate-dev flyway:validate   # 파일 변조 검증 (※ DB_* 환경변수 필요)
.\mvnw -Pmigrate-dev flyway:repair     # 체크섬/실패기록 복구 (문제 생겼을 때)
```
적용 확인은 위 info 결과 또는 DB에서 직접: `SELECT * FROM flyway_schema_history;` → 누가 언제 무슨 버전 적용했는지 기록.

---

## 7. 자주 묻는 것

- **운영 DB에서 잘못된 SQL 넣으면?** Flyway는 "자동 실행"만 도와줌. 위험한 SQL(컬럼 삭제 등)은 사람이 신중히.
  운영 적용 전 **개발/스테이징에서 먼저 검증**하는 습관이 안전장치. 실패하면 그 버전은 미적용으로 남고, 고쳐서 재시도.
- **롤백은?** 무료 버전엔 자동 롤백(undo)이 없음. 대신 **되돌리는 새 마이그레이션(V5: 컬럼 다시 삭제)** 을 추가하는 방식.
  (그래서 운영 전 검증이 중요. 실무에선 이걸로 충분.)
- **기존에 데이터/테이블이 이미 있는 DB에 처음 붙이려면?** `baseline-on-migrate: true` 로 현재 상태를 기준점(baseline)으로 잡고 그 다음 버전부터 적용.

---

## 8. 우리 프로젝트에서 내가 해줄 것 / 사용자가 할 것

- **내가(골격 작업 시)**: pom 의존성+`migrate-dev` 프로파일, application.properties/-dev.properties Flyway 설정(부팅=validate만 — `FlywayValidateOnlyConfig`), 수동 적용 스크립트(`scripts/db-migrate-dev.ps1`), `V1__...` 초기 스키마를 다 만들어 둠.
- **사용자가(평소)**: DB 바꿀 일 생기면 `db/migration/`에 다음 번호 SQL 파일 하나 추가 → `.\scripts\db-migrate-dev.ps1`(확인) → `-Apply`(적용). 그게 전부.

> 정리: Flyway는 "DB 변경 = 번호 SQL 파일 추가"라는 습관 하나만 들이면 됩니다. 첫 설정은 내가 다 해두고,
> 사용자는 새 파일 추가만 하면 되니 실제 부담은 거의 없습니다.

---

## 9. ERD/스키마를 나중에 수정해야 할 때 (중요)

ERD는 한 번에 완벽할 필요 없다. 개발하며 계속 바뀌는 게 정상. 원칙: **기존 파일 수정 X, 새 번호 파일 추가.**

### 수정 종류별
| 종류 | 예 | 난이도 | 처리 |
|---|---|---|---|
| 컬럼/테이블 추가 | 예약에 요청사항 추가 | 🟢 | `ALTER TABLE ... ADD COLUMN ...` (데이터 영향 없음) |
| 타입/길이 변경 | 전화번호 20→30 | 🟡 | `ALTER TABLE ... MODIFY ...` (기존 데이터 호환 확인) |
| 컬럼 삭제/리네임 | 미사용 컬럼 제거 | 🔴 | expand-contract |
| 테이블 분리/재구조 | 1테이블→2 | 🔴 | 새 테이블 → 데이터 복사 → 구 제거 (단계 분할) |

### 위험 변경 = expand-contract (무손실)
```
V10: 새 구조 추가(확장)
V11: 데이터 복사 + 코드가 새 쪽 사용하도록 배포
V12: 안정 확인 후 구 구조 제거
```

### ★ 개발 초기 vs 데이터 쌓인 뒤
- 우리 개발 DB는 **전원이 공유하는 하나**이므로, 초기여도 `clean`(전체 삭제)·적용된 V파일 직접 수정은 **하지 않는다**
  (`clean-disabled=true`로 막혀 있음). 변경은 **항상 새 번호 V파일**로.
- 데이터 보존이 필요한 위험 변경(컬럼 개명·삭제 등)은 위의 expand-contract(추가→전환→제거 단계 분할)로.

### 실수 시
- 개발 중: 되돌리는 보정 마이그레이션(새 V) 추가가 기본. 체크섬 꼬임은 `mvnw -Pmigrate-dev flyway:repair`.
- 운영: 되돌리는 보정 마이그레이션(새 V) 추가. (운영 전 개발/스테이징 검증이 안전장치)
