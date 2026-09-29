# 🔐 인증·권한 흐름 가이드 (프론트엔드)

> **대상**: 사용자/파트너/운영자 SPA + 모바일 앱 ｜ 최초 2026-06-10 · **최종 갱신 2026-06-11 (auth/account API 구현·검증 완료 — 대부분 확정됨)**
> **표기**: ✅ **확정·동작 중**(E2E 검증 완료) ｜ 🟡 **미확정**(확정 시 공지)

---

## 1. 핵심 계약 ✅ (전부 구현·검증 완료)

| 항목 | 확정 내용 |
| --- | --- |
| 인증 헤더 | `Authorization: Bearer {accessToken}` |
| access 만료 | **30분** (응답의 `accessTokenExpiresIn: 1800`) |
| refresh 만료 | **14일** (회전 방식 — 사용할 때마다 새 토큰으로 교체) |
| **refresh 전달(웹)** | **HttpOnly Secure 쿠키 `REFRESH_TOKEN`** (path=/auth, SameSite=Lax) — JS에서 접근 불가·불필요. 로그인 시 `clientType:"WEB"`(기본값) |
| **refresh 전달(앱)** | 응답 body의 `refreshToken` — **iOS Keychain / Android Keystore(EncryptedSharedPreferences)에 저장**. 로그인 시 `clientType:"APP"` |
| 토큰 저장(웹) | access는 **메모리만** (localStorage/sessionStorage 금지 — XSS). refresh는 쿠키라 저장할 것 없음 |

## 2. API 명세 ✅

### POST /auth/login
```json
// 요청 — ⚠ chnlCd 필수 (2026-06-12 변경): 자기 SPA 의 채널값을 보내세요
{ "emlAddr": "user@eeum.kr", "pswd": "...", "clientType": "WEB", "chnlCd": "USER" }
// clientType: WEB(기본)|APP ｜ chnlCd: USER(사용자앱)|PARTNER(파트너웹)|OPERATOR(운영자웹)

// 응답 200 (웹 — refreshToken은 쿠키로 내려오므로 body에 없음)
{ "resultCode": "SUCCESS", "resultMessage": "...", "result": {
    "accessToken": "eyJ...",
    "accessTokenExpiresIn": 1800,
    "user": {
      "userSn": "2", "emlAddr": "user@eeum.kr", "userNm": "홍길동",
      "userTypeCd": "MEMBER",            // MEMBER | PARTNER | OPERATOR
      "acntStts": "ACTIVE",              // ACTIVE | PENDING(파트너 승인대기)
      "permissions": ["PRTNR_APRV"]      // 권한코드 목록 (없으면 [])
    } } }
// 앱(clientType=APP)은 result에 "refreshToken" 포함
```
- 오류: `401 AUTH_INVALID_CREDENTIALS`(이메일/비번 불일치 — 미존재 계정도 동일 코드), `403 AUTH_ACCOUNT_SUSPENDED`(정지), `403 AUTH_ACCOUNT_REJECTED`(반려)
- **채널 제한(2026-06-12 신설)**: 계정에 해당 사이트 로그인 자격이 없으면(예: 일반회원이 운영자웹에서 로그인) **자격증명 오류와 똑같이 `401 AUTH_INVALID_CREDENTIALS`**가 옵니다(보안상 구분 비노출 — 확정 정책). 일반회원=USER만, 파트너=PARTNER만, 운영자=OPERATOR만이 기본이며, 전 채널 가능한 슈퍼계정은 별도 부여. 로그인 화면에 "파트너는 파트너 사이트에서 로그인하세요" 류 안내 문구를 권장.
- `permissions`에 `CHNL_LOGIN_*` 코드가 보이면 **로그인 채널 자격**입니다 — 메뉴/기능 분기에 사용하지 마세요(업무 권한 아님).

### POST /auth/refresh — 회전 방식 ✅
- **⚠ body 의 `chnlCd` 필수 (2026-06-12 변경)** — 원 로그인과 같은 채널값.
- **웹**: `{ "chnlCd": "USER" }` body + 쿠키 자동 전송(`credentials: 'include'` 필수) → 새 access + 새 refresh 쿠키
- **앱**: `{ "refreshToken": "...", "chnlCd": "USER" }` body로 → 응답 body에 새 쌍
- 응답 형태는 로그인과 동일. **사용한 refresh는 즉시 폐기됨** — 옛 토큰 재사용 시 `401 AUTH_REFRESH_INVALID`가 떨어지고 **그 사용자의 모든 세션이 회수**됩니다(탈취 의심 보호). 401 받으면 재로그인으로.
- 채널이 원 로그인과 다르면 `401 AUTH_REFRESH_INVALID` (이 경우 세션은 살아있음 — 올바른 채널로 다시 호출하면 됨).

### POST /auth/logout ✅
- 웹: 쿠키로 호출 → 세션 폐기 + 쿠키 즉시 만료. 앱: body refreshToken. 멱등(이미 폐기돼도 200).

### GET /auth/me ✅
- Bearer 토큰 기준 현재 사용자(`user` 객체와 동일 형태) — 라우트 가드/새로고침 복원용.

### POST /auth/social-login · /auth/social-signup — 소셜 로그인 (구글·카카오 ✅ 2026-06-15)
프론트(JS SDK)·앱(네이티브 SDK)이 받은 **`id_token`**을 서버에 보내면 검증 후 우리 토큰 발급. 채널은 서버가 USER 고정. **provider 만 바꾸면 동일 흐름**(구글·카카오 공통).
- 요청: `POST /auth/social-login { "provider":"GOOGLE|KAKAO", "idToken":"<provider id_token>", "clientType":"WEB|APP" }`
  - ⚠ 카카오: **이메일 미동의 가능**(비즈 앱 심사 전) → `email`/`emlAddr` 가 `null` 일 수 있음. 표시 이름은 카카오 `nickname`. 계정 식별은 이메일이 아니라 provider 사용자 ID 기준이라 무관.
- 응답 2갈래 (`result.status`):
  - `LOGIN` — 기존 연결 계정 → `result.token`(이메일 로그인과 **동일 토큰/user 형태**). 바로 로그인 완료.
  - `SIGNUP_REQUIRED` — 신규 → `result.signup = { signupToken, email, name }`. **약관 동의 화면** 후 아래 가입완료 호출.
- 가입완료: `POST /auth/social-signup { signupToken, trmsAgreList:[{trmsSn,agreYn}], clientType, ncnm? }` → 계정 생성 + 토큰(이메일 가입과 동일 응답).
  - 필수약관 전부 `agreYn:"Y"` 필요. `signupToken`은 **1회용·10분 만료**(만료/실패 시 social-login부터 다시).
  - 📌 **약관 목록·본문은 `GET /terms/list`·`GET /terms/detail?trmsSn=N`로 받습니다(trmsSn 하드코딩 금지)** — 비로그인 공개. 상세는 api-guide §8-5. (일반 `POST /accounts/insert`·파트너 `POST /partners/insert` 가입도 동일하게 `trmsAgreList` 사용)
- 이후 refresh/me/logout 은 이메일 로그인과 **완전히 동일**(소셜 = USER 채널). refresh 시 `chnlCd:"USER"`.
- ⚠ **동일 이메일 자동연결 안 함**(보안) — 이미 이메일 가입된 주소면 social-signup이 거부(`ACCOUNT_EMAIL_DUPLICATE`) → 기존 계정으로 로그인 안내.
- 실패 시 `401 AUTH_SOCIAL_TOKEN_INVALID`(토큰 위조·만료·미설정 — 단일 메시지).

### GET /auth/oauth/{provider}/authorize · /callback · POST /auth/oauth/result — 소셜 로그인 **웹 흐름** (구글·카카오·네이버 ✅ 2026-06-22)
SDK 없이 **브라우저 리다이렉트**로 하는 소셜 로그인(서버 시작 Authorization Code + PKCE). 위 `social-login`(id_token 직전달)이 **앱/JS SDK용**이라면, 이건 **웹용**이다. `provider` = `GOOGLE|KAKAO|NAVER`.

프론트가 처리하는 부분만:
1. 브라우저를 이동: `window.location = "{API}/auth/oauth/{provider}/authorize?redirectKey=default&clientType=WEB"` → 서버가 provider 로그인 페이지로 302 (state/nonce/PKCE·흐름바인딩 쿠키는 **서버가 발급**).
2. provider 로그인 완료 → 서버 콜백(`/auth/oauth/{provider}/callback`) → 서버가 **프론트 redirect URL**로 302:
   - 성공: `{프론트URL}?result=<1회용 resultToken>`
   - 실패: `{프론트URL}?error=oauth_failed`
3. 프론트 콜백 페이지가 `result` 쿼리를 읽어 교환: `POST /auth/oauth/result { "resultToken":"..." }`
   - ⚠ **반드시 `credentials:"include"`** — 흐름바인딩 쿠키(`OAUTH_FLOW`)를 동봉해야 함(로그인 CSRF 차단). 흐름을 **시작한 같은 브라우저**에서 result까지 해야 한다.
   - 응답 2갈래: `LOGIN_SUCCESS` → `result.token`(이메일 로그인과 동일, 웹은 refresh 쿠키 발급) / `SIGNUP_REQUIRED` → `result.signup={ signupToken, email, name }` → 약관 동의 후 `POST /auth/social-signup`(위와 동일).
- **redirectKey**: 서버 allowlist에 등록된 프론트 콜백 URL의 키. ⚠ **프론트 도메인 확정 시 백엔드 설정에 등록 요청 바람**(현재 `default` = `http://localhost:5173/oauth/callback` 임시값).
- **네이버**: 레거시 OAuth2(id_token 없음) → **이 웹 흐름 전용**(`/auth/social-login`엔 네이버 없음). `emailVerified` 항상 false, email/name 미동의 시 누락 가능 → 그땐 `SIGNUP_REQUIRED`로 가입폼 보강.
- 실패/만료/state·흐름바인딩 불일치 → 콜백 `?error=oauth_failed` 또는 result `410/400 OAUTH_RESULT_INVALID`. resultToken은 **1회용·짧은 TTL**.
- ⚠ **구글 dev 제약**: 구글 OAuth는 redirect URI에 `https` 또는 `http://localhost`만 허용 → dev(`http://minoservers...`) 불가. 구글 웹 흐름은 **운영 HTTPS 후**(또는 로컬). 네이버·카카오는 http 공개주소 허용돼 dev 검증 완료.
- 나머지(동일 이메일 자동연결 금지·소셜=USER 채널 등)는 위 `social-login`과 동일.

## 3. 401 처리 규칙 ✅ (기존과 동일 — 루프 방지)

```
401 수신 시:
1. /auth/login·/auth/refresh 자신의 401 → refresh 시도 금지, "로그인 필요" 처리
2. 그 외 → refresh 1회만 시도 (동시 다발 401은 큐로 묶어 refresh 한 번만)
3. 성공 → 대기 요청 재시도 / 실패 → 토큰 폐기 + 로그인 화면
```
- 403 ≠ 로그인 이동 (권한 없음 안내).

## 4. 권한(메뉴/라우트 분기) ✅ 확정

- `user.permissions`(권한코드 문자열 배열)가 정식 스키마 — §4의 임시 스키마가 그대로 확정됐습니다. `hasPermission(code)` 추상화 유지 권장.
- **동적 메뉴 API**: `GET /menus/list?menuChnlCd=USER|PARTNER|OPERATOR` ✅
  - 비로그인 호출 가능(공개 메뉴만), Bearer 첨부 시 권한 보유 메뉴까지.
  - 응답 = **평면 목록(전량, 페이징 없음)**: `[{ menuSn, upperMenuSn(null=최상위), menuNm, urlAddr(null=폴더형), iconNm, sortSeq }]` — 트리 조립은 프론트.
  - 부모가 비노출이면 자식도 서버에서 제거되어 내려옴(고아 없음).
- 메뉴 숨김은 UX일 뿐, 최종 차단은 서버(403) — 변함없음.
- **도메인 권한코드(현재 구현)**: `PRTNR_APRV`(파트너 승인)·`PRTNR_VIEW`(파트너 조회)·`ACNT_MNG`(계정관리)·`INQR_ANS`(문의 답변)·`INQR_MODER`(문의 게시관리/블라인드). 예: 운영자 웹에서 문의 **답변 UI는 `INQR_ANS`**, **블라인드 UI는 `INQR_MODER`** 보유 시에만 노출. (`CHNL_LOGIN_*`은 권한 아님 — §2 참고)

## 5. SPA별 동작 ✅

| SPA | 확정 동작 |
| --- | --- |
| 사용자 | 가입(`POST /accounts/insert`) 직후 ACTIVE·바로 로그인 가능 (전화인증은 포트원 연동 시 추가 예정) |
| 파트너 | 가입(`POST /partners/insert`) 후 **acntStts=PENDING으로 로그인 가능** — `user.acntStts==="PENDING"`이면 승인대기 화면으로 분기. 권한이 없어 보호 API는 403. 운영자 승인 후 ACTIVE |
| 운영자 | 권한코드(`PRTNR_APRV` 등) 보유 — 파트너 승인 화면(/partners/list·detail·approve·reject). **2차 인증(MFA)은 🟡**(흐름 확정 후 추가 — 현재는 일반 로그인) |

## 6. 남은 🟡 (확정 시 공지)

| 항목 | 상태 |
| --- | --- |
| 소셜 로그인 | **구글·카카오·네이버 ✅ (2026-06-22)** — 앱/SDK는 §social-login(id_token, 구글·카카오). 웹은 §oauth 웹 흐름(authorize/callback/result, 구글·카카오·네이버). 네이버=웹 전용. 구글 웹은 운영 HTTPS 후. 애플은 추후 |
| 운영자 2차 인증(MFA) | 흐름 미확정 — 중간 화면 전제만 유지 |
| 전화번호 본인인증(포트원) | 계약 후 — 가입 흐름에 인증 단계 추가 예정 |
| 비밀번호 변경/재설정 | 토큰 무효화 정책과 함께 확정 예정 |

> ❗ 변함없는 금지: localStorage 토큰 저장, resultMessage 텍스트 분기, 응답 스키마 하드코딩 대신 위 명세 사용.
