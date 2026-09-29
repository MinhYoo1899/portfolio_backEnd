# 🔌 API 연동 규약 (프론트엔드 필독)

> **대상**: 사용자/파트너/운영자 SPA + 모바일 앱 개발자 ｜ 최초 2026-06-10 · **최종 갱신 2026-06-25** (auth/account/menu + 소셜(**웹 Authorization Code 흐름 + 네이버 추가**) + **약관 조회 API(§8-5)** + **가용성 조회 API(§8-6)** + 공통코드 + 파일첨부 API + 통합 첨부 반영). 소셜 웹 흐름 상세는 **frontend-auth-guide** 참고)
> **표기**: ✅ **확정·동작 중**(백엔드 구현+검증 완료) ｜ 📐 **고정 규칙**(전 API가 이 형태로 구현됨 — 팀 표준으로 확정, 단 도메인 API 자체는 아직) ｜ 🟡 **미확정**(바뀔 수 있음 — 확정 시 공지)

---

## 1. 환경 / Base URL

| 환경 | Base URL | 상태 |
| --- | --- | --- |
| 로컬(백엔드 직접 실행) | `http://localhost:8080` | ✅ |
| **개발서버 (2026-06-11 배포 완료)** | **`http://minoservers.iptime.org:8082`** | ✅ |

- 개발서버 Swagger: `http://minoservers.iptime.org:8082/swagger-ui/index.html` — **백엔드를 로컬에 안 띄워도 여기서 바로 API 확인·호출 가능**
- 개발서버는 **develop 브랜치 push 시 자동 배포**됩니다(CI/CD) — 항상 develop 최신 코드 기준
- 헬스체크: `GET /actuator/health` → `{"status":"UP"}`

- 📐 **URL에 `/api` 프리픽스 없음**, 버전 프리픽스 없음.

## 2. CORS 정책 ✅ (현재 동작 기준)

- 허용 origin: 백엔드 설정의 **명시 목록**(콤마 구분, 환경변수 `CORS_ALLOW_ORIGIN`). 현재 기본 `http://localhost:3000`.
  → **각 SPA의 dev origin(예: :3000/:3001/:3002)을 백엔드에 전달**하면 목록에 추가합니다. 와일드카드(*) 미사용.
- `credentials: true` 허용, 허용 메서드 GET/POST/PUT/PATCH/DELETE.
- 허용 요청 헤더: `Authorization`, `Content-Type`, `X-Requested-With`. 노출 헤더: `Authorization`, `X-Correlation-Id`.

## 3. URL 규칙 (액션식 — REST 아님) 📐

```
POST /{resource}/insert     생성
GET  /{resource}/list       목록(페이징)
GET  /{resource}/detail     단건(?{id}=)
POST /{resource}/update     수정 (body에 식별자 포함)
POST /{resource}/delete     삭제 (body에 식별자 포함)
+ 도메인 액션: POST /{resource}/{action}  (예: /reservations/hold)
```
- 📐 **조회 = GET + query parameter**, **변경 = POST + JSON body**. PUT/DELETE 미사용.
- 📐 성공 status: 조회/수정/삭제 **200**, 생성(insert) **201**. (어느 쪽이든 body의 envelope는 동일하므로, 프론트는 **2xx + resultCode=SUCCESS**로 성공 판정 권장)

## 4. 응답 envelope ✅ (모든 JSON 응답 — 동작 중)

```json
// 성공 (result 형태: 단건=객체, 목록=페이징객체, 생성=생성된 객체, 수정/삭제=null)
{ "resultCode": "SUCCESS", "resultMessage": "정상 처리되었습니다.", "result": { ... } }

// 목록
{ "resultCode": "SUCCESS", "resultMessage": "정상 처리되었습니다.",
  "result": { "items": [ ... ], "total": 137, "page": 1, "pageSize": 20, "totalPages": 7 } }

// 오류 (result = null)
{ "resultCode": "NOT_FOUND", "resultMessage": "대상을 찾을 수 없습니다.", "result": null }

// 입력 검증 실패 400 (필드 오류 목록 포함)
{ "resultCode": "VALIDATION_ERROR", "resultMessage": "입력값이 올바르지 않습니다.",
  "result": { "fieldErrors": [ { "field": "emlAddr", "message": "이메일 형식이 아닙니다." } ] } }
```
- ✅ `fieldErrors.field`의 중첩/배열 표기: Spring 표준 경로 — 예) `items[0].roomNm`, `applicant.telno`.
- ✅ 파일 다운로드: 바이너리 직접 응답(오류 시에만 envelope) — **구현 완료**(아래 §파일 첨부 API 참고).

## 5. resultCode ↔ HTTP status ✅ (전부 구현·검증됨)

| HTTP | resultCode (정확한 값) | 프론트 처리 |
| --- | --- | --- |
| 2xx | `SUCCESS` | 정상 |
| 400 | `VALIDATION_ERROR` | `result.fieldErrors`로 필드별 표시 |
| 401 | `UNAUTHORIZED` | 인증 흐름 가이드 참고 (refresh → 로그인) |
| 403 | `FORBIDDEN` | 권한 없음 안내 (로그인 이동 아님) |
| 404 | `NOT_FOUND` | 대상/경로 없음 |
| 405 | `METHOD_NOT_ALLOWED` | 호출 방식 오류(개발 중 실수) |
| 406 | `NOT_ACCEPTABLE` | Accept 헤더 오류 |
| 415 | `UNSUPPORTED_MEDIA_TYPE` | Content-Type 오류(JSON으로 보내세요) |
| 409 | `CONFLICT` | 상태 충돌 → 메시지 표시 |
| 429 | `RATE_LIMITED` | 요청 과다 |
| 500 | `INTERNAL_ERROR` | 공통 오류 안내 |
| 503 | `SERVICE_UNAVAILABLE` | 일시적 서비스 불가(예: 로그인 시 세션 저장소 일시 장애) → 잠시 후 재시도 안내 |

- 📐 도메인별 코드는 **`DOMAIN_REASON` 형식으로 추가**됩니다(예: `RESERVATION_SOLD_OUT`, 409로 응답). 각 도메인 API 공개 시 **그 API 문서에 발생 가능한 resultCode 목록을 명시**합니다.
- ✅ **확정된 도메인 코드 (2026-06-11, 구현·검증 완료)**:

| HTTP | resultCode | 발생 |
| --- | --- | --- |
| 401 | `AUTH_INVALID_CREDENTIALS` | 로그인 — 이메일/비밀번호 불일치(미존재 계정·**채널 자격 없음**도 동일 — 인증 가이드 §2 채널 제한 참고) |
| 403 | `AUTH_ACCOUNT_SUSPENDED` / `AUTH_ACCOUNT_REJECTED` | 로그인 — 정지/반려 계정 |
| 401 | `AUTH_REFRESH_INVALID` | refresh 만료·폐기·재사용(→재로그인) 또는 **채널 불일치**(세션 유지 — 올바른 chnlCd로 재호출) |
| 401 | `AUTH_SOCIAL_TOKEN_INVALID` | 소셜 로그인 — id_token 또는 가입티켓 무효(위조·만료·미설정). social-login부터 재시도 |
| 400/410 | `OAUTH_RESULT_INVALID` | 소셜 **웹 흐름** — result 토큰 무효·만료·재사용·흐름바인딩 불일치. authorize부터 재시도 (콜백 단계 실패는 프론트로 `?error=oauth_failed` 리다이렉트) |
| 400 | `OAUTH_PROVIDER_UNSUPPORTED` | 소셜 웹 흐름 — 미지원 provider/purpose |
| 409 | `ACCOUNT_EMAIL_DUPLICATE` | 가입 — 이메일 중복 |
| 400 | `ACCOUNT_TERMS_REQUIRED` | 가입 — 필수약관 미동의 |
| 404 | `TERMS_NOT_FOUND` | 약관 상세 — 미존재/미시행 trmsSn (§8-5) |
| 404 | `ACCOUNT_NOT_FOUND` | 계정 조회/수정 — 대상 없음 |
| 401 | `ACCOUNT_PASSWORD_MISMATCH` | 탈퇴 — 본인확인 비밀번호 불일치 |
| 409 | `PARTNER_BRNO_DUPLICATE` | 파트너 가입 — 사업자등록번호 중복 |
| 404 | `PARTNER_NOT_FOUND` | 파트너 조회 — 대상 없음 |
| 409 | `PARTNER_ALREADY_PROCESSED` | 파트너 승인/반려 — 이미 처리됨 |
- 🆕 **로그인 보호(2026-06-15 적용)**: 짧은 시간 반복 실패 시 일시적으로 로그인이 차단됩니다. 이때도 응답은 동일한 `401 AUTH_INVALID_CREDENTIALS`(잠금 사실·남은시간 비노출 — 계정 열거 방지). 별도 처리 불필요. 단, 세션 저장소 일시 장애 시 로그인은 `503 SERVICE_UNAVAILABLE`로 응답되므로(비밀번호 오류 아님) "잠시 후 재시도"로 안내하세요.
- **분기는 resultCode 문자열 기준** (resultMessage 텍스트 비교 금지 — 메시지는 바뀔 수 있음).
- 권장 구현: 공통 코드(위 표)는 전역 처리 + 도메인 코드는 화면별 처리, **모르는 코드는 HTTP status 기준 fallback**.

## 6. 데이터 포맷 📐 (고정 규칙)

| 항목 | 규칙 | 예 |
| --- | --- | --- |
| 날짜 | `yyyy-MM-dd` | `"2026-06-10"` |
| 일시 | ISO-8601 **UTC `Z`** (화면 표시는 프론트가 KST 변환) | `"2026-06-18T05:50:49.692060Z"` |
| 금액 | **원 단위 정수**(JSON number) | `150000` ※ KRW 원 단위는 JS safe integer(9007조) 내 — 안전 |
| ID | **문자열** — 응답·요청(query/body) 모두 | `"1024"`, `?userSn=1024`(서버가 파싱) |
| boolean | JSON `true/false` (DB의 Y/N은 노출 안 됨) | `true` |
| enum/코드 | 대문자 스네이크 문자열 | `"RESERVED"` |
| JSON 키 | camelCase | `userNm`, `regDt` |

## 7. 페이징 (서버사이드 — 그리드 연동)

- ✅ 응답: `result.items` + `total` + `totalPages` (`PageResponse` — 구현됨)
- 📐 요청: `?page=1&pageSize=20` — page **1부터**, pageSize 최대 100. 빈 검색값 파라미터는 생략(빈 문자열 전송 비권장).
- 🟡 **정렬 파라미터 형식 미확정** — 도메인 API별로 허용 정렬 컬럼(whitelist)과 함께 확정·공지 예정. 그 전까지 정렬 UI는 백엔드와 협의 후 구현.
- 📐 정렬·필터는 서버 처리. **전체 받아서 클라에서 자르기 금지.**
- ✅ 예외: **메뉴(`GET /menus/list`)는 전량 반환**(페이징 없음, result가 배열) — 메뉴는 건수가 작고 트리 조립이 필요해서 예외로 확정. 그 외 목록은 전부 PageResponse.

## 8. 인증 헤더 ✅

```
Authorization: Bearer {accessToken}
```
- 401/403 envelope 응답은 **현재 동작 중** — 상세 흐름은 [인증·권한 흐름 가이드] 참고.

## 8-2. 오류 신고 시 `X-Correlation-Id` 첨부 ✅ (2026-06-11 추가)

- 모든 응답에 요청 추적용 UUID 헤더 `X-Correlation-Id`가 내려갑니다.
- API 오류·이상 동작 신고 시 **이 헤더 값을 같이 전달**해 주세요 — 백엔드 로그·감사 기록이 이 ID로 한 번에 검색됩니다.
- (권장) 프론트 공통 에러 핸들러에서 오류 응답의 헤더 값을 콘솔/리포트에 남겨두면 추적이 쉽습니다. 별도 요청 헤더 세팅은 불필요(서버가 발급).

## 8-3. 파일 첨부 API — 공개 이미지 ✅ (2026-06-16 구현·검증 · **2026-06-18 정책 강화: 업로드 권한·쿼터·해상도**)

> Phase 1 = **공개 이미지(PUBLIC_IMAGE)** 전용. 사업자서류 등 비공개문서(PRIVATE_DOC, 암호화)는 Phase 2(추후).
> 저장은 백엔드 경유(SFTP). **업로드는 운영자·파트너만**, 삭제는 인증, 조회/다운로드는 공개.

- **업로드** `POST /files/insert` — **운영자·파트너 권한(`FILE_UPLOAD`) 필요**(Bearer), `multipart/form-data`
  - ⚠ **일반 회원은 업로드 불가(403)**. 문의·리뷰 등 일반 사용자 첨부는 **비공개 스코프**로 별도 제공 예정(Phase 2).
  - 필드: `file`(필수, 이미지) · `trgtType`/`trgtId`(선택 — 연결 대상 메모)
  - 제약: 확장자 `gif/jpg/jpeg/png` · **50MB 이하** · **매직바이트 검사**(확장자 위조 거부) · **해상도 ~50MP**(픽셀폭탄 차단) · **업로드 한도**(업로더 1인 최근 24시간 **50건·500MB**)
  - 응답 **201**: `result = { atchFileSn, orgnlFileNm, downloadUrl, mimeTypeNm, extnNm, fileSz }`
- **다운로드** `GET /files/download?atchFileSn=` — **공개**(비인증). **바이너리 직접 응답**(envelope 아님).
  - `Content-Type`(이미지 MIME) · `Content-Disposition: inline`(브라우저 표시) · `X-Content-Type-Options: nosniff` 헤더.
  - 👉 업로드 응답의 `downloadUrl`(`/files/download?atchFileSn=N`)을 **`<img src>`에 그대로** 사용 가능.
- **메타 조회** `GET /files/detail?atchFileSn=` — 공개. envelope로 메타 반환.
- **삭제** `POST /files/delete` — **인증 필요**. body `{ "atchFileSn": 1 }`. **등록자 본인 또는 운영자만**(아니면 403).

| HTTP | resultCode | 발생 |
| --- | --- | --- |
| 400 | `FILE_EMPTY` | 빈 파일 |
| 400 | `FILE_EXTENSION_NOT_ALLOWED` | 허용 외 확장자 |
| 400 | `FILE_CONTENT_INVALID` | 내용이 형식과 불일치(확장자 위조 등) |
| 413 | `FILE_TOO_LARGE` | 50MB 초과 |
| 413 | `FILE_DIMENSION_TOO_LARGE` | 이미지 해상도(픽셀) 한도(~50MP) 초과 |
| 429 | `FILE_QUOTA_EXCEEDED` | 업로드 한도(최근 24h 건수/용량) 초과 → 잠시 후 재시도 |
| 404 | `FILE_NOT_FOUND` | 없거나 삭제된 파일 |
| 403 | `FORBIDDEN` | 업로드 권한(`FILE_UPLOAD`) 없음 — 일반 회원 등 |
| 403 | `FILE_DELETE_FORBIDDEN` | 삭제 권한 없음(본인/운영자 아님) |
| 503 | `FILE_STORAGE_UNAVAILABLE` | 파일 저장소 일시 불가 → 잠시 후 재시도 |

> 📌 **지원 형식**: 현재 **이미지(gif/jpg/jpeg/png)** 만. 문서(PDF·엑셀 등)·기타 형식이 필요하면 **용도·공개범위·용량 정책 확정 후** 백엔드에서 허용목록·검증 시그니처를 추가합니다(소규모). 비공개·민감 문서는 **Phase 2(파일 암호화)** 와 함께 제공 예정. → 필요한 형식이 있으면 백엔드에 요청해 주세요.

## 8-3b. 프로필 이미지 API ✅ (2026-06-18 구현 — 통합 첨부 시스템)

> **프로필 이미지(PROFILE_IMAGE)** — 본인 인증만 필요(공개 이미지 업로드 권한 `FILE_UPLOAD` **불요**). 업로드=즉시 교체(이전 이미지 자동 정리).
> EXIF/GPS 등 메타데이터는 **재인코딩 시 제거**됩니다(위치정보 유출 방어).

- **업로드/교체** `POST /accounts/profile-image` — **인증(Bearer), 대상=토큰의 본인**. `multipart/form-data`.
  - 필드: `file`(필수, 이미지)
  - 동작: 업로드 즉시 `USER_DTL_INFO.PRFL_IMG_FILE_SN` 을 새 파일로 **교체**(이전 프로필 이미지는 자동 soft delete — 별도 삭제 호출 불필요).
  - 제약: 확장자 **`jpg/jpeg/png`** · **5MB 이하** · 해상도 **~16MP**(픽셀폭탄 차단) · 업로드 한도(업로더 1인 최근 24시간 **50건·200MB**)
  - 응답 **201**: `result = { atchFileSn, downloadUrl, mimeTypeNm, extnNm, fileSz }`
  - 👉 응답의 `downloadUrl`(`/files/download?atchFileSn=N`)을 `<img src>`에 그대로 사용 가능(다운로드는 공개 — §8-3).

| HTTP | resultCode | 발생 |
| --- | --- | --- |
| 400 | `FILE_EMPTY` | 빈 파일 |
| 400 | `FILE_EXTENSION_NOT_ALLOWED` | 허용 외 확장자(jpg/jpeg/png 외) |
| 400 | `FILE_CONTENT_INVALID` | 내용이 형식과 불일치(확장자 위조 등) |
| 413 | `FILE_TOO_LARGE` | 5MB 초과 |
| 413 | `FILE_DIMENSION_TOO_LARGE` | 이미지 해상도(픽셀) 한도(~16MP) 초과 |
| 429 | `FILE_QUOTA_EXCEEDED` | 업로드 한도(최근 24h 건수/용량) 초과 → 잠시 후 재시도 |
| 503 | `FILE_STORAGE_UNAVAILABLE` | 파일 저장소 일시 불가 → 잠시 후 재시도 |

## 8-4. 문의(게시판형 CS Q&A) API ✅ (2026-06-16 구현·개발서버 실연동 검증 완료)

> 공개 게시판 + 비밀글 구조. **목록/상세는 공개**(비밀글·블라인드는 접근제어), **작성/수정/삭제는 회원 본인**, **답변/블라인드는 운영자**.
> 목록은 **오프셋(그리드)** 과 **커서(무한스크롤)** 두 모드 지원. ID는 전부 **문자열**.

**비밀글 규칙(중요)**

- `secret` 미지정 시 유형별 기본값: **예약(RESERVATION)·결제(PAYMENT)·계정(ACCOUNT) = 비밀(Y)**, 그 외(ETC 등) = 공개(N). 명시값이 항상 우선.
- 비밀글을 **비인가자**(비로그인·타회원)가 목록에서 보면 **제목·작성자명이 `null`** 로 내려오고 `masked: true`. 상세는 **404**(작성자/운영자만 열람).
- **블라인드(운영자 부적절글 처리)** 글도 일반 사용자에게는 목록 비노출 + 상세 404.

**엔드포인트**

- **작성** `POST /inquiries/insert` — 인증. body `{ inqrTypeCd, inqrTtl, inqrCn, secret?, attachments? }`. 응답 **201**, `result` = 상세 객체.
  - `inqrTypeCd` 는 공통코드 **`INQR_TYPE`**(RESERVATION/PAYMENT/ACCOUNT/ETC) 값만 허용(임의값 거부).
  - `attachments` = 먼저 임시 업로드(`POST /inquiries/files/upload`)한 첨부를 연결할 목록. 각 항목 `{ atchFileSn, visibility? }`(visibility 미지정 시 PUBLIC). 첨부 정책·규칙은 아래 **첨부** 참고.
- **목록** `GET /inquiries/list` — 공개. 두 모드(아래). 필터 `mine`(본인글, 로그인 필요)·`inqrType`·`ansStts`(PENDING/ANSWERED)·`keyword`(제목 검색).
  - **오프셋 모드(그리드)**: `?page=1&pageSize=20` → `result = { items[], total, page, pageSize, totalPages }`
  - **커서 모드(무한스크롤)**: `?size=20`(첫 페이지) → 응답 `nextCursor`·`hasNext` → 다음 호출 `?size=20&cursor={nextCursor}`. `hasNext:false`면 끝.
  - `items[]` 항목: `{ inqrSn, inqrTypeCd, inqrTtl(마스킹 시 null), writerNm(마스킹 시 null), secret, blinded, masked, ansStts, answerCnt, regDt }`
- **상세** `GET /inquiries/detail?inqrSn=` — 공개글 누구나. 비밀글/블라인드는 작성자·운영자만(아니면 **404**). `result` = `{ inqrSn, inqrTypeCd, inqrTtl, inqrCn, writerNm, secret, blinded, ansStts, regDt, answers[], attachments[] }`.
  - `attachments[]` 항목: `{ atchFileSn, orgnlFileNm, downloadUrl, fileSz, visibility }`. `downloadUrl` 은 **권한판정 경유**(`/inquiries/files/download?atchFileSn=N`) — PRIVATE 첨부는 작성자·운영자만 실제 다운로드 가능.
- **수정** `POST /inquiries/update` — **작성자 본인 + 답변 전(PENDING)** 에만. body `{ inqrSn, inqrTypeCd, inqrTtl, inqrCn, secret?, attachments? }`. (답변 완료 글은 409)
  - `attachments` 는 **수정 후 유지할 첨부 전체 목록**(목록에서 빠진 기존 첨부는 연결해제, null/빈 목록이면 전부 해제). 새 첨부는 먼저 임시 업로드 후 `atchFileSn` 으로 연결.
- **삭제** `POST /inquiries/delete` — 작성자 본인(soft delete). body `{ inqrSn }`.
- **답변 등록** `POST /inquiries/answers/insert` — **운영자(INQR_ANS)**. body `{ inqrSn, ansCn }`. 등록 시 문의 상태 **ANSWERED**. 응답 = 상세 객체.
- **답변 수정** `POST /inquiries/answers/update` — 운영자(INQR_ANS). body `{ ansSn, ansCn }`.
- **답변 삭제** `POST /inquiries/answers/delete` — 운영자(INQR_ANS). body `{ ansSn }`. 남은 답변 없으면 상태 **PENDING** 복귀.
- **블라인드** `POST /inquiries/blind` — **운영자(INQR_MODER)**. body `{ inqrSn, blndRsn }`. 부적절글 비노출.
- **블라인드 해제** `POST /inquiries/unblind` — 운영자(INQR_MODER). body `{ inqrSn }`.

**첨부 (이미지 — 선업로드 후 연결, 하이브리드 공개성)** ✅ (2026-06-18 — 통합 첨부 시스템)

> 흐름: ①임시 업로드(`atchFileSn` 수령) → ②작성/수정 body `attachments` 로 연결 → ③상세 `attachments[]` 의 `downloadUrl` 로 표시.

- **임시 업로드** `POST /inquiries/files/upload` — **인증(회원 본인)**. `multipart/form-data`.
  - 필드: `file`(필수, 이미지) · `visibility`(선택, `PUBLIC`/`PRIVATE` — **미지정 시 PUBLIC**)
  - EXIF/GPS 제거 재인코딩 후 **임시(TEMP)** 저장. 응답 **201**: `result = { atchFileSn, orgnlFileNm, downloadUrl, mimeTypeNm, extnNm, fileSz }`.
  - ⚠ 이 단계에서 받은 `atchFileSn` 만 사용하세요. **업로드 응답의 `downloadUrl` 은 무시**(임시값) — 실제 다운로드 URL은 **연결 후 상세(`detail`)의 `attachments[].downloadUrl`**(`/inquiries/files/download?...`) 입니다.
  - ⚠ 업로드만 하고 **24시간 내 글에 연결하지 않으면 자동 정리**(삭제)됩니다.
- **다운로드** `GET /inquiries/files/download?atchFileSn=` — **하이브리드 권한판정**. **바이너리 직접 응답**(envelope 아님).
  - **공개글 + PUBLIC 첨부 = 누구나(비인증 가능)**. **비밀글·PRIVATE 첨부·삭제·블라인드 = 작성자·담당 운영자만**(비인가 시 **404** — 존재 은닉).
  - `Content-Disposition: inline` · `X-Content-Type-Options: nosniff`. PUBLIC 이미지는 `<img src>`에 그대로 사용 가능.

**하이브리드 공개성 규칙(중요)**

- **공개글**: 첨부별로 `PUBLIC`/`PRIVATE` 선택 가능(미지정 시 **PUBLIC**).
- **비밀글**: 첨부는 **항상 PRIVATE** — 글 공개성이 상한선. `visibility:"PUBLIC"` 으로 보내도 서버가 **자동으로 PRIVATE 로 보정**(에러 아님, 그대로 연결됨). 즉 비밀글 첨부는 공개될 수 없습니다.
- **공개→비밀 전환** 시(수정에서 `secret:true`): 연결된 첨부는 **전부 PRIVATE 로 강제**.

**첨부 제한**: 문의 1건당 **최대 5개** · 파일 1건 **10MB 이하** · 1건당 **총 30MB 이하** · 형식 **jpg/jpeg/png**.

| HTTP | resultCode | 발생 |
| --- | --- | --- |
| 404 | `INQUIRY_NOT_FOUND` | 없거나 삭제, **또는 비인가자의 비밀글·블라인드 접근**(존재 은닉) |
| 403 | `INQUIRY_FORBIDDEN` | 타인 글 수정/삭제 시도 |
| 409 | `INQUIRY_LOCKED` | 답변 완료(또는 답변 존재) 글을 작성자가 수정 |
| 404 | `INQUIRY_ANSWER_NOT_FOUND` | 없거나 삭제된 답변 |
| 400 | `FILE_ATTACHMENT_INVALID` | 연결 불가 첨부(타인 파일·타 용도·이미 연결·만료·삭제 등 — 열거 방지 단일 코드) |
| 400 | `FILE_ATTACHMENT_COUNT_EXCEEDED` | 문의당 첨부 개수(5개) 초과 |
| 413 | `FILE_ATTACHMENT_SIZE_EXCEEDED` | 문의당 첨부 총 용량(30MB) 초과 |

> 📌 첨부 1건의 빈 파일/확장자/매직바이트/크기(10MB)/해상도/쿼터 검증은 업로드 단계에서 §8-3·§8-3b 와 동일한 `FILE_*` 코드(`FILE_EMPTY`/`FILE_EXTENSION_NOT_ALLOWED`/`FILE_CONTENT_INVALID`/`FILE_TOO_LARGE`/`FILE_DIMENSION_TOO_LARGE`/`FILE_QUOTA_EXCEEDED`)로 응답됩니다. 위 `FILE_ATTACHMENT_*` 는 **연결(작성/수정) 단계** 검증입니다.

> 📌 **XSS 주의(프론트 책임)**: 제목·내용·답변은 사용자 입력 평문입니다. 화면 렌더링 시 **반드시 이스케이프**(React 기본 텍스트 바인딩 사용, `dangerouslySetInnerHTML` 금지)하세요. 서버는 저장 원문을 보존합니다.
> 📌 **권한**: 운영자 권한은 `INQR_ANS`(답변)·`INQR_MODER`(게시관리) 두 가지로 분리. 답변자/모더레이터를 역할로 분리 운영 가능.

## 8-5. 약관 조회 API ✅ (2026-06-22 구현·개발서버 검증)

가입 동의화면용. **`trmsSn` 하드코딩 금지 — 반드시 `/terms/list`로 받아 동의 목록을 구성**하세요. 둘 다 **비로그인 공개**(가입 전 호출).

| 화면/용도 | API | 응답 |
| --- | --- | --- |
| 동의 목록(체크박스) | `GET /terms/list` | `[{ trmsSn, trmsTypeCd, verNo, trmsTtl, esntlYn }]` — **유형당 최신 시행버전 1건**, 본문 미포함 |
| 약관 본문("보기") | `GET /terms/detail?trmsSn=N` | `{ trmsSn, trmsTypeCd, verNo, trmsTtl, trmsCn }` — 본문 포함 |

- `esntlYn`: `"Y"`=필수동의(전부 동의해야 가입), `"N"`=선택동의. `trmsTypeCd`=공통코드 `TRMS_TYPE`(SERVICE/PRIVACY/MARKETING…).
- **가입 흐름**: `/terms/list`로 목록 받기 → 필수 전부 + 선택 동의 받기 → 가입 호출 시 `trmsAgreList:[{trmsSn, agreYn:"Y"|"N"}]`로 제출(일반 `POST /accounts/insert`, 소셜 `POST /auth/social-signup`, 파트너 `POST /partners/insert` 공통). 필수 미동의면 `400 ACCOUNT_TERMS_REQUIRED`.
- `detail`은 **시행 중(사용·미삭제·시행일 도래) 약관만** 조회됩니다. 그 외 `trmsSn`은 `404 TERMS_NOT_FOUND`. `trmsSn`은 양수 — 누락/음수는 `400`.
- 📌 **XSS 주의(프론트 책임)**: 제목·본문은 입력 평문이고, 서버 응답은 특수문자가 HTML 엔티티로 인코딩될 수 있습니다(예: `·`→`&middot;`). React 기본 텍스트 바인딩으로 렌더(필요 시 디코드), `dangerouslySetInnerHTML` 금지.

## 8-6. 가용성 조회 API ✅ (2026-06-25 구현·개발서버 검증 완료)

> 손님이 **특정 고택**의 기간·인원에 예약 가능한 객실과 가격합계를 조회. **로그인 필요**(일반회원 누구나 — 별도 권한 불요). **ACTIVE 고택·객실만** 노출.
> ⚠ 이 API는 **고택을 이미 알고 있을 때**(`houseSn` 보유) 쓰는 단계입니다. **손님용 고택 목록/검색/상세(공개 브라우즈)는 아직 미구현** — 아래 📌 참고.

- **가용성 조회** `GET /availability?houseSn=&checkInDate=&checkOutDate=&guestCount=` — 인증(Bearer)
  - 파라미터(전부 필수): `houseSn` · `checkInDate`(KST, **포함**) · `checkOutDate`(KST, **미포함**) · `guestCount`(1 이상)
  - 기간 = `[checkInDate, checkOutDate)` **반개구간**(체크아웃일은 숙박 안 함). 최소 1박 ~ 최대 365박.
  - 응답 `result`:

```json
{ "houseSn": "5", "checkInDate": "2026-07-04", "checkOutDate": "2026-07-06", "nights": 2,
  "rooms": [
    { "roomSn": "11", "roomNm": "안채", "bscPrsnCnt": 2, "maxPrsnCnt": 4,
      "available": true,  "reason": null,       "totalPrice": 300000 },
    { "roomSn": "12", "roomNm": "사랑채", "bscPrsnCnt": 2, "maxPrsnCnt": 2,
      "available": false, "reason": "SOLD_OUT", "totalPrice": null }
  ] }
```

  - `available=true` → 요청 **전 기간** 예약 가능 + `totalPrice`(KRW 정수, **전 기간 합계**) 채워짐. `false` → `totalPrice=null` + `reason`:
    - `CAPACITY` — 최대인원 < 요청인원 ｜ `MIN_STAY` — 박수 < 객실 최소숙박일 ｜ `SOLD_OUT` — 판매가능 밤 수 부족(매진 또는 해당일 캘린더 미설정)
    - (한 객실에 여러 사유 해당 시 우선순위 **CAPACITY → MIN_STAY → SOLD_OUT** 중 하나만 내려감)
  - 재고 기준 = **DB(ROOM_CALENDAR)의 TOTAL−SOLD**. "**캘린더 행이 없는 날짜 = 판매 불가**". (Redis 임시점유 HOLD 차감은 예약 슬라이스에서 추가 예정.)

| HTTP | resultCode | 발생 |
| --- | --- | --- |
| 400 | `CALENDAR_INVALID_RANGE` | 체크아웃 ≤ 체크인(최소 1박) 또는 365박 초과 |
| 400 | `VALIDATION_ERROR` | 필수 파라미터 누락·형식오류(`houseSn`/날짜/`guestCount<1`) |

> 📌 **손님용 고택 목록/검색/상세(메인 추천·검색·상세)는 아직 백엔드 미구현입니다.** 현재 `/houses/*`·`/rooms/*` 엔드포인트는 전부 **파트너(판매자) 본인 소유 관리 전용**(`HOUSE_MNG` 권한)이라 **손님 브라우즈로 쓸 수 없습니다**(타 소유 조회 시 404·권한 없으면 403). 손님용 **공개** 조회 API(고택 목록/검색/상세)는 설계 후 별도 공지합니다. 그 전까지 프론트의 고택검색/상세 화면은 ComingSoon 유지가 맞습니다.

## 8-7. 운영자 상품등록/이어작성(임시저장) API 계약 ✅ (2026-06-29 점검·codex 교차검토)

> 운영자 콘솔의 상품(고택/객실) 등록·수정·이어작성용. 변경계 권한 = `HOUSE_PROXY_MNG`. owner(prtnrSn)는 서버가 대상에서 해석 — **body에 prtnrSn 넣지 말 것**(`houses/insert`만 대상파트너 지정용 예외). 응답 envelope = `ApiResponse<T>`, insert=201, update/set/delete=200.

### 권한 (조회는 변경계와 다름)
- 변경계(insert/update/delete/status/policy/*-set) = `HOUSE_PROXY_MNG`
- 고택 detail/list = `HOUSE_VIEW`(소유권무관) · 객실 detail/list = `HOUSE_PROXY_MNG` · export = `HOUSE_EXPORT` · suspend/release = `HOUSE_MODER`

### ★함정1 — update/policy/room update 는 null OVERWRITE
`POST /operator/houses/update`, `/operator/houses/policy`, `/operator/rooms/update`는 보낸 값으로 컬럼을 통째 덮는다(생략한 optional 필드 = **NULL로 지워짐**). 이어작성에서 부분저장하면 기존값 소실. **반드시 detail GET → 기존값 머지 → 전체 필드 전송.** (예외: 객실 `dfltStockQty`/`minStayNgtCnt`는 null이면 1로 보정. status/insert에는 해당 없음)

### ★함정2 — *-set 은 FULL REPLACE
`pay-methods/set`, `beddings/set`, `rooms/amenities/set`, `options/set`, `climate/set`, `houses/amenities/set` 은 선택집합 전체 교체. **빈 배열 = 전부 해제**(부분추가 아님). 단 누락(필드 자체 미전송)은 `@NotNull` 로 400(전체해제 아님).

### ★함정3 — 동시성(낙관적 락 없음)
update/policy/set 모두 전체 덮어쓰기인데 version/ETag/mdfcnDt 조건이 없다. 다중 탭·동시 운영자 편집 시 lost update 가능. 같은 화면 동시편집 금지 또는 저장 직전 재조회 권장.

### 이어작성 detail 조합 (단일 detail로 다 안 내려옴)
- 고택: `GET /operator/houses/detail`(HOUSE_VIEW) + `/operator/rooms/list` + `/operator/houses/amenities` + `/operator/houses/images`
- 객실: `GET /operator/rooms/detail`(bedding/options/climate/amenities 임베드) + `/operator/rooms/images`
- `lateChkoutTime`/`noticeCn`/`houseSttsCd`/`cnclRfndPlcyCn`/`pbtrnspGuideCn`/`chkinTime`·`chkoutTime` → houses/detail 에 있음
- 이미지ID = images GET 의 `houseImgSn`/`roomImgSn`. (대표이미지 `repImgFileSn`은 **첨부파일ID(atchFileSn)** 이지 houseImgSn 아님 — 삭제엔 images GET 의 houseImgSn/roomImgSn 사용)

### 이미지 업로드/삭제
- 업로드 응답에 `houseImgSn`/`roomImgSn` 포함. 삭제 = `POST /operator/houses(rooms)/images/delete`(필수 = parent ID + image ID, 소유권 서버검증).
- 삭제 동작 = row/파일메타 soft delete + 물리파일 커밋 후 best-effort 삭제(실패분만 정리배치). 대표이미지 삭제 시 첫 이미지 자동 승격(프론트 상태 갱신할 것).
- ★함정4 — **삭제 비멱등**: 404는 "이미 삭제" 외 없는 ID·잘못된 houseSn/roomSn·소유권불일치도 포함. "방금 조회/업로드한 그 ID의 재시도 404"만 성공 취급, 그 외 404는 재조회/에러처리.
- 주의: 업로드는 **즉시 실DB+파일 생성**. "업로드→임시저장→취소"로 delete 안 하고 이탈하면 이미지 잔존(자동회수 기대 금지). 화면에서 제거 시 반드시 delete 호출.

### 미지원 (저장되는 척 UI 금지)
정기휴관일(요일반복) · 법정공휴일휴무 · 최대숙박기간(min-stay만 있음) · 고급요금정책(기본 날짜별 가격만) · OTA연동 · 정산/환불자동화 = **미지원**.
지원: 인원추가요금(`houses/policy` adult/child/infantAddChrgAmt) · 임시휴무일(`/operator/rooms/calendar/set` 날짜별 CLOSED). 판매자 신고번호/연락처는 파트너 도메인(상품 폼 아님).

### 코드값 (하드코딩 금지)
`GET /codes/list?groupCd=COOLING_TYPE|HEATING_TYPE|BEDDING_TYPE|AMENITY_CTGRY` 동적조회. 편의시설 저장은 `amenitySn`(ID), 직접입력은 `customAmenityNms`. 객실 추가옵션은 코드 아님(자유텍스트 `optionNm`+`addAmt`).

### 편의시설 분류 5종 (2026-06-29 확정·배포)
`AMENITY_CTGRY` = `ROOM_FEATURE`(객실편의시설) / `ELECTRONICS`(전자제품) / `BATHROOM`(욕실용품) / `OUTDOOR`(외부시설) / `SPECIAL`(특수시설). `GET /codes/list?groupCd=AMENITY_CTGRY` 로 받아 그룹핑. 적용범위 `applyScopeCd`(HOUSE/ROOM/BOTH)는 분류와 **별개 축**(객실엔 ROOM/BOTH만 매핑). 승인 게이트 없음 — 직접입력은 즉시 노출.

> **조회 scope 정합(2026-06-29):** 편의시설 조회(`/operator/rooms·houses/amenities`, room detail 임베드 amenities)는 **백엔드가 저장가능 scope만** 내려준다(객실=ROOM/BOTH, 고택=HOUSE/BOTH). 이전에 객실에 잘못 매핑됐던 고택전용 항목(예: 무료주차) 때문에 "변경 없이 저장 시 404" 나던 round-trip 문제를 백엔드에서 차단(V35로 기존 오염 매핑 정리). 그래도 프론트는 detail 임베드 등에서 set 재전송 전 `applyScopeCd` 한 번 더 확인 권장(defense in depth).

## 9. 금지 패턴 ⚠

- ❌ 금액을 클라이언트에서 계산해 보내기 → 서버가 항상 재계산(보낸 값 무시)
- ❌ resultMessage 텍스트로 분기 → resultCode로
- ❌ `"Y"/"N"` 문자열 기대 → boolean
- ❌ ID를 number로 보관/연산 → 문자열 유지
- ❌ 전체 데이터 요청 후 클라 페이징

## 10. Swagger

- **개발서버(권장)**: `http://minoservers.iptime.org:8082/swagger-ui/index.html` — 로컬 실행 불필요
- 로컬 실행 시: `http://localhost:8080/swagger-ui/index.html`
- **구현 완료된 API만** 노출됩니다. 새 API는 구현 즉시 Swagger에 반영 + 공지.
