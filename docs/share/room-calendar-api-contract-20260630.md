# 객실 캘린더 API 계약 정리 (2026-06-30)

> 대상: OPS/PTNS 프론트 · 백엔드 재사용
> 목적: `set` / `close` / `price-stock/set` 3개 API의 **책임·변경/보존 필드·사용처**를 고정해
> "요금 변경이 마감일을 다시 열어버리는" CLOSED_YN 재오픈 버그의 재발을 막는다.
> 본 문서는 코드/매퍼(`RoomCalendarServiceImpl`, `RoomCalendarMapper_mysql.xml`) 기준 사실 정리이며 동작 변경은 없다.

---

## 1. 한눈에 보기 — API별 책임 비교

| API | 용도 | 변경 필드 | 보존 필드 | 신규 행 |
|---|---|---|---|---|
| `set` | 가격·수량·마감 **일괄** 설정 | TOTAL_QTY, PRICE_AMT, **CLOSED_YN** | SOLD_QTY | closedYn(기본 N) |
| `price-stock/set` | **가격·수량만** 저장 | TOTAL_QTY, PRICE_AMT | **CLOSED_YN**, SOLD_QTY | CLOSED_YN='N' |
| `close` | **마감여부만** 토글 | CLOSED_YN | TOTAL_QTY, PRICE_AMT, SOLD_QTY | Y=placeholder(0/0/Y) / N=생성안함 |

> 공통: `SOLD_QTY`(판매수량)는 어떤 API도 변경하지 않는다(결제 최종확정 전용).

### 날짜 입력 방식 & 정책 차이

| API | 날짜 입력 | 과거 날짜 | 최대 |
|---|---|---|---|
| `set` / `price-stock/set` | startDate~endDate + (선택)daysOfWeek 요일필터 | **허용**(범위 기반) | between(start,end)≤365일(종료일 포함이라 최대 366일) |
| `close` | dates[] (명시적 날짜 목록) | **거부**(오늘 KST 이전) | 366개(@Size — **원본 배열 길이** 기준, 중복 제거 전) |

> ⚠ `set`/`price-stock/set`은 과거 날짜를 막지 않는다(범위 upsert). 과거 마감/해제가 필요하면 그 차이를 인지할 것. `close`만 미래(오늘 포함) 날짜로 제한.

---

## 2. API별 상세

### 2-1. `set` — 가격/수량/마감 일괄 설정 (`upsertCalendarRange`)
- **요청**: roomSn, startDate, endDate, daysOfWeek?, totalQty, priceAmt, closedYn?(기본 "N")
- **동작**: 범위×요일로 산출한 날짜에 `INSERT ... ON DUPLICATE KEY UPDATE TOTAL_QTY, PRICE_AMT, CLOSED_YN`
- **변경**: TOTAL_QTY, PRICE_AMT, **CLOSED_YN** / **보존**: SOLD_QTY
- ⚠ **`closedYn`을 생략(기본 N)하면 기존에 마감(Y)이던 날짜가 다시 열린다**(CLOSED_YN=VALUES). → 단순 요금 저장 용도로 쓰면 안 됨.
- **용도**: 초기 셋업, 또는 가격·수량·마감을 **명시적으로 한 번에** 덮어야 하는 특수 상황.
- 발행 가드: ACTIVE 고택이면 **HousePublishValidator 전체 재검증**(캘린더 변경의 주 영향=미래 판매가능 행 존재) — 미달 시 롤백.

### 2-2. `price-stock/set` — 가격/수량만 저장 (`upsertPriceStockRange`) ★요금 저장 권장
- **요청**: roomSn, startDate, endDate, daysOfWeek?, totalQty, priceAmt  (※ closedYn 필드 **없음**)
- **동작**: `INSERT(신규행 CLOSED_YN='N') ... ON DUPLICATE KEY UPDATE TOTAL_QTY, PRICE_AMT`만
- **변경**: TOTAL_QTY, PRICE_AMT / **보존**: **CLOSED_YN(기존 행)**, SOLD_QTY
- **신규 행**: CLOSED_YN='N'으로 생성(아직 마감 아님)
- **용도**: **OPS/PTNS의 요금·수량 변경은 이 API를 쓴다.** 기존 마감일을 건드리지 않으므로 재오픈 버그가 구조적으로 불가능.
- 발행 가드: `set`과 동일(가격>0·재고여유가 판매가능에 영향).

### 2-3. `close` — 마감/해제 토글 (`closeDatesUpsert` / `openDates`)
- **요청**: roomSn, dates[], closedYn("Y"=마감 / "N"=해제)  ※ 한 호출은 한 의도만
- **마감(Y)**: 기존 행은 `CLOSED_YN='Y'`만, 없는 날짜는 `0/0/'Y'` placeholder 생성. TOTAL/PRICE/SOLD 보존. 발행 가드 재검증.
- **해제(N)**: 대상 날짜 중 행이 없는 날짜가 하나라도 있으면 **거부**(`CALENDAR_ROW_NOT_FOUND`) — 없는 날짜는 원가격/원수량이 없어 열 수 없음. 있는 날짜만 `CLOSED_YN='N'`. (해제는 판매가능 증가 방향 → 발행 가드 불요)
- **정책**: 과거 날짜(오늘 KST 이전) 거부(`CALENDAR_INVALID_RANGE` 400), 중복 제거. 빈 목록·366개 초과는 DTO 검증으로 먼저 차단(`VALIDATION_ERROR` 400) — `@Size(max=366)`는 **원본 배열 길이**에 걸리므로 367개를 보내 중복 제거 후 366 이하가 돼도 거부. 서비스의 중복 제거·최대 개수(366)는 2차 방어.

---

## 3. 채널별 경로 / 권한 / 소유권

| 채널 | 경로 prefix | 권한 | 소유자 해석 | 행위자(기록자) |
|---|---|---|---|---|
| PARTNER(본인) | `/rooms/calendar...` | `HOUSE_MNG` | 토큰→findManageablePrtnrSn | 파트너 본인 |
| OPERATOR(대행) | `/operator/rooms/calendar...` | `HOUSE_PROXY_MNG` | 대상 객실에서 **서버 해석**(resolveOwnerForRoom) | 운영자 |

- 두 채널 모두 `roomSn`만 받고 **소유 파트너는 서버가 해석**한다(요청에 prtnrSn 없음 → IDOR 차단). 소유 아닌/없는 객실은 404(`ROOM_NOT_FOUND`). 단 OPERATOR 경로에서 **객실은 있으나 부모 고택이 삭제/미존재**면 404(`HOUSE_NOT_FOUND`) — 둘 다 404지만 코드가 다름.
- OPERATOR 대행은 소유 파트너가 **APPROVED + 계정 ACTIVE(`ACNT_STTS`, 고택 상태 아님)**일 때만 허용 — 미승인·비활성이면 `409 CONFLICT`(resolver가 차단).
- 행위자 userSn은 DB에 **신규 행이면 `RGTR_SN`, 기존 행 갱신 시 `MDFR_SN`** 으로 기록(PARTNER=본인 / OPERATOR=운영자).
- 엔드포인트: 각 채널에 `set`·`price-stock/set`·`close`(POST), `list`(GET) 대칭 존재. 권한 경계는 보안 슬라이스 테스트로 고정됨(`RoomCalendarSecurityVerification` / `OperatorRoomCalendarSecurityVerification`).

---

## 4. 잘못 쓰면 생기는 버그 예시

### 버그 ① 요금 변경에 `set` 사용 → 마감일 재오픈 (가장 흔한 함정)
```
초기: 7/10 마감(CLOSED_YN='Y'), 가격 15만
운영자가 7월 전체 요금을 17만으로 올리려고 /set { start:7/1, end:7/31, price:170000, qty:1 } 호출 (closedYn 생략)
→ set 의 ON DUPLICATE 가 CLOSED_YN=VALUES('N')로 덮음
→ 7/10 이 다시 열림(판매 가능). 닫아둔 날짜가 노출되는 사고.
✅ 올바른 방법: /price-stock/set 사용 → 7/10 의 CLOSED_YN='Y' 보존.
```

### 버그 ② 신규 마감일을 `price-stock/set`으로 만들려 함
```
아직 캘린더 행이 없는 8/15 를 마감하려고 price-stock/set 호출
→ 신규 행이 CLOSED_YN='N'(마감 아님)으로 생성됨. 마감 안 됨.
✅ 올바른 방법: /close { dates:[8/15], closedYn:'Y' } → placeholder(0/0/'Y')로 마감 생성.
```

### 버그 ③ 미설정 날짜를 `close(N)`으로 열려 함
```
행이 없는 9/1 을 해제하려고 close { dates:[9/1], closedYn:'N' }
→ CALENDAR_ROW_NOT_FOUND(거부). 원가격/원수량이 없어 열 수 없음.
✅ 먼저 price-stock/set 으로 가격·수량을 넣어 행을 만든 뒤(기본 CLOSED_YN='N'=이미 열림) 사용.
```

---

## 5. 프론트 권장 흐름 (OPS Phase2 기준)

| 사용자 의도 | 호출 API | 비고 |
|---|---|---|
| 요금/수량 변경 | `price-stock/set` | 마감일 보존. 평일/주말 다른 가격은 daysOfWeek로 2회 호출 |
| 특정 날짜 마감 | `close` (Y) | 없는 날짜도 placeholder로 마감 생성 |
| 특정 날짜 해제(오픈) | `close` (N) | 행 있는 날짜만. 없으면 먼저 price-stock/set |
| 신규 객실 초기 셋업 | `set` 또는 price-stock/set | 마감까지 한 번에 정하려면 set, 가격/수량만이면 price-stock/set |
| 가격·수량·마감을 동시에 명시적으로 덮기 | `set` | closedYn을 **반드시 명시**해서 의도대로 |

**핵심 규칙**
1. 요금/수량 변경은 **항상 `price-stock/set`** (set 금지).
2. 마감/해제는 **항상 `close`**.
3. `set`은 "CLOSED_YN까지 의도적으로 덮어쓰는" 특수 상황에만, closedYn 명시 필수.
4. 기존 닫힘일은 기본적으로 보존된다는 전제로 동작 — 신규 닫힘일은 `close(Y)`로 만든다.

---

## 6. PTNS 재사용 시 주의사항
- PTNS(파트너 콘솔)도 동일 계약을 그대로 사용한다(경로만 `/rooms/calendar...`, 권한 `HOUSE_MNG`). 위 권장 흐름 동일 적용.
- PTNS는 본인 소유만 가능(토큰→prtnrSn). 운영자 대행 경로(`/operator/...`)와 혼동 금지.
- 화면이 "요금 저장" 버튼에 `set`을 연결하지 않도록 주의(재오픈 버그 ①). 반드시 `price-stock/set`.
- 정기휴관 같은 반복 마감은 현재 서버 전용 규칙 없음 → 프론트가 대상 날짜를 펼쳐 `close(Y)`로 호출(백로그: 서버측 daysOfWeek 마감 확장 검토 중).

---

## 7. 공통 에러
- `VALIDATION_ERROR`(400): DTO 검증 실패 — set·price-stock 필수필드 누락/범위(@Min·@Max), close `dates` 빈 목록(@NotEmpty)·366개 초과(@Size). 컨트롤러 진입 시 가장 먼저 발생.
- `CALENDAR_INVALID_RANGE`(400): 종료<시작, 기간 365일 초과, 요일필터 결과 0, close **과거 날짜**.
- `CALENDAR_TOTAL_BELOW_SOLD`(409): 이미 판매된 수량(SOLD)보다 적은 TOTAL 설정 시도(set·price-stock 공통).
- `CALENDAR_ROW_NOT_FOUND`(**404**): close(N) 대상에 행 없는 날짜 포함.
- `ROOM_NOT_FOUND`(404): 소유 아닌/없는 객실.
- `HOUSE_NOT_FOUND`(404): OPERATOR 경로에서 객실은 있으나 부모 고택이 삭제/미존재(resolver 소유자 해석 실패).
- `HOUSE_NOT_PARTNER`(403): PARTNER 경로인데 토큰 사용자에게 관리 가능한 파트너가 없음.
- `CONFLICT`(409): OPERATOR 대행 시 소유 파트너 미승인·비활성.
- 미인증 401 / 권한부족 403(채널 권한 불일치 포함).
