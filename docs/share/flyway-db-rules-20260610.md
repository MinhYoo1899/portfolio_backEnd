# 🗄 Flyway·DB 운영 규칙 (팀 공유본)

> **대상**: 백엔드 전원 ｜ 상세 가이드: 저장소 `docs/flyway-guide.md`, 명명: `docs/db-naming-standard.md`
> 한 줄: **DB 변경 = `db/migration/`에 다음 번호 SQL 파일 추가. 그게 전부.**

---

## 1. 동작 방식 (현재)
- 앱 **부팅 시 Flyway 자동 적용** (`spring.flyway.enabled=true`). 별도 명령 없음.
- 개발서버 MySQL `eeum` 스키마. 적용 이력: `flyway_schema_history` 테이블.
- 현재 적용: **V1**(공통코드/계정/RBAC/감사·로그 9테이블).

## 2. 절대 규칙 🔴
1. **이미 적용된 V파일은 절대 수정 금지** — 체크섬 불일치로 전원 부팅 실패함. 변경은 **새 번호 파일**로.
2. **번호 충돌 주의** — 브랜치 머지 시 같은 V번호 겹치면 안 됨. 작업 전 팀에 "다음 번호 쓸게요" 공유.
3. **`flyway clean` 금지** (`clean-disabled=true` 설정됨) — 공유 DB 전체 드롭 방지.
4. **물리 DELETE/DROP 신중** — soft delete(DEL_YN) 원칙. 위험 DDL(컬럼삭제/리네임/타입변경)은 expand-contract 분할 + 팀 합의.

## 3. 새 마이그레이션 작성법
```
src/main/resources/db/migration/V{다음번호}__{영문_설명}.sql
예: V2__account_profile_terms_file.sql
```
**DDL 필수 체크리스트:**
- [ ] 테이블/컬럼 공공표준용어 명명 (`docs/db-naming-standard.md` 약어표)
- [ ] **모든 컬럼 COMMENT(논리명)**
- [ ] 감사컬럼 5종: `REG_DT, RGTR_SN, MDFCN_DT, MDFR_SN, DEL_YN` (+ Y/N CHECK)
- [ ] PK = `{엔터티}_SN BIGINT AUTO_INCREMENT`
- [ ] 유니크가 필요한 컬럼은 **활성 유니크 생성컬럼**(`ACTV_*`) 패턴 (V1 참고)
- [ ] `ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='테이블논리명'`
- [ ] FK 정책: 업무 FK는 설정, **로그성 테이블은 FK 생략**(증적 보존)
- [ ] 금액 BIGINT(원), 일시 DATETIME(6), 날짜 DATE, 여부 CHAR(1) Y/N

## 4. 적용·확인
- 적용: 앱 재시작 → 부팅 로그 `Successfully applied ...` 확인
- 현황: `SELECT * FROM flyway_schema_history;`
- 실패 시: SQL 고치고(아직 미적용 상태이므로 수정 가능) 재부팅. history에 failed 행 남으면 팀 공유 후 repair.

## 5. 미정(추후 결정 — 임의 진행 금지)
- 공통코드 **seed 데이터** 전략 (V 마이그레이션 vs R__ 반복 스크립트)
- `REG_DT/MDFCN_DT` 입력 책임 (앱 통일 vs DB default)
- 운영 전환 시: 스테이징 검증 → 승인 → 적용 절차 (docs/db-operations.md)
