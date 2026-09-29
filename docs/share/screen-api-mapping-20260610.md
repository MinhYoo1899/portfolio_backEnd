# 🗺 화면-API 매핑 (SPA 3종 × 기획서 메뉴 기준)

> **기준**: 요구사항정의서 V1.0 메뉴 구조도 ｜ 최초 2026-06-10 · **최종 갱신 2026-06-11** (auth/account/partner/menu API 구현 반영)
> **최종 갱신 2026-06-16** (파일첨부·문의 게시판 API 구현 반영)
> **최종 갱신 2026-06-25** (상품(고택/객실/편의시설/이미지 — 파트너 자기관리 HOUSE_MNG)·재고/요금 캘린더·**가용성 조회** API 반영. ⚠ 손님용 공개 고택 브라우즈는 아직 미구현)
> **최종 갱신 2026-06-28** (운영자 상품관리 정정 — 운영자는 **HOUSE_PROXY_MNG**로 파트너 대신 고택/객실을 대행 등록·수정·삭제·상태·정책·결제수단·이미지까지 설정 가능. 과거 "운영자는 조회+강제 게시중지만" 설명을 현재 구현 기준으로 수정. + 편의시설 카탈로그 한옥 재정비/커스텀)
> 상태: ✅ **구현·검증 완료(Swagger 노출 중)** / 🔜 다음 개발 / 📋 예정(V3~V8)

---

## 0. 공통 (전 SPA)

| 화면 | API | 상태 |
| --- | --- | --- |
| 동적 메뉴(사이드바/헤더) | `GET /menus/list?menuChnlCd=USER\|PARTNER\|OPERATOR` — 비로그인 가능, 토큰 있으면 권한 반영. 평면 목록(트리 조립은 프론트) | ✅ |
| 인증 공통 | `POST /auth/login` · `POST /auth/refresh`(회전) · `POST /auth/logout` · `GET /auth/me` | ✅ |

## 1. 사용자 앱/웹 (일반회원)

| 화면 | API 후보 (초안) | 상태 |
| --- | --- | --- |
| 회원가입(일반) | `POST /accounts/insert` (약관동의 포함 — 전화번호 인증은 포트원 연동 시 추가) | ✅ |
| 회원가입/로그인(소셜) | 앱/SDK: `POST /auth/social-login`(구글·카카오) ｜ 웹: `GET /auth/oauth/{provider}/authorize`→`/callback`→`POST /auth/oauth/result`(구글·카카오·네이버) → 신규면 `POST /auth/social-signup` | ✅ 2026-06-22 (구글 웹은 운영 HTTPS 후·애플 추후) |
| 로그인/로그아웃 | `POST /auth/login`, `POST /auth/refresh`, `POST /auth/logout`, `GET /auth/me` | ✅ |
| 메인(추천 고택/슬라이더) | (손님용 공개 API 미구현) `GET /houses/*`는 **파트너 전용**(HOUSE_MNG) — 손님 브라우즈 불가 | 📋 손님용 공개 API 설계 예정 |
| 검색(목록/지도/조건) | (손님용 공개 API 미구현 — 위와 동일) | 📋 손님용 공개 API 설계 예정 |
| 상품 상세(고택/객실) | (손님용 공개 API 미구현 — 위와 동일) | 📋 손님용 공개 API 설계 예정 |
| 가용성 조회(기간·인원→가용객실·가격) | `GET /availability?houseSn=&checkInDate=&checkOutDate=&guestCount=` — **로그인**, ACTIVE만. 가이드 §8-6 | ✅ 2026-06-25 (단 `houseSn` 보유 전제 — 손님 브라우즈는 별도) |
| 예약하기 | `POST /reservations/hold` → `POST /reservations/insert` | 📋 V5 |
| 결제 | `POST /payments/ready` → 토스 결제창 → `POST /payments/approve` | 📋 V5 |
| 마이페이지-나의예약 | `GET /reservations/list?my=Y&sttsCd=` | 📋 V5 |
| 예약 취소/환불 | `POST /reservations/cancel` | 📋 V6 |
| 찜 | `POST /favorites/insert`, `POST /favorites/delete`, `GET /favorites/list` | 📋 V8 |
| 후기 | `POST /reviews/insert`, `GET /reviews/list?houseSn=` | 📋 V8 |
| 쿠폰함 | `GET /coupons/list?my=Y` | 📋 V8 |
| 문의(게시판형 Q&A) | `GET /inquiries/list`(오프셋/커서), `GET /inquiries/detail`, `POST /inquiries/insert`, `POST /inquiries/update`, `POST /inquiries/delete` — 비밀글 마스킹·답변 전 수정 | ✅ |
| 알림 리스트 | `GET /notifications/list` | 📋 V8 |
| 즐길거리 | `GET /attractions/list?regionCd=`, `GET /attractions/detail` | 📋 V8 |
| 프로필 수정 | `GET /accounts/detail`(토큰 기준 본인), `POST /accounts/update` | ✅ |
| 회원 탈퇴 | `POST /accounts/delete` (본인확인 비밀번호 필수) | ✅ |

## 2. 파트너 웹 (고택 사업자)

| 화면 | API 후보 (초안) | 상태 |
| --- | --- | --- |
| 파트너 가입(사업자) | `POST /partners/insert` — 사업자번호 형식 검증(공공API 실검증은 추후) | ✅ |
| 로그인 | `POST /auth/login` — **승인 전(PENDING)도 로그인 가능**, `user.acntStts==="PENDING"`이면 승인대기 화면 분기 (확정 정책) | ✅ |
| 대시보드(예약요약/신규예약) | `GET /reservations/list?partner=Y&...`, 요약 `GET /partners/dashboard` | 📋 V5 |
| 예약 캘린더/리스트 | `GET /reservations/list?roomSn=&fromYmd=&toYmd=` | 📋 V5 |
| 예약 수기 등록(전화예약) | `POST /reservations/insert` (channel=PHONE) | 📋 V5 |
| 예약 상태 처리(확정/취소) | `POST /reservations/change-status` | 📋 V5 |
| 객실 현황(일별 투숙) | `GET /reservations/list?date=` | 📋 V5 |
| 고택/객실 등록·관리 | `POST /houses/insert·update·delete·status`, `GET /houses/list·detail`, `POST /rooms/insert·…`, 편의시설 `GET/POST /houses/amenities·set` + `GET /amenities`, 이미지 `/houses/images`·`/rooms/images` — **전부 파트너 본인 소유**(HOUSE_MNG) | ✅ 2026-06-24 (Swagger 노출) |
| 재고/요금 캘린더 | `POST /rooms/calendar/set`(기간+요일별 재고·가격 일괄), `GET /rooms/calendar` — 파트너 본인 소유 | ✅ 2026-06-24 (개발서버 검증 2026-06-25) |
| (문의 답변은 **운영자 권한으로 일원화** — 운영자 웹 §3 참고. 파트너 문의관리 도입은 미정) | — | 📋 미정 |
| 정산 리스트/통계 | `GET /settlements/list?partner=Y` | 📋 V7 |

## 3. 운영자 웹 (운영자)

| 화면 | API 후보 (초안) | 상태 |
| --- | --- | --- |
| 로그인 | `POST /auth/login` ✅ — 2차 인증(MFA)은 흐름 확정 후 📋 | ✅/📋 |
| 대시보드(예약/결제 현황) | `GET /master/dashboard` | 📋 V5+ |
| 파트너 관리(승인/반려) | `GET /partners/list?aprvStts=`, `GET /partners/detail?prtnrSn=`, `POST /partners/approve`, `POST /partners/reject` — 권한코드 `PRTNR_APRV` 필요 | ✅ |
| 상품 관리(운영자) | **조회**(HOUSE_VIEW, 소유권 무관) `GET /operator/houses`(목록)·`/operator/houses/detail`·`/operator/rooms/list·detail`. **대행 등록·수정·삭제·상태·정책·결제수단**(HOUSE_PROXY_MNG, 소유 파트너 서버해석) `POST /operator/houses/insert·update·delete·status·policy·pay-methods/set` + 객실 `/operator/rooms/insert·update·delete·status·beddings/options/climate/calendar·set` + 이미지 `/operator/houses/images(·delete·representative)`·`/operator/rooms/images(·delete·representative)`. **강제 게시중지/해제**(HOUSE_MODER) `POST /operator/houses/suspend·release` | ✅ 2026-06-28 |
| 회원 관리 | `GET /accounts/list?userTypeCd=`, `GET /accounts/detail?userSn=` | 🔜 V2 |
| 문의 관리(답변/블라인드) | `GET /inquiries/list`, `GET /inquiries/detail`, `POST /inquiries/answers/insert\|update\|delete`, `POST /inquiries/blind\|unblind` — 권한 `INQR_ANS`(답변)·`INQR_MODER`(게시관리) | ✅ |
| 정산 관리(파트너별) | `GET /settlements/list`, `POST /settlements/confirm` | 📋 V7 |
| 쿠폰 관리 | `POST /coupons/insert`, `GET /coupons/list` | 📋 V8 |
| DM/공지 관리 | `POST /campaigns/insert`, `POST /notices/insert` | 📋 V8 |
| **권한 관리** (역할/권한 배정) | `GET /roles/list`, `GET /authorities/list`, `POST /accounts/assign-role` | ✅ V1 테이블 / 🔜 API V2 |
| 약관 관리 | `GET /terms/list`, `POST /terms/insert` | 🔜 V2 |

---

## 협업 방법
- 이 표는 **초안** — 화면 기획이 구체화되면 프론트 각 팀이 "이 화면에 이 데이터가 필요하다"를 코멘트로 달아주세요. 백엔드가 응답 DTO에 반영합니다.
- 구현 완료된 API는 ✅로 갱신 + Swagger에 자동 노출 + 변경 공지.
- **프론트 블로커가 되는 화면**(데모/시연 일정상 급한 것)이 있으면 우선순위 조정 요청 주세요.
