# SNS 로그인(구글·카카오·네이버·애플) 연동 전략 (확정 보류 — 착수 시 이 문서 기준)

> 작성: 2026-06-12. codex 교차검토 완료(공식 문서 근거: RFC 9700, OIDC Core, Google/Kakao/Naver/Apple 공식 문서).
> 상태: **보류** — 사용자 지시로 저장만 해두고 추후 재개. 재개 시 이 문서 + 하단 "착수 전 결정 필요" 부터.

## 1. 전체 그림

소셜 로그인 = 비밀번호 확인을 provider(구글 등)가 대신하고, 백엔드는 그 결과(id_token)만 검증해 **자체 JWT 발급**. 로그인 성공 이후는 기존 이메일 로그인과 동일(access 30분 + refresh 14일 Redis 회전, chnlCd 검사, LOGIN_LOG_INFO 기록).

| 구분 | 웹(SPA) | 모바일 앱(AOS/iOS) |
|---|---|---|
| 방식 | Authorization Code + **PKCE(S256)** — 백엔드가 callback 수신, 코드를 id_token 으로 교환 | 네이티브 SDK 가 받은 id_token 을 `POST /auth/social-login` 으로 전달 |
| 백엔드 | state/nonce/code_verifier 를 Redis 5~10분 TTL·1회용으로 관리, 코드 교환+검증 | 전달받은 id_token 검증 |
| 공통 | 검증 통과 → 자체 JWT 발급 (기존 로그인 응답 동일) | |

- **4사 모두 OIDC id_token 경로 지원** (카카오·네이버 포함 — codex 확인). id_token 검증을 1차 인증 근거로 통일, 이메일/이름 보강 필요 시에만 userinfo 호출.
- 명칭 주의: 내부 문서에 "하이브리드"라 쓰지 말 것(OIDC Hybrid Flow 와 혼동) — "웹=Authorization Code, 모바일=네이티브 토큰 검증"으로 표기.
- 구조: `SocialProvider` 인터페이스 + 4사 구현체 — SSO 분리(타 프로젝트 재사용) 대비.
- 웹 로그인 완료 시 JWT 를 query string 에 싣지 말 것 — HttpOnly 쿠키 또는 1회성 login ticket.

## 2. 계정 정책 (codex 가 업계 표준으로 검증)

1. **동일 이메일 자동 연결 금지** — 이메일은 본인 증명이 아님(계정 탈취 통로). 연결은 로그인 상태에서 명시적 연동 API 로만(2단계). 카카오 공식 문서도 동일 취지 명시.
2. **신규 가입 순서**: 소셜 인증 성공 → pending signup 발급(계정 미생성) → 약관 동의 → USER_ACNT/USER_SOCIAL_ACNT 생성 → JWT 발급. 약관 전 활성 계정 생성 금지.
3. **소셜은 USER 채널 전용** — 파트너·운영자는 이메일만.
4. **계정 키 = provider user id** (이메일은 없거나 변경 가능). USER_SOCIAL_ACNT 유니크는 `PRVDR_CD + PROVIDER_USER_ID` 복합키 권장.

## 3. 사전 준비물 (사용자 발급 — 상세 절차는 §7)

| | 어디서 | 받을 것 | 특이사항 |
|---|---|---|---|
| 구글 | GCP 콘솔 | OAuth Client ID/Secret (웹·Android·iOS 각각) | 동의화면에 개인정보처리방침 URL 필요 |
| 카카오 | Kakao Developers | REST API 키 + Client Secret | **이메일 동의항목=비즈 앱 전환+심사(영업일 3~5일+) — 최우선 신청 권장**. OIDC ON |
| 네이버 | NAVER Developers | Client ID/Secret | Callback URL 개발/운영 분리 등록. OIDC 경로는 `/oauth2/*`(일반 OAuth 는 `/oauth2.0/*`) |
| 애플 | Apple Developer | **유료 멤버십($99/년)** + Services ID + `.p8` 키(Key ID, Team ID) | redirect **HTTPS 필수**(개발서버 http 라 HTTPS 도메인 전까지 개발 불가). iOS 에서 타사 소셜 제공 시 애플 로그인 의무(App Review 4.8) |

callback 경로(안): `/auth/oauth/{provider}/callback` — 개발 `http://minoservers.iptime.org:8082/...` + 로컬 `http://localhost:8082/...`, 운영(추후 https) 두 벌 등록.

## 4. 단계 분할

**MVP (1단계 — 구글·카카오·네이버)**
- PRVDR_CD 공통코드 NAVER 추가 (DB 변경 — 새 V 마이그레이션, 데이터 1행)
- SocialProvider 인터페이스 + 3사 구현
- 웹용 authorize/callback + 모바일용 `POST /auth/social-login`
- pending signup + 약관 동의 완료 API
- 기존 JWT·refresh 회전·chnlCd=USER 검사·로그인 이력 통합
- provider 토큰 DB 저장 없음 / 동일 이메일 자동 연결 없음

**2단계**
- 애플 구현 (유료 멤버십+HTTPS 후)
- 명시적 계정 연동 API (로그인+재인증 후 연결)
- 연동 해제 API (소셜 전용 계정은 해제 전 비밀번호 설정 강제 — 로그인 수단 0개 방지)
- Kakao 연결끊기 웹훅, Apple 계정삭제·토큰 revoke 대응 검토

## 5. 보안 필수사항

- state/nonce/code_verifier: 요청별 난수, Redis TTL, 1회 사용. PKCE 는 S256.
- redirect URI exact match, provider 별 callback 분리. returnUrl 은 내부 allowlist 만.
- id_token 검증: 서명/JWKS, iss, aud, exp, iat, nonce, 필요 시 azp.
- userinfo 사용 시 id_token 의 sub 와 userinfo sub/id 정합 확인.
- 클라이언트가 보낸 email/name/providerUserId 절대 신뢰 금지(검증된 토큰에서만 추출).
- provider code/access_token/id_token/client_secret 은 로그·DB 에 남기지 않음.
- 로그인 실패 이력: provider·chnlCd·실패 사유는 기록, 토큰/원문 PII 마스킹.

## 6. 함정

- 애플은 이름을 **최초 승인 1회만** 제공(놓치면 재수신 불가), 이메일은 가림 주소(`privaterelay.appleid.com`) 가능 — relay 발신 도메인 등록 검토.
- `LGN_TYPE(EMAIL|SOCIAL)` 은 "가입 경로" vs "로그인 수단" 의미 혼재 가능 — 명시 연동(2단계) 때 의미 확정 필요.
- 카카오 이메일 동의항목 심사가 일정 병목 가능.
- dev http 환경: 구글·카카오·네이버는 등록 가능하나 provider 별 제약 상이, 애플은 HTTPS dev 도메인 별도 일정.

## 7. 발급 절차 상세 (사용자 안내용)

### 7-1. 구글 (console.cloud.google.com)
1. GCP 프로젝트 생성(예: "Eeum") → "API 및 서비스 > OAuth 동의 화면": 외부(External), 앱 이름·지원 이메일·개인정보처리방침 URL 입력.
2. "사용자 인증 정보 > OAuth 클라이언트 ID 만들기"를 **3개**:
   - 웹 애플리케이션: 승인된 리디렉션 URI 에 `http://minoservers.iptime.org:8082/auth/oauth/google/callback`, `http://localhost:8082/auth/oauth/google/callback` → **Client ID + Client Secret** 수령(백엔드용).
   - Android: 패키지명 + SHA-1/SHA-256 서명 지문(앱 개발 시).
   - iOS: 번들 ID(앱 개발 시).
3. 백엔드에 전달: 웹 Client ID/Secret + (앱 생기면) Android/iOS Client ID — 허용 client_id 목록 검증용. scope 는 `openid email profile`.

### 7-2. 카카오 (developers.kakao.com)
1. "내 애플리케이션 > 애플리케이션 추가" → **REST API 키** 확인.
2. "카카오 로그인 ON" + Redirect URI 등록(위와 동일 패턴 `/auth/oauth/kakao/callback`).
3. "보안 > Client Secret" 발급·활성화.
4. "카카오 로그인 > OpenID Connect ON".
5. "동의항목": 닉네임 등 기본 + **카카오계정(이메일)은 비즈 앱 전환 필요** — 사업자 정보 등록 후 심사(영업일 3~5일+). **가장 먼저 신청해 둘 것.**
6. 플랫폼 등록: Web 도메인 + (앱 개발 시) Android 패키지/키해시, iOS 번들 ID.
7. 백엔드에 전달: REST API 키 + Client Secret.

### 7-3. 네이버 (developers.naver.com)
1. "Application > 애플리케이션 등록": 이름, 사용 API = "네이버 로그인", 제공 정보(이메일·이름 등) 선택.
2. 서비스 URL + **Callback URL** 등록(개발·로컬·운영 분리, `/auth/oauth/naver/callback`).
3. 발급된 **Client ID + Client Secret** 백엔드에 전달.
4. 참고: 프로필 응답의 `response.id` 가 앱별 유니크 식별자(네이버 아이디 자체는 미제공). OIDC 사용 시 엔드포인트는 `/oauth2/authorize`, `/oauth2/token`, scope=openid.

### 7-4. 애플 (developer.apple.com) — 2단계
1. **Apple Developer Program 가입($99/년)**.
2. App ID 에 "Sign in with Apple" capability 활성화.
3. 웹용 **Services ID** 생성 + return URL/도메인 등록(**HTTPS 만 가능**).
4. "Keys" 에서 Sign in with Apple 용 **`.p8` 개인키** 생성 → Key ID·Team ID 와 함께 백엔드 전달(client secret JWT 를 백엔드가 이 키로 서명·생성).

### 키 전달 방법 (공통)
- Client Secret·.p8 등 비밀값은 **채팅·repo 에 올리지 말고** `.env.develop`(gitignore 됨)에 직접 기입하거나 파일로 전달 → 백엔드는 환경변수로만 주입.

## 착수 전 결정 필요 (재개 시 사용자 확인)

1. MVP=구글·카카오·네이버 / 애플·연동관리=2단계 분할 확정 여부
2. 착수 시점(상품 도메인과의 순서) — 키 발급(특히 카카오 심사)은 선행 가능
3. 카카오 비즈 앱 전환(사업자 정보 심사) 진행 여부
4. 애플 유료 멤버십 결제 시점
5. callback 경로 확정(`/auth/oauth/{provider}/callback` 안)
