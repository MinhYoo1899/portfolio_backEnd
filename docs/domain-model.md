# 도메인 모델 — '이음' 고택(한옥)숙박 예약 서비스

> 공식 요구사항정의서 V1.0(파랑새소프트/큐어) + codex 설계 검토 기반. 백엔드(Eeum_back) API 관점.
> 표준 규약은 [architecture.md](./architecture.md), 미결정/협의 항목은 [decisions-pending.md](./decisions-pending.md).
> ⚠ **표기는 논리(개념) 명칭입니다** — 미구현 엔터티 다수 포함. **실제 물리 테이블·컬럼명은 [db-schema-current.md](./db-schema-current.md)를 우선**하세요(예: `USER_ACCOUNT`→`USER_ACNT`, `COMMON_CODE`→`COMM_CD`, `PERMISSION`→`AUTH_MST`).

---

## 0. 서비스 개요

'이음'은 **고택(한옥) 숙박 예약 플랫폼**이다. 3개 클라이언트를 단일 백엔드가 서빙한다.

| 역할 | 클라이언트 | 설명 |
| --- | --- | --- |
| **사용자(회원)** | 앱(AOS/iOS) + 웹 | 고택 검색·예약·결제·찜·후기·문의 |
| **파트너(고택 관리자/공급자)** | 웹 | 사업자. **운영자 승인 후** 활성. 고택·객실·요금·예약·정산 관리 |
| **운영자(큐어 운영자)** | 웹 | 직원. 2차 인증. 파트너·상품·회원·정산·마케팅·시스템 관리 |

상품 계층: **파트너 → 고택(House) → 객실(Room) → 예약 → 결제 → 정산**

---

## 1. 모듈 구조 (도메인별, egovframework.eeum)

> **원칙(codex)**: 역할별로 패키지를 쪼개지 않는다. 예약·결제·정산·객실은 3역할이 공유하는 도메인이므로
> 상태머신·금액계산을 **service에서 단일화**하고, 역할 차이는 **controller 분리 + RBAC 권한코드 + SQL 데이터 스코프(partnerId/userId)** 로 흡수한다.

```
egovframework.eeum
├─ base/          공통 토대(response·exception·security·audit) — 상세는 conventions.md §0
├─ auth/          로그인·refresh·logout·소셜·전화/2차 인증
├─ account/       USER_ACCOUNT + 프로필(user/partner/master) + 소셜계정 + 약관동의
├─ rbac/          ROLE_MST·PERMISSION·USER_ROLE·ROLE_PERMISSION
├─ partner/       파트너 사업자 정보·승인 워크플로우
├─ house/         고택
├─ room/          객실 + 비품·시설·옵션·정책·이미지
├─ rate/          요금제·요금 캘린더
├─ inventory/     재고(객실 가용성) 캘린더·휴무
├─ reservation/   예약 + 가격 스냅샷·게스트·상태이력  (web: User/Partner/Operator 컨트롤러)
├─ payment/       결제(토스) + 결제이벤트(웹훅)
├─ refund/        환불·위약금
├─ settlement/    정산(파트너별)·정산항목·정책
├─ coupon/        쿠폰·발급
├─ point/         포인트 원장(ledger)
├─ review/        후기(별점)
├─ favorite/      찜(고택/즐길거리)
├─ inquiry/       문의(채팅 thread/message)
├─ notification/  알림(알림톡/푸시) 발송·이력
├─ content/       notice(공지)·banner·event·attraction(즐길거리)
├─ marketing/     DM 발송·통계 연계
├─ statistics/    대시보드·KPI 통계
├─ terms/         약관·동의 이력
├─ system/        권한관리·코드·정책 설정
├─ code/          COMMON_CODE_GROUP·COMMON_CODE
└─ external/      toss·portone·kakaomap·business(사업자검증)·alimtalk  (PG는 PaymentGateway 추상화)
```

각 모듈 내부(2026-06-12 확정 표준 — `docs/conventions.md` §0): `web/(컨트롤러들+dto) / vo / exception / service/{이름}/(dao·impl)`.
역할별 컨트롤러 예: `reservation/web/{User,Partner,Operator}ReservationController` — service는 공유.

---

## 2. 계정 / 인증 (account, auth, rbac)

단일 `USER_ACCOUNT` + `USER_TYPE`(USER/PARTNER/OPERATOR) + **데이터 주도 RBAC**. 역할별 상세는 분리 테이블.

```
USER_ACCOUNT      USER_ID PK, EMAIL, PASSWORD_HASH(소셜전용은 NULL), USER_TYPE,
                  LOGIN_TYPE(EMAIL|SOCIAL|MIXED), ACCOUNT_STATUS(PENDING|ACTIVE|REJECTED|SUSPENDED|WITHDRAWN),
                  PHONE_NO, PHONE_VERIFIED_YN, NICKNAME, USER_NAME, LAST_LOGIN_AT, WITHDRAWN_AT, + audit
USER_SOCIAL_ACCOUNT  ID PK, USER_ID FK, PROVIDER(GOOGLE|KAKAO|APPLE), PROVIDER_USER_ID,
                     EMAIL, LINKED_AT, UNIQUE(PROVIDER, PROVIDER_USER_ID)
USER_PROFILE      USER_ID PK/FK, 프로필이미지, 마케팅수신여부 등 일반회원 전용
MASTER_PROFILE    USER_ID PK/FK, 부서, 직급, MFA 설정
MASTER_MFA_CHALLENGE  ID PK, USER_ID, METHOD(SMS), STATUS(REQUESTED|VERIFIED|EXPIRED|FAILED), EXPIRES_AT

ROLE_MST / PERMISSION / USER_ROLE / ROLE_PERMISSION   (역할명·권한 모두 데이터. 코드 하드코딩 금지)
```

- **파트너 승인 워크플로우**: 가입 → `ACCOUNT_STATUS=PENDING` + `PARTNER_BUSINESS.APPROVAL_STATUS=REQUESTED`
  → 사업자검증(공공API) `VERIFIED` → 운영자 승인 → `ACTIVE`/`APPROVED`(승인자·일시 기록) / 반려 시 사유 기록.
  로그인 성공 후에도 **`ACCOUNT_STATUS=ACTIVE` AND `APPROVAL_STATUS=APPROVED`** 동시 확인.
- **운영자 2차 인증**: 1차 이메일+비번 → 2차 모바일 인증(MFA_CHALLENGE) 통과해야 토큰 발급.
- **JWT 클레임**: `userId, userType, tokenVersion, permissionCodes`(운영자는 권한 변동 잦으니 짧은 TTL 캐시 또는 서버조회).
- `USER_TYPE=OPERATOR`라도 자동 전권 아님 — **권한코드 기반 RBAC**가 기준.

---

## 3. 상품: 파트너 → 고택 → 객실 (partner, house, room)

```
PARTNER_BUSINESS  PARTNER_ID PK, USER_ID FK, BUSINESS_NAME, BUSINESS_NO(중복검증),
                  BUSINESS_VERIFY_STATUS, BUSINESS_ADDRESS, MANAGER_NAME, MANAGER_PHONE, COMPANY_EMAIL,
                  CATEGORY_CODE, APPROVAL_STATUS(REQUESTED|APPROVED|REJECTED), APPROVED_BY/AT, REJECT_REASON, + audit
                  (첨부: 사업자등록증·숙박업인허가증 → FILE_ATTACHMENT)
HOUSE             HOUSE_ID PK, PARTNER_ID FK, HOUSE_NAME, HOUSE_STATUS(DRAFT|REQUESTED|ACTIVE|HIDDEN|SUSPENDED),
                  REGION_CODE, ADDRESS, LATITUDE, LONGITUDE, DESCRIPTION(TEXT), CHECKIN_GUIDE, SAFETY_GUIDE, + audit
ROOM              ROOM_ID PK, HOUSE_ID FK, ROOM_NAME, ROOM_STATUS, AREA_M2, AREA_PYEONG,
                  STANDARD_CAPACITY, MAX_CAPACITY, BEDDING_COUNT, ROOM_COUNT, BATHROOM_COUNT,
                  MAX_STAY_NIGHTS, NO_SMOKING_YN, DETAIL_DESCRIPTION(TEXT), + audit
HOUSE_IMAGE / ROOM_IMAGE   ID, (HOUSE_ID|ROOM_ID), FILE_ID, SORT_ORDER
ROOM_AMENITY      ROOM_ID, AMENITY_CODE   (공통코드: 주방/가전/욕실/기타)
ROOM_FACILITY     ROOM_ID, FACILITY_CODE  (공통코드: 테라스/바비큐/전망 등)
ROOM_OPTION       ID, ROOM_ID, OPTION_NAME, OPTION_AMOUNT, ACTIVE_YN   (추가옵션)
ROOM_OPERATION_POLICY  ROOM_ID, CHECKIN_TIME, CHECKOUT_TIME, LATE_CHECKOUT_TIME
ROOM_POLICY       ROOM_ID, USE_RULE(TEXT), CHECKIN_GUIDE(TEXT), SAFETY_RULE(TEXT)  (정책·안내 에디터)
```

> **비품/시설 모델링(codex)**: 검색·필터·통계에 쓰이는 값은 **공통코드 + 매핑테이블**(ROOM_AMENITY/FACILITY),
> 표현용 긴 텍스트(시설 상세설명·이용수칙)는 **TEXT 컬럼**. JSON은 MVP에서 제한적으로만.

---

## 4. 요금 / 재고 / 휴무 (rate, inventory)

> **캘린더 전개 방식(codex)**: 운영자가 요금/휴무를 바꾸면 미래 N개월 **날짜별 캘린더를 생성/갱신**하고,
> 검색·예약은 캘린더 테이블만 조회 → SQL 단순·성능 안정.

```
ROOM_RATE_PLAN        RATE_PLAN_ID PK, ROOM_ID, BASE_WEEKDAY_AMOUNT, BASE_WEEKEND_AMOUNT, PEAK_SEASON_AMOUNT,
                      ADULT_EXTRA_AMOUNT, CHILD_EXTRA_AMOUNT, INFANT_EXTRA_AMOUNT, ACTIVE_YN
ROOM_RATE_CALENDAR    ROOM_ID, RATE_DATE, RATE_PLAN_ID, SALE_AMOUNT, EXTRA_ADULT/CHILD/INFANT_AMOUNT,
                      RATE_TYPE(WEEKDAY|WEEKEND|PEAK|SPECIAL), OVERRIDE_YN, CLOSED_YN, PK(ROOM_ID, RATE_DATE)
ROOM_INVENTORY_CALENDAR  ROOM_ID, INVENTORY_DATE, TOTAL_COUNT, SOLD_COUNT, AVAILABLE_COUNT,
                      INVENTORY_STATUS(OPEN|CLOSED|SOLD_OUT|MAINTENANCE), PK(ROOM_ID, INVENTORY_DATE)
ROOM_CLOSED_RULE      ROOM_ID, RULE_TYPE(WEEKLY|MONTHLY_NTH_WEEKDAY|HOLIDAY|TEMPORARY), DAY_OF_WEEK,
                      WEEK_OF_MONTH, START_DATE, END_DATE, ACTIVE_YN     (규칙)
ROOM_CLOSED_DAY       ROOM_ID, CLOSED_DATE, CLOSED_REASON, SOURCE_TYPE(RULE|MANUAL|MAINTENANCE|LEGAL_HOLIDAY)  (결과)
```

요금 계산 순서(고정): ① 날짜별 기본요금 합산 → ② 인원추가요금 → ③ 옵션 → ④ 쿠폰할인 → ⑤ 포인트 → ⑥ 최종결제금액.
요금 우선순위: **수동 날짜 override > 임시휴무 > 성수기 > 주말/평일 기본가**.

> ⚠ **재고 단위 확정 필요**: 고택은 보통 "객실 실물 1개" → `ROOM_ID+DATE` 1실 재고 모델이 단순. 큐어 확인.

---

## 5. 예약 ↔ 결제 ↔ 환불 ↔ 정산 (핵심 트랜잭션)

### 예약 상태머신
```
HOLD → PAYMENT_READY → RESERVED → (USED) → SETTLED
                         ↘ CANCEL_REQUESTED → CANCELLED → REFUNDED / PARTIAL_REFUNDED
                         ↘ NO_SHOW
```

### 예약 생성 흐름 (중복예약 방지 + 정합성)
```
1. Redis SET NX 로 박별 임시점유  reservation:hold:{roomId}:{yyyyMMdd}  (TTL 10~15분, 다박은 전 날짜)
2. RESERVATION 생성(HOLD) + RESERVATION_PRICE 가격 스냅샷 저장
3. 토스 orderId 생성 + PAYMENT 생성(READY)
4. 결제 승인 콜백/웹훅 → DB 트랜잭션:
   토스 승인검증 → PAYMENT=APPROVED → RESERVATION=RESERVED
   → INVENTORY_CALENDAR.SOLD_COUNT 증가(재고 차감, 낙관락/유니크) → Redis hold 삭제
```
> Redis hold는 보조장치. **최종 정합성은 DB 트랜잭션 + 재고 유니크/낙관락**으로 보장.

### 테이블
```
RESERVATION         RESERVATION_ID PK, RESERVATION_NO UK, USER_ID FK NULL(전화/수기예약), PARTNER_ID, HOUSE_ID, ROOM_ID,
                    CHECKIN_DATE, CHECKOUT_DATE, NIGHT_COUNT, ADULT/CHILD/INFANT_COUNT,
                    RESERVATION_CHANNEL(EEUM|PHONE|NAVER|KAKAO|OTA), RESERVATION_TYPE(NORMAL|MANUAL),
                    RESERVATION_STATUS, PAYMENT_STATUS, MEMO, EXPIRES_AT(HOLD만료), + audit
RESERVATION_PRICE   예약 시점 가격 스냅샷: ROOM_AMOUNT, EXTRA_PERSON_AMOUNT, OPTION_AMOUNT, DISCOUNT_AMOUNT,
                    COUPON_DISCOUNT_AMOUNT, POINT_USE_AMOUNT, TOTAL_AMOUNT, PAY_AMOUNT, SUPPLY_AMOUNT, VAT_AMOUNT
RESERVATION_GUEST   투숙객 정보 ;  RESERVATION_OPTION  선택 옵션 스냅샷
RESERVATION_STATUS_HISTORY  상태 전이 이력(FROM/TO/사유/행위자/일시)
PAYMENT             PAYMENT_ID PK, RESERVATION_ID FK, PG_PROVIDER(TOSS), PAYMENT_KEY UK, ORDER_ID UK,
                    METHOD(CARD|KAKAO_PAY|NAVER_PAY|TOSS_PAY), PAYMENT_STATUS(READY|APPROVED|CANCELLED|PARTIAL_CANCELLED|FAILED),
                    REQUEST_AMOUNT, APPROVED_AMOUNT, CANCELLED_AMOUNT, APPROVED_AT, + audit
PAYMENT_EVENT       결제 이벤트/웹훅 멱등 처리: EVENT_ID PK, PAYMENT_KEY, EVENT_TYPE, RAW_PAYLOAD,
                    PROCESSED_YN, UNIQUE(PAYMENT_KEY, EVENT_TYPE)  ← 중복/역순 도착 방어
REFUND              REFUND_ID PK, RESERVATION_ID, PAYMENT_ID, REFUND_STATUS, REFUND_REASON, REFUND_AMOUNT,
                    PENALTY_AMOUNT, PLATFORM_PENALTY_AMOUNT, PARTNER_PENALTY_AMOUNT, REQUESTED_AT, COMPLETED_AT
REFUND_EVENT        환불 이벤트 이력
SETTLEMENT          SETTLEMENT_ID PK, PARTNER_ID, PERIOD_FROM, PERIOD_TO, SETTLEMENT_STATUS(EXPECTED|CONFIRMED|PAID|HELD|CANCELLED),
                    SALES_AMOUNT, REFUND_AMOUNT, CARD_FEE_AMOUNT, PLATFORM_FEE_AMOUNT, VAT_AMOUNT, SETTLEMENT_AMOUNT,
                    EXPECTED_PAY_DATE, PAID_AT
SETTLEMENT_ITEM     SETTLEMENT_ID FK, RESERVATION_ID, PAYMENT_ID, 금액 필드들, ITEM_STATUS
SETTLEMENT_STATUS_HISTORY   정산 상태 이력
```

### 정산 흐름 (즉시 확정 금지)
```
결제완료 → 정산후보 ─(체크아웃)→ 정산예정 ─(환불/취소 없음 + D+5)→ 정산확정 ─(입금)→ 지급완료
```
정산금액 = 판매금 − 환불 − 카드수수료 − 플랫폼(이음)수수료 − 부가세. (수수료율은 정책 테이블)

> 금액 컬럼은 **원 단위 `BIGINT`** 일관 사용. 모든 금액은 예약 시점 **스냅샷** 보존(요금 변경에 불변).

---

## 6. 부가 도메인

```
COUPON / COUPON_ISSUE   쿠폰(제목·할인률|금액·기간·수취인지정) / 사용자 발급·사용 이력
POINT_LEDGER            포인트 적립/사용 원장(가감 내역, 잔액 파생)
REVIEW                  후기(별점, 고택/객실/작성자/내용). 후기 5건 미만 별점 숨김(노출 규칙)
FAVORITE                찜(TARGET_TYPE: HOUSE|ATTRACTION, TARGET_ID)
INQUIRY_THREAD / INQUIRY_MESSAGE   채팅형 문의(사용자↔파트너), 답변 등록 시 알림
NOTIFICATION            알림(알림톡/푸시) 발송요청·이력, 30일 보관, READ_YN
NOTICE / BANNER / EVENT / ATTRACTION   공지·배너·이벤트·즐길거리(블로그형 콘텐츠)
TERMS / TERMS_AGREEMENT   약관 버전 + 동의 이력(필수/선택, 개인정보·제3자·마케팅·만14세)
FILE_ATTACHMENT         공용 첨부(TARGET_TYPE/ID, 저장명 UUID, 표시명). 사업자서류·이미지 등
COMMON_CODE_GROUP / COMMON_CODE   상태·유형·비품·시설·지역 등 코드 일원화
```

코드 그룹(초기): `USER_TYPE, ACCOUNT_STATUS, PARTNER_APPROVAL_STATUS, HOUSE_STATUS, ROOM_STATUS, REGION,
AMENITY_*, FACILITY_*, VIEW_TYPE, RESERVATION_STATUS, RESERVATION_CHANNEL, PAYMENT_STATUS, PAYMENT_METHOD,
REFUND_STATUS, SETTLEMENT_STATUS, NOTI_TYPE, COUPON_TYPE`.

### Redis 용도
| 용도 | 키 | TTL |
| --- | --- | --- |
| Refresh 토큰 | `auth:refresh:{userId}:{jti}` | refresh 만료 |
| 예약 임시점유 | `reservation:hold:{roomId}:{yyyyMMdd}` | 10~15분(결제창) |
| 운영자 권한 캐시 | `rbac:perms:{userId}` | 짧게 |
| 공통코드/요금 캐시 | `cache:code:{group}` 등 | 변경 시 무효화 |
| 로그인 실패/레이트리밋 | `auth:fail:{loginId}` 등 | 정책별 |

---

## 7. 외부 연동 (external — 모두 인터페이스 추상화)

| 연동 | 용도 | MVP 처리 |
| --- | --- | --- |
| **토스페이먼츠** | 결제 승인/취소/환불/웹훅 | `PaymentGateway` 구현. orderId/paymentKey 유니크 + 이벤트 멱등 |
| **포트원(PortOne)** | 휴대폰 본인인증 | `PhoneVerification` 인터페이스 |
| **소셜 OAuth** | 구글/카카오/애플 로그인 | provider별 검증 → USER_SOCIAL_ACCOUNT |
| **카카오맵** | 지도 검색/좌표 | 좌표 저장(LAT/LNG), 검색은 앱/프론트 위주 |
| **공공 사업자검증 API** | 사업자번호 진위 | 파트너 가입 시 호출 |
| **알림톡(외부 사업자)** | 알림/DM 발송 | `MessageSender` 인터페이스, 발송이력 저장 |

---

## 8. MVP 스프린트 순서 (codex 권장)

> 핵심 리스크는 UI가 아니라 **재고 중복판매·결제 불일치·환불/정산 오류**. 예약·결제·요금·재고를 먼저 안정화.

| Sprint | 범위 |
| --- | --- |
| **1. 기반** | global(응답/예외/보안/JWT) · USER_ACCOUNT · RBAC · 파일첨부 · 공통코드 · 약관동의 |
| **2. 계정/승인** | 이메일/소셜 로그인 · 전화인증 인터페이스 · 파트너 가입/사업자검증/첨부 · 운영자 승인 · 운영자 MFA |
| **3. 상품** | 파트너→고택→객실 · 이미지/비품/시설/옵션/정책 · 운영시간 · 휴무규칙 |
| **4. 요금/재고** | RATE_PLAN · RATE_CALENDAR · INVENTORY_CALENDAR · 날짜/인원 가용객실 조회 |
| **5. 예약/결제** | Redis hold · 예약생성 · 토스 결제승인 · 웹훅 멱등 · 예약 상태관리 · 수기예약 |
| **6. 취소/환불/알림** | 취소요청 · 환불 정책계산(수동) · 알림톡/푸시 발송이력 · 예약 알림 |
| **7. 운영 부가** | 후기 · 찜 · 문의 · 쿠폰 기본 · 포인트 원장 |
| **8. 정산 MVP** | 정산 예정 리스트 · 금액 수동확정 · 파트너별 조회 · 다운로드 |

**후순위(분리)**: 타OTA 실시간연동 · 정산 자동입금 · 복잡 탄력요금제 · 마케팅 DM 자동화 · MICE 고도화 · 통계 고도화 · 환불 완전자동화.

**미정/협의 항목은 코드에 박지 말고 `정책 테이블 + 수동 운영`으로 시작** (요금권한·정산로직·환불자동화·알림톡 템플릿).

### MVP에서도 절대 빠지면 안 되는 것
예약 가격 **스냅샷** · 결제 이벤트 **멱등성** · Redis hold **TTL** · DB 재고차감 **정합성** ·
권한별 **데이터 스코프** · soft delete 기준 통일 · **상태 변경 이력**(예약/결제/환불/정산).
