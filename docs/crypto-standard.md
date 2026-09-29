# 암호화 저장 표준 (필드·파일·키 관리)

> 확정일: 2026-06-11 (codex xhigh 교차검토 반영 — NIST SP 800-38D / OWASP Cryptographic Storage / 개인정보 안전성 확보조치 기준(PIPC 2025.11 안내서) 정합).
> **이 문서는 "정의"** — 구현(필드 암호화 모듈/파일 업로드)은 별도 단계에서 이 표준대로 진행한다.
> ⚠ 암호화는 안전조치의 일부일 뿐 — 접근통제(RBAC)·로그 마스킹·백업 보호가 같은 급(P0)이다.

---

## 1. 암호화 대상 (필수 — 누락 금지)

| 구분 | 대상 | 방식 |
|---|---|---|
| 필드 | `USER_ACNT.MBL_TELNO` 휴대전화번호 | AES-256-GCM + 검색용 `MBL_TELNO_HASH`(HMAC) |
| 필드 | `USER_ACNT.CI` 본인인증연계정보 | AES-256-GCM + 중복판단용 `CI_HASH`(**HMAC — §3**) |
| 필드 | `PRTNR_MST.PIC_MBL_TELNO` 담당자 전화번호 | AES-256-GCM (검색 불요 — 해시 없음) |
| 필드 | `RESERVATION_GUEST.GUEST_MBL_TELNO` 예약 게스트 전화번호 | AES-256-GCM (검색 불요 — 해시 없음) |
| 필드 | `PRTNR_MST.ENACTNO` 정산계좌번호 | AES-256-GCM (검색 불요) |
| 필드 | `PRTNR_MST.DPSTR_NM` 예금주명 | AES-256-GCM — 계좌번호·은행코드와 세트로 금융정보 (자가검토 반영 추가) |
| 필드 | `MBL_TELNO_VRFC_INFO.MBL_TELNO` (+HASH) | 위와 동일 |
| 파일 | `ATCH_FILE` 중 **`FILE_SCOPE_CD='PRIVATE_DOC'`** (사업자등록증 등) | envelope 암호화 (§5) |
| **제외** | `PUBLIC_IMAGE`(CDN 공개 서빙) ｜ `ENPSWD`(BCrypt 단방향 — 암호화 아님) ｜ **이메일주소**(로그인 ID — 평문 유니크 검색 필수) ｜ **이름/별명**(표시·검색용, 단독 위험도 낮음) ｜ **주소 — 사업장주소에 한정**(공개성 정보. ⚠ 개인 주거지 주소 컬럼이 생기면 기본 암호화 대상으로 재검토) ｜ **IP/UA**(로그 증적 — 보존 목적) | 제외 사유를 변경하려면 이 표 개정 필요 |
- ⚠ `DPSTR_NM` 은 V2 DDL 에 평문 VARCHAR(100)로 정의됨 — 구현 시 **VARCHAR(512) 확장 ALTER 동승** (한글 100자=UTF-8 300바이트 → GCM+Base64 암호문 최대 약 443자, 255로는 부족 — codex 재검토 반영).

- 신규 테이블에 전화번호/식별번호/계좌류 컬럼 추가 시 **이 표에 등록 + 암호화 적용이 기본값**.

## 2. 필드 암호화 사양

- **알고리즘**: AES-256-GCM, IV 96bit 랜덤(매 암호화마다 신규), 인증태그 128bit.
- **AAD 사용 (자가검토 반영 채택)**: AAD = **필드ID** UTF-8 바이트 (예: `USER_ACNT.MBL_TELNO`) — 한 컬럼의 암호문을 **다른 컬럼으로 복사·이식하면 복호화가 실패**하도록 바인딩.
  - **운영 계약 (codex 재검토 반영)**: 필드ID는 **최초 부여 시점에 동결** — 이후 테이블/컬럼이 리네임·분리되어도 **AAD는 바꾸지 않는다** (바꾸면 기존 암호문 복호화 불가). AAD를 변경하려면 **복호화 후 재암호화 마이그레이션이 필수**. 필드ID 목록은 §1 표가 진실원.
  - **TypeHandler 전달 방식 확정**: 필드ID별 **TypeHandler 서브클래스**(생성자에 필드ID 고정 — 예: `MblTelnoEncryptedTypeHandler extends EncryptedStringTypeHandler`)를 resultMap/파라미터에 지정. SQL alias·ResultSetMetaData 에서 필드ID **추론 금지**(깨짐).
- **암호문 포맷**: `v{키버전}:` + Base64(IV ‖ CIPHERTEXT ‖ TAG)
  - 예: `v1:3q2+7w...` / 길이 ≈ 프리픽스(`v1:`=3자) + ⌈(12+평문바이트+16)÷3⌉×4 → **이 포맷은 §1 지정 필드 전용** (255자 일반 텍스트 컬럼에는 부족).
- **적용 방식**: MyBatis **TypeHandler**(`EncryptedStringTypeHandler`, 위치 `egovframework.eeum.base.crypto`) — 서비스 코드에 암호화 호출을 흩뿌리지 않는다.
  - ⚠ **(구현 제약 — 자가검토 반영)** `resultType` 자동 매핑으로는 특정 컬럼에만 TypeHandler를 지정할 수 없다 →
    **암호화 컬럼이 포함된 SELECT 는 `resultMap` 작성 필수** (해당 컬럼에 `typeHandler=` 지정). 파라미터는 `#{필드,typeHandler=...}` 로 지정.
- **금지 규칙 (매퍼 작성 시 — 위반 = 리뷰 반려)**:
  1. 암호화 컬럼을 `WHERE`/`JOIN`/`ORDER BY`/`LIKE`에 사용 금지 (랜덤 IV라 같은 평문도 매번 다른 암호문) — **검색·중복검사는 `*_HASH` 컬럼만**.
  2. 암호화 컬럼이 포함된 조회에 `resultType` 자동매핑/`resultType="map"`/EgovMap 사용 금지 (TypeHandler 미적용·우회) — **반드시 resultMap + typeHandler 지정**.
  3. 암호문 값과 해시 값은 **같은 트랜잭션에서 함께** 생성/갱신 (불일치 = 중복검사 깨짐).

## 3. 해시(검색·유니크용) 사양

- **방식**: **HMAC-SHA256** (전용 키 — 암호화 키와 분리), 출력 = **hex 소문자 64자**(`CHAR(64) ascii_bin`).
- **정규화(해시 입력 전 필수 — 실패 시 중복검사 무력화)**:
  - 휴대전화번호: ① 숫자만 추출 → ② **선행 `82`는 `0`으로 치환** (`+82 10-1234-5678` → `821012345678` → `01012345678`) → ③ **최종값이 `01`로 시작하는 10~11자리인지 검증 — 불일치 시 저장 거부**(해시 오염 방지, codex 재검토 반영)
  - CI: 트림만 (원문 그대로 — 본인인증사 반환값)
- **CI_HASH = HMAC-SHA256으로 확정** (2026-06-11) — V1 주석의 "SHA-256"은 구버전 표기.
  현재 CI 데이터 0건이므로 백필 불요, **구현 첫날부터 HMAC 사용**. (V1 컬럼 COMMENT 정정은 다음 ALTER 마이그레이션에 동승 — V1 파일 수정 금지)
- 해시 키 회전은 비용이 큼(전 행 재계산) — 키 유출 시에만 회전, 절차는 §6.

## 4. 평문/암호문 판별 및 전환

- 값 판별: `NULL/빈문자열` = 없음 / `^v\d+:` = 암호문 / 그 외 = legacy 평문(발생하면 안 됨 — 발견 시 즉시 보고).
- **legacy 평문 탐지(구현 배포 전 1회 + 이후 점검용 — 전 대상 컬럼, 암호문 패턴 기준. codex 재검토 반영)**:
```sql
-- 0건이어야 정상 (값이 있는데 v{n}: 패턴이 아니면 legacy 평문)
SELECT 'USER_ACNT.MBL_TELNO' T, COUNT(*) C FROM USER_ACNT WHERE NULLIF(TRIM(MBL_TELNO),'') IS NOT NULL AND MBL_TELNO NOT REGEXP '^v[0-9]+:'
UNION ALL SELECT 'USER_ACNT.CI', COUNT(*) FROM USER_ACNT WHERE NULLIF(TRIM(CI),'') IS NOT NULL AND CI NOT REGEXP '^v[0-9]+:'
UNION ALL SELECT 'PRTNR_MST.PIC_MBL_TELNO', COUNT(*) FROM PRTNR_MST WHERE NULLIF(TRIM(PIC_MBL_TELNO),'') IS NOT NULL AND PIC_MBL_TELNO NOT REGEXP '^v[0-9]+:'
UNION ALL SELECT 'PRTNR_MST.ENACTNO', COUNT(*) FROM PRTNR_MST WHERE NULLIF(TRIM(ENACTNO),'') IS NOT NULL AND ENACTNO NOT REGEXP '^v[0-9]+:'
UNION ALL SELECT 'PRTNR_MST.DPSTR_NM', COUNT(*) FROM PRTNR_MST WHERE NULLIF(TRIM(DPSTR_NM),'') IS NOT NULL AND DPSTR_NM NOT REGEXP '^v[0-9]+:'
UNION ALL SELECT 'MBL_TELNO_VRFC_INFO.MBL_TELNO', COUNT(*) FROM MBL_TELNO_VRFC_INFO WHERE NULLIF(TRIM(MBL_TELNO),'') IS NOT NULL AND MBL_TELNO NOT REGEXP '^v[0-9]+:';
```
  (2026-06-11 기준 코드상 저장 경로 없음 → 전부 0건 전제)
- 복호화 실패(키 불일치/변조): 오류를 사용자에게 평문 노출 없이 500 처리 + 경고 로그(값 미포함) — 데이터 변조 의심 절차로 연결.

## 5. 파일 암호화 (PRIVATE_DOC — envelope 방식 확정)

- **구조**: 파일마다 랜덤 **DEK**(AES-256) 생성 → DEK로 파일 본문 AES-256-GCM 암호화 → DEK를 **KEK**(`FILE_KEK_Vn`)로 래핑(AES-GCM)해 메타데이터에 저장.
- **IV는 2개 — 구분 저장** (자가검토 반영): ① 본문 암호화용 IV ② DEK 래핑용 IV.
- **GCM 태그 위치 (codex 재검토 반영)**: 필드 포맷과 동일 — 본문 암호문 파일은 `IV ‖ CIPHERTEXT ‖ TAG` 순서로 저장(태그는 파일 끝에 append). 래핑된 DEK도 같은 순서로 메타 컬럼에.
- **파일 AAD (codex 재검토 반영)**: 본문 암호화의 AAD = `ATCH_FILE_SN + ":" + FILE_SCOPE_CD` — 메타데이터와 암호 객체를 바꿔치기(스왑)하면 복호화가 실패하도록 바인딩. AAD 동결 계약은 §2와 동일.
- **파일 크기 상한**: GCM은 인증태그 검증 전 평문을 내보내면 안 되므로 **일괄 처리 전제 — PRIVATE_DOC 업로드는 10MB 제한** (멀티파트 전역 100MB와 별도로 서류 업로드 API에서 제한).
- 장점: KEK 회전 시 파일 본문 재암호화 없이 **DEK 재래핑만** / 추후 S3·NCP **KMS(SSE-KMS)** 이전 용이.
- ATCH_FILE 메타 컬럼(업로드 구현 시 V 마이그레이션으로 추가): 암호화여부, KEK 키버전, 래핑된 DEK, 본문 IV, 래핑 IV.
- 운영자키 직접 암호화는 **업로드 기능 구현 전 임시 개발에만 허용** — 실서류 업로드 시작 전 envelope 필수.
- 오브젝트 스토리지 전환 시: 최소 SSE-KMS, 민감 서류는 앱 레벨 envelope 병행.

## 6. 키 관리

| 키 | 용도 | 형식 |
|---|---|---|
| `FIELD_ENC_KEY_V{n}` | 필드 암호화(AES-256) | base64 32바이트 |
| `FIELD_HMAC_KEY_V{n}` | 검색 해시(HMAC) | base64 32바이트 |
| `FILE_KEK_V{n}` | 파일 DEK 래핑 | base64 32바이트 |
| `EGOV_JWT_SECRET` | JWT 서명(기존) | 32바이트+ |

- **주입**: 환경변수. dev = `.env.develop`(개발 데이터 한정 공유 — 기존 방침), 운영 = 배포 시크릿(절대 커밋 금지). **KMS/Vault 도입은 클라우드 확정 시 결정**(decisions-pending).
- **기동 검증(fail-fast)**: 시작 시 base64 디코딩 + 32바이트 길이 검증, 실패 시 부팅 중단. **키 값 로그 출력 금지.**
- **키버전 매핑**: 코드/설정에 `버전 → (알고리즘, 키, 상태)` 관리. 상태 = `encrypt`(신규 암호화용, 1개만) / `decryptOnly`(구버전 — 복호화 전용).
- **회전 절차**: 새 키 V(n+1) 추가(`encrypt`) → 기존 키 `decryptOnly` 강등 → 신규 데이터는 자동으로 새 버전 → (선택) 재암호화 배치. 해시 키는 §3 참고(유출 시에만).
- **폐기**: 해당 버전 암호문이 0건임을 확인한 뒤에만 키 제거.

## 7. 로그·백업·노출 통제 (암호화와 동급 P0)

- SQL/파라미터 로그(MyBatis 바인딩 로그 포함)에 전화번호/CI/계좌 **평문 출력 금지** — 구현 시 로그 마스킹 동반 적용.
- API 응답에 복호화 값 노출 최소화(전화번호는 마스킹 `010-****-1234` 기본, 전체 노출은 권한 필요 화면만).
- DB 백업/덤프 파일은 접근 통제 대상(db-operations.md 연계). 운영 콘솔 조회도 해시/마스킹 우선.

## 8. 기타 확정 사항

- eGov 잔재 설정 `Globals.crypto.algoritm` 은 **이 표준과 무관(데모용) — 미사용** 명시.
- 검증: 구현 시 `*Verification`으로 라운드트립/포맷/정규화/금지규칙(매퍼 grep) 자동 검사 추가.
- 구현 순서(추후 단계): ① base.crypto 모듈+TypeHandler ② 가입/파트너 API에 전화번호 저장 활성화 ③ 파일 업로드(V5+)에서 envelope.
