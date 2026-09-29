# DB 명명 표준 (공공데이터 공통표준용어 기반)

> 출처: 행정안전부 **공공데이터 공통표준용어**(`행정안전부_공공데이터 공통표준용어_20251101.csv`, 13,177건).
> 모든 테이블·컬럼명은 이 표준 단어/약어로 작성한다(D6). 모든 컬럼에 COMMENT(논리명) 필수.
> 표준에 없는 기술 필드(예: User-Agent, correlationId, passwordHash)는 합리적 명칭 + 주석으로 표기.

## 확정 표준 단어 → 약어 (사전 확인)

| 단어 | 약어 | 단어 | 약어 | 단어 | 약어 |
|---|---|---|---|---|---|
| 등록 | REG | 수정 | MDFCN | 삭제 | DEL |
| 등록자 | RGTR | 수정자 | MDFR | 담당자 | PIC |
| 일시 | DT | 일자 | YMD | 일련번호 | SN |
| 번호 | NO | 순서(정렬) | SORT_SEQ | 우선순위 | PRRTY_RNK |
| 사용자 | USER | 회원 | MBR | 이름/명 | NM |
| 코드 | CD | 그룹 | GROUP | 내용 | CN |
| 설명 | EXPLN | 상세 | DTL | 여부 | YN |
| 사용 | USE | 게시 | PSTG | 비밀번호 | PSWD |
| 암호화비밀번호 | ENPSWD | 이메일주소 | EML_ADDR | 전화번호 | TELNO |
| 휴대전화번호 | MBL_TELNO | 로그인 | LGN | 최종 | LAST |
| 탈퇴 | WHDWL | 가입 | JOIN | 역할 | ROLE |
| 권한 | AUTH | 부서 | DEPT | 실패 | FAIL |
| 사유 | RSN | 이력 | HSTRY | 로그 | LOG |
| 시작 | BGNG | 종료 | END | 만료 | EXPRY |
| 발급 | ISSU | 결과 | RSLT | 기기 | ISTR |

## 팀 확정 예외·규칙 (2026-06-12 사용자 확정 — 공공표준보다 우선)

1. **권한 = `AUTH`** — 공공표준 약어(AUTHRT)가 길어 축약 확정. 컬럼 `AUTH_SN`/`AUTH_CD`/`AUTH_NM`, 테이블은 `AUTH_MST`(규칙 7)/`ROLE_AUTH`.
2. **공통 = `COMM`** — `COM`은 유사 단어와 혼동되어 변경 확정. 공통코드 = `COMM_CD`/`COMM_CD_GROUP`.
3. **로그성 테이블 = `{대상 풀단어}_LOG_INFO`** — 약어 대신 풀단어 사용. 예: `LOGIN_LOG_INFO`, `AUDIT_LOG_INFO`,
   `PARTNER_APPROVE_LOG_INFO`. **앞으로 만드는 모든 로그 테이블(결제·알림 등)도 이 원칙 고수** (예: `PAYMENT_LOG_INFO`).
4. **상세정보(구 프로필) 테이블 = `{주체}_DTL_INFO`** — 예: `USER_DTL_INFO`, `OPERATOR_DTL_INFO`.
5. **정보 보관 성격의 지정 업무 테이블은 `_INFO` 부착** — `TRMS_AGRE_INFO`, `MBL_TELNO_VRFC_INFO`. (전 테이블 일괄 아님 — 사용자 지정분만)
6. **컬럼 원칙**: 의미 단위가 바뀐 단어(AUTH/COMM/테이블 정체성 스템)는 컬럼에도 적용하되, `_INFO` 같은 분류 접미사는
   컬럼명에 붙이지 않는다. 예: `LOGIN_LOG_INFO`의 PK = `LOGIN_LOG_SN` (LOGIN_LOG_INFO_SN 아님). 속성 컬럼의 표준약어(`LGN_ID` 등)는 유지.
7. **(2026-06-12 추가) 기준(마스터) 테이블 = `_MST` 접미** — 사용자 지정 4종: `AUTH_MST`(권한), `ROLE_MST`(역할),
   `PRTNR_MST`(파트너), `TRMS_MST`(약관). **테이블 COMMENT 에 정체를 반드시 명기**(짧은 이름 보완 — V7 반영).
   매핑 테이블(`USER_ROLE`, `ROLE_AUTH`)은 접미 없음.
8. **(2026-06-12 추가) 운영자 = `OPERATOR`** — 구 '마스터(MASTER/MSTR)' 명칭 폐기(`_MST` 접미와의 혼동 제거).
   코드값(`USER_TYPE`·`MENU_CHNL`·역할코드·`CHNL_LOGIN_OPERATOR`)과 테이블(`OPERATOR_DTL_INFO`) 모두 적용.
   ※ 개발 시드 계정 이메일(master@eeum.dev)은 유지(접속정보 변경 방지).

## 합성 예시 (사전 확인된 용어)
- 등록일시 = `REG_DT`, 수정일시 = `MDFCN_DT`, 최종로그인일시 = `LAST_LGN_DT`, 탈퇴일시 = `WHDWL_DT`, 가입일시 = `JOIN_DT`, 만료일시 = `EXPRY_DT`, 발급일시 = `ISSU_DT`
- 사용자일련번호 = `USER_SN`, 이력일련번호 = `HSTRY_SN`, 로그일련번호 = `LOG_SN`, 수정자일련번호 = `MDFR_SN`
- 회원명 = `MBR_NM`, 역할명 = `ROLE_NM`, 권한명 = `AUTH_NM`, 그룹명 = `GROUP_NM`, 코드그룹명 = `CD_GROUP_NM`
- 코드내용 = `CD_CN`, 코드설명 = `CD_EXPLN`, 상세설명 = `DTL_EXPLN`, 실패사유 = `FAIL_RSN`
- 사용여부 = `USE_YN`, 삭제여부 = `DEL_YN`

## 표준 감사(audit) 컬럼 (모든 업무 테이블 공통)
| 컬럼 | 논리명 |
|---|---|
| `REG_DT` | 등록일시 (DATETIME(6)) |
| `RGTR_SN` | 등록자(사용자일련번호, BIGINT → USER_SN 참조) |
| `MDFCN_DT` | 수정일시 (DATETIME(6)) |
| `MDFR_SN` | 수정자(사용자일련번호, BIGINT) |
| `DEL_YN` | 삭제여부 (CHAR(1) 'Y'/'N') — 삭제시각=MDFCN_DT |
> surrogate/FK 식별자는 `_SN`(일련번호)로 통일(`_ID`/`_NO` 혼재 금지). 코덱스 검토 반영.

## V2에서 추가 확인된 표준 단어 → 약어 (CSV 사전 확인, 2026-06-11)

| 단어 | 약어 | 단어 | 약어 | 단어 | 약어 |
|---|---|---|---|---|---|
| 약관 | TRMS | 동의 | AGRE | 버전번호 | VER_NO |
| 첨부파일 | ATCH_FILE | 원본파일명 | ORGNL_FILE_NM | 저장파일명 | STRG_FILE_NM |
| 파일크기 | FILE_SZ | 확장자명 | EXTN_NM | URL주소 | URL_ADDR |
| 승인 | APRV | 반려 | RJCT | 필수여부 | ESNTL_YN |
| 사업자등록번호 | BRNO | 상호 | CONM | 대표자 | RPRSV |
| 예금주 | DPSTR | 은행 | BANK | 계좌번호 | ACTNO |
| 직위 | JBPS | 시행일시 | ENFC_DT | 연결일시 | LNKG_DT |
| 주소 | ADDR | 상세주소 | DADDR | 검증완료일시 | VRFC_CMPTN_DT |

- 합성 예: 약관제목 = `TRMS_TTL`, 약관내용 = `TRMS_CN`, 동의여부 = `AGRE_YN`, 동의일시 = `AGRE_DT`, 반려사유 = `RJCT_RSN`, 승인일시 = `APRV_DT`, 예금주명 = `DPSTR_NM`.
- 암호화 접두 패턴(표준 ENPSWD/ENRRNO 준용): 암호화계좌번호 = `ENACTNO`.

## 표준 사전에 단독 항목이 없어 보완한 단어 (★검토 필요)
- 상태 = `STTS`, 접속 = `CNTN`(접속IP=`CNTN_IP`), 유형 = `TYPE`(확인됨)
- 추정(기관 표준단어집 확정 시 교체): 별명 = `NCNM`, 인증 = `VRFC`, 행위 = `ACTN`, 행위자 = `ACTR`, 대상 = `TRGT`, 상위 = `UPPER`, 단계 = `STEP`, 활성(유니크용) = `ACTV`
- V2 추가 보완(★): 파트너 = `PRTNR`, 프로필 = `PRFL`(폐기 — DTL_INFO 사용), 운영자 = `OPERATOR`(풀단어, 팀 확정 — 구 MSTR 폐기), 소셜 = `SOCIAL`, (소셜)제공자 = `PRVDR`, 변경전/변경후 = `BFR`/`AFT`, 목적 = `PRPS`, 저장소키 = `STRG_KEY`, MIME유형명 = `MIME_TYPE_NM`, 해시 = `HASH`
- 기술필드(표준 단독어 없음): User-Agent = `USER_AGNT`, correlationId = `CORR_ID`, 연계정보 = `CI` → 기관 표준단어 등록 후 일관 적용 권장.

## 규칙
- surrogate PK(BIGINT AUTO_INCREMENT) = `{엔터티}_SN` (일련번호). FK는 참조 PK명 또는 의미명_`SN`/`ID`.
- 코드성 컬럼 = `{개념}_CD`, 명칭 = `{개념}_NM`, 여부 = `{개념}_YN`.
