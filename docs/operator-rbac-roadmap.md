# 운영자 권한 체계 — 계정관리·확장경로·검증환경 참고자료

> 작성 2026-06-12 ｜ 대상: 소유자(의사결정용 참고자료) ｜ 운영자 다단계 권한(원 기획 범위)의 후속 전략 메모.
> 1차 구현(V8 — STAFF/SUPERVISOR 분화)의 다음 단계들을 미리 그려둔 것. 확정 시 본 문서를 갱신하고 구현에 반영한다.

---

## A. 운영자 계정 관리 (수명주기)

### A-1. 현재 상태
- 운영자 계정 **생성 API 없음** — 개발 시드(R__) 또는 수동 INSERT만 가능.
- 일반회원/파트너는 가입 API가 있지만, 운영자는 "직원 채용"이라 **Supervisor가 만들어 주는 모델**이 맞음.

### A-2. 2단계(후속 작업)에서 만들 것 — 운영자 계정관리 API
| API (안) | 하는 일 | 비고 |
|---|---|---|
| `POST /operators/insert` | 직원 계정 생성 (이메일+이름+초기 역할) | 임시 비밀번호 발급 → 첫 로그인 시 변경 강제(비밀번호 정책과 함께 설계) |
| `GET /operators/list` / `detail` | 직원 목록/상세 | |
| `POST /operators/suspend` / `restore` | 정지/복구 | 정지 즉시 **전체 세션 회수** |
| `POST /operators/grant-role` / `revoke-role` | 역할 부여/회수 | 변경 즉시 세션 회수(재로그인 유도) |
| `POST /operators/delete` | 퇴사 처리 (soft delete) | 세션 회수 + 감사 보존 |

**보호 권한**: 전부 `OPERATOR_MNG` (신설). `ACNT_MNG`(회원·파트너 계정용)와 **분리** —
`ACNT_MNG`로는 운영자 계정·운영자 역할을 절대 못 만지게 API 레벨에서 차단 (codex 필수).

**필수 가드 세트** (한 번에 구현):
1. **자기 자신** 강등·정지·삭제 금지
2. **마지막 Supervisor 보호** — 시스템에 Supervisor가 0명이 되는 조작 금지
3. Supervisor 부여 시 **STAFF 자동 동반 부여**(한 트랜잭션), 회수도 동반 처리 — "SPV인데 STAFF 없음" 상태 원천 차단
4. 모든 행위 **감사 기록**(@Audit — `OPERATOR_INSERT`, `OPERATOR_ROLE_GRANT` 등)
5. 역할·상태 변경 시 대상자 `revokeAll` (남는 노출 = access 최대 30분 — 수용 위험으로 기록됨)

### A-3. 미결정 (확정 필요)
- **`OPERATOR_MNG`를 누구에게**: 모든 Supervisor에게 기본 부여 vs 별도 최상위(예: `OPERATOR_ADMIN`) 분리.
  판단 기준 = "Supervisor가 조직 관리자냐(부여), 업무 승인자냐(분리)". → 큐어 조직 구조 확인 후 결정 권장.
- 임시 비밀번호 전달 방식(이메일 발송 vs 화면 1회 표시) — 알림 인프라 시점과 연동.

---

## B. 확장 경로 (역할 체계가 커질 때)

### B-1. 확장은 "데이터 추가"로만 — 코드 무변경이 정상
현 설계(완전 데이터 주도 RBAC)에서 아래 전부가 **ROLE_MST 행 추가 + ROLE_AUTH 매핑**만으로 수용됨:

| 시나리오 | 방법 | 예 |
|---|---|---|
| 3단계 추가 | 역할 1행 추가 | `OPERATOR_ADMIN`(조직·계정 관리 전담) — STAFF·SPV 동시 부여 + OPERATOR_MNG |
| 부서/직능별 담당 | 직능 역할 추가 | `OPERATOR_SETTLE`(정산 담당 = STLM_VIEW+STLM_CNFM), `OPERATOR_CS`(문의 답변) |
| 특정 직원에게만 추가 권한 | USER_ROLE 1행 | 역할 조합으로 해결 (권한 직접 부여는 안 함 — 역할 경유 원칙) |

### B-2. 권한 폭증 방지 원칙 (codex 합의)
- **"사람·리스크·메뉴 노출이 달라질 때만 권한을 분리"** — API마다 권한 만들기 금지.
- 도메인당 기본 2개: `{대상}_VIEW`(조회 — staff 기본) + 굵은 처리 1개(`{대상}_MNG` 또는 `{대상}_APRV`).
  처리 안에서 리스크 차이가 실제로 생길 때만 추가 분리(예: 정산 확정 vs 정산 조회).

### B-3. 역할 계층(상속) 전환 기준
- 지금: `UPPER_ROLE_SN`/`ROLE_STEP`은 **메타데이터**(권한 조회가 상속을 안 탐) — 상속 효과는 "역할 동시 부여"로 대신.
- **전환 신호**: 역할이 ~10개를 넘거나, "동시 부여 세트" 관리가 자주 틀리기 시작할 때 → 권한 조회 쿼리를 재귀(CTE)로 확장해 진짜 상속 도입. 그 전까지는 단순함 유지가 이득.

---

## C. 검증 환경 (다단계 권한을 눈으로 확인하는 방법)

### C-1. 개발 시드 계정 체계 (V8 이후)
| 계정 | 역할 | 검증 용도 |
|---|---|---|
| `master@eeum.dev` | STAFF+SUPERVISOR+SUPER | 전권 운영자 + 3채널 로그인 (기존 계정 보전) |
| `operator-staff@eeum.dev` (V8 신설) | STAFF만 | **일반직원 시나리오** — 조회 되고 승인 안 되는지 |
| `chnlmember@eeum.dev` / `chnlpartner@eeum.dev` | 회원/파트너 | 채널 제한·일반 사용자 동작 (기존) |

### C-2. 검증 매트릭스 (V8 완료 판정 기준)
| 시나리오 | staff 계정 | supervisor 계정 |
|---|---|---|
| 운영자웹 로그인(chnlCd=OPERATOR) | 200 | 200 |
| 파트너 목록/상세 조회 | **200** (PRTNR_VIEW) | 200 |
| 파트너 승인/반려 | **403** | 200 (PRTNR_APRV) |
| 운영자 메뉴(파트너 관리) 노출 | **보임** (메뉴=VIEW 기준) | 보임 |
| 프론트 버튼 분기 | permissions에 APRV 없음 → 버튼 숨김 | APRV 있음 → 버튼 표시 |
| 역할 회수 후 재발급(refresh) | 회수된 권한으로 거부 | 〃 |
| 배포 체크: "SPV 보유자 중 STAFF 누락" 검증 쿼리 | **0건이어야 함** | |

### C-3. 검증 방법
- 1차: Swagger에서 두 계정으로 로그인 토큰 각각 받아 위 매트릭스 수동 확인 (기존 방식).
- 백로그: 권한 매트릭스 자동 검증(통합 테스트 — DB 필요해서 현 검증 체계(Verification, DB 무관)와 별도 설계 필요. 감사 회귀 검증 백로그와 함께 검토).

### C-4. 배포 체크 쿼리 — "SUPERVISOR인데 STAFF 누락" 0건 확인 (codex 제공)
`-Apply` 이후 또는 운영자 역할을 수동으로 만진 뒤 실행 — **결과가 0행이어야 정상** (`*Verification`에는 넣지 않음: DB 필요해서 무DB 검증 체계를 깨뜨림):
```sql
SELECT U.USER_SN, U.EML_ADDR
  FROM USER_ROLE SPUR
  JOIN ROLE_MST SP ON SP.ROLE_SN = SPUR.ROLE_SN
                  AND SP.ROLE_CD = 'OPERATOR_SUPERVISOR' AND SP.DEL_YN = 'N'
  JOIN USER_ACNT U ON U.USER_SN = SPUR.USER_SN
 WHERE SPUR.DEL_YN = 'N'
   AND NOT EXISTS (
       SELECT 1 FROM USER_ROLE STUR
         JOIN ROLE_MST ST ON ST.ROLE_SN = STUR.ROLE_SN
                          AND ST.ROLE_CD = 'OPERATOR_STAFF' AND ST.DEL_YN = 'N'
        WHERE STUR.USER_SN = SPUR.USER_SN AND STUR.DEL_YN = 'N');
```

---

## 참고 — 관련 확정 문서
- 권한 체계 원칙: `docs/conventions.md` §0·`docs/db-naming-standard.md` 팀 확정 규칙
- 1차 구현(V8) 범위·세션 회수·메뉴 연동: 이 문서 A·C + codex 검토 결과(2026-06-12, 필수 6·권고 3)
