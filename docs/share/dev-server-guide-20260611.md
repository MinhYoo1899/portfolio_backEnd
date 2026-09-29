# 🖥 개발서버 이용 안내 (백엔드 — 2026-06-11 배포 시작)

> **대상**: 프론트(앱/파트너웹/운영자웹) + 백엔드 개발자 전원
> 백엔드를 로컬에 설치하지 않아도, 이 서버로 바로 API 연동 개발을 시작할 수 있습니다.

## 1. 주소

| 용도 | URL |
| --- | --- |
| **API Base URL** | `http://minoservers.iptime.org:8082` |
| **Swagger (API 문서·직접 호출)** | `http://minoservers.iptime.org:8082/swagger-ui/index.html` |
| 헬스체크 | `http://minoservers.iptime.org:8082/actuator/health` → `{"status":"UP"}` |

## 2. 테스트 계정 (개발서버 전용 시드)

| 계정 | 용도 |
| --- | --- |
| `master@eeum.dev` / `EeumMaster1!` | 운영자(OPERATOR) — 파트너 승인·계정 관리 권한 + **슈퍼(3채널 로그인 가능)** |

- 일반회원/파트너 계정은 가입 API(`/accounts/insert`, `/partners/insert`)로 직접 만들어 쓰면 됩니다.
- ⚠ 로그인·재발급에 **`chnlCd`(USER/PARTNER/OPERATOR) 필수** (2026-06-12부터) — 자기 SPA 채널값을 보내세요. 일반 계정은 자기 채널만 로그인됩니다(인증 가이드 §2).
- ⚠ 개발서버 DB에는 **실데이터·운영정보를 절대 넣지 마세요** (개발용 — 주기적으로 초기화될 수 있음).

## 3. 배포 방식 (자동)

- **develop 브랜치에 push되면 자동으로 빌드·검증 후 재배포**됩니다 (GitHub Actions CI/CD).
- 배포 중 수십 초 정도 API가 잠시 끊길 수 있습니다. 배포 상태는 GitHub **Actions 탭**에서 확인.
- 검증 실패 시 배포되지 않고, 기동 실패 시 이전 버전으로 **자동 롤백**됩니다 — "서버가 갑자기 옛날 코드"라면 Actions 탭에서 빨간 실패 기록을 확인하세요.
- 배포 성공 판정은 **DB·Redis·디스크가 모두 정상**일 때만(`/actuator/health/deploy`, 2026-06-22~) — 의존성 장애면 배포가 성공 처리되지 않습니다. (이용자용 헬스 `/actuator/health` 는 그대로)

## 4. 프론트 개발 시 할 일

1. **CORS origin 등록 요청**: 각 SPA의 dev 서버 origin(예: `http://localhost:3000`)을 백엔드 담당에게 전달 — 등록돼야 브라우저에서 호출됩니다. (현재 등록: `http://localhost:3000`)
2. API 규약은 [API 연동 규약] 문서 필독 (envelope, resultCode, 인증 헤더 등).
3. 인증 흐름(로그인/refresh/쿠키)은 [인증·권한 흐름 가이드] 참고.

## 5. 오류 신고 방법

- 모든 응답에 `X-Correlation-Id` 헤더(추적 ID)가 내려갑니다.
- API 오류·이상 동작 신고 시 **① 호출 URL ② 요청 본문 ③ X-Correlation-Id 값**을 함께 전달해 주세요 — 이 ID 하나로 서버 로그와 감사 기록이 한 번에 검색됩니다.

## 6. 서버 구성 (참고 — 백엔드 담당용)

- 배포서버(록키리눅스 9.5)의 `eeum-backend` systemd 서비스, 포트 8082 (기존 testBoot 스택 80/8080/8081과 분리).
- DB(MySQL `eeum` 스키마)·Redis는 별도 DB서버 — 접속정보는 `.env.develop`/서버 `/etc/eeum/eeum.env`.
- 서버 1회 셋업·운영 상세: 저장소 `deploy/README.md`.
