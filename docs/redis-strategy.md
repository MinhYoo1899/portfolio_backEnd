# Redis 운용 전략

> 확정일: 2026-06-11 (codex xhigh 검토 반영). 배치 결정: **개발 = DB 서버(우분투 24.04, MySQL과 동일 호스트) 단일 인스턴스(A안, 포트포워딩+ACL)** → 트래픽 증가 시 critical/cache 분리 확장.
> 배치 근거: 데이터 저장소 통합(관리 동선 일치) + 배포서버 재배포 시에도 세션 유지. 같은 내부망이라 지연 무시 가능.
> 운영(배포처) Redis는 클라우드 확정 시 결정(관리형 우선 검토) — `decisions-pending.md`.

---

## 1. 원칙

- **Redis = 보조 저장소(휘발 허용), 진실원 = MySQL.** 전손 시 영향: 전원 재로그인 + 진행중 hold 소실 + 캐시 콜드스타트. **데이터 유실은 없어야 한다.**
- **모든 키 TTL 필수** (영구 키 금지).
- **용도별 컴포넌트 추상화** — `RedisTemplate` 직접 호출 금지. 용도마다 인터페이스(`TokenSessionStore` 등)를 두고 구현체만 Redis 의존.
- `KEYS` 명령 금지(요청 경로) — 패턴 조회는 `SCAN`만.
- `SELECT` DB 미사용(단일 DB 0) — 클러스터 호환 대비.
- **maxmemory-policy = `noeviction`** (codex P0) — 가득 차면 쓰기 오류가 *드러나게* 한다. volatile-lru는 세션/hold를 조용히 날려 원인 파악 불가.
- 장애 정책: 세션/hold/레이트리밋 용도는 **fail-fast**(타임아웃 1~2초, 오류 envelope 응답). 캐시 용도는 **DB 폴백**.

## 2. 키 인벤토리 (단일 진실원 — 새 용도 추가 시 반드시 갱신)

> 표기: `<x>` = 치환 변수. 중괄호 `{}`는 Redis Cluster hash tag 의미가 있어 문서 표기에 사용 금지(codex P1).

| 키 패턴 | 용도 | 값 | TTL | 실패 정책 | 상태 |
|---|---|---|---|---|---|
| `auth:refresh:<userSn>:<jti>` | refresh 세션(회전) | `"1"` | 14일 | fail-fast | ✅ 구현 |
| `resv:hold:<roomSn>:<stayDate>` | 예약 임시점유 | `<holdId>` (소유자 식별 — "내 hold만 해제" Lua compare-delete) | 결제윈도우 | fail-fast | V5 예정 |
| `cache:comcd:<groupCd>` | 공통코드 캐시 | JSON | 10분 + jitter | DB 폴백 | 예정 |
| `auth:lgnfail:ai:<hash>` | 로그인 실패 — 계정+IP (`hash`=SHA-256(정규화 이메일+IP)). 임계 5회→차단 | 카운터 | 15분(고정 윈도우) | **fail-closed**(Redis 장애 시 로그인 `503` — 보안통제라 fail-open 아님, codex 변경) | ✅ 구현 (2026-06-15, `LoginAttemptGuard`) |
| `auth:lgnfail:ip:<hash>` | 로그인 실패 — IP 집계 (`hash`=SHA-256(IP)). 임계 50회→차단(분산 시도 방어) | 카운터 | 15분 | fail-closed | ✅ 구현 (2026-06-15) |
| `auth:mfa:<userSn>` | 운영자 MFA 챌린지 | JSON | 5분 | fail-fast | MFA 흐름 확정 후 |

- 값 직렬화: 문자열/JSON만(`StringRedisTemplate`). Java 직렬화 금지.
- 레이트리밋류는 INCR+EXPIRE 분리 금지 → Lua로 "증가+최초 TTL" 원자화(codex P1).

## 3. refresh 세션 설계 (구현 기준)

- 발급: 로그인/재발급 시 `SET auth:refresh:<userSn>:<jti> "1" EX 1209600`.
- 소비(회전): `DEL` 결과 **1일 때만 성공**. 0 = 만료/회수/재사용 의심 → 해당 사용자 **전체 세션 회수** 후 401(`AUTH_REFRESH_INVALID`).
- 전체 로그아웃/계정 정지: `SCAN auth:refresh:<userSn>:*` → DEL.
- 미결(account API 단계에서 결정): 비밀번호 변경 시 access 토큰 즉시 무효화 — JWT `tokenVersion` 클레임 + DB 비교 방식 검토(codex P0 권고).

## 4. 개발 서버 구성 (A안 — 사용자 결정 2026-06-11, 구축 완료)

- **DB 서버(우분투 24.04, 호스트 minobuntu24)에 apt 네이티브 설치** (redis-server 7.0.15, systemd 서비스 `redis-server`, conf `/etc/redis/redis.conf`). 공유기 포트포워딩 30007 (MySQL 30006과 동일 방식).
- 구축 검증: eeum 사용자 PONG / 익명 접속 NOAUTH 확인(2026-06-11). ufw 30007 개방.
- 공인망 노출 보완책: **ACL**(default 사용자 off, 앱 전용 `eeum` 사용자 + `-@dangerous`로 FLUSHALL/CONFIG/SHUTDOWN 등 차단) + 강한 비밀번호 + `noeviction` + `maxmemory 256mb`.
  - `+@scripting` 허용 사유: 예약 hold compare-delete·레이트리밋 원자화에 Lua(EVAL) 필요 예정(§2).
  - `+info` 예외 허용(2026-06-11): Spring Actuator 의 Redis 헬스체크(배포 자동 롤백 판정)가 `INFO` 를 사용하는데 INFO 는 `@dangerous` 소속 — 읽기 전용이라 위험 낮아 단건 허용. 정의 위치 = `/etc/redis/users.acl`(aclfile 방식, redis.conf 2279행에서 지정):
    `user eeum on >... ~* &* +@read +@write +@keyspace +@connection +@scripting -@dangerous +info`
  - **노출 상태 기록(2026-06-11)**: 인터넷 노출됨 — iptime 공유기 포트포워딩(외부 30007→DB서버 30007, 기존 MySQL 30006과 동일 계층/방식). codex는 P0로 터널/VPN 권고했으나 사용자 결정으로 A안 수용. 단서: **개발 Redis에는 운영 사용자 토큰/실데이터 없음**(유출 시 영향 = 개발 계정 세션 한정), 운영 전 private endpoint 전환 + 비밀번호 교체 필수(개발 DB 비번 교체 일정과 연동).
  - 단일장애점 인지: DB 서버 장애 시 MySQL과 세션이 동시 정지 — DB 없이는 어차피 서비스 불가라 수용(운영은 분리 검토).
- 영속화: AOF everysec (재시작 편의 — 안전장치 아님, 최대 1초 손실 허용).
- 접속 정보는 `.env.develop`(REDIS_HOST/PORT/USERNAME/PASSWORD) — 백엔드는 `spring.data.redis.*`로 주입, 타임아웃 connect 1s / command 2s.

## 5. 확장 경로

1. (지금) 단일 인스턴스 — 세션+hold+캐시 공존, noeviction.
2. (트래픽 증가) **critical Redis(noeviction: 세션/hold/MFA) + cache Redis(allkeys-lru)** 분리 — 캐시 폭주가 인증/예약 쓰기를 막는 것 방지(codex P1). 코드는 환경변수/커넥션 팩토리 분리만.
3. (운영) 관리형 + private endpoint + TLS. Sentinel/Cluster는 필요 시점에 결정(현 전략으로는 전손=재로그인 감수로 충분).

## 6. 모니터링 (지표명 확정 — 도입은 운영 단계)

`used_memory`/`maxmemory`, `evicted_keys`(0이어야 정상 — noeviction), rejected commands(OOM), `connected_clients`, `expired_keys`, AOF 상태.
