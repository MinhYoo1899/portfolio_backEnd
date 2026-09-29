# 공개 고택 브라우즈 API 계약 (2026-07-02)

> 대상: Eeum_pts(손님 SPA) 프론트 · 백엔드 재사용
> 목적: 손님 퍼널 **목록 → 상세 → 날짜/인원 가용검색 → 예약 생성**이 붙도록 공개(PUBLIC) API 3종의 계약을 고정한다.
> 본 문서는 코드(`PublicHouseController`, `PublicHouseServiceImpl`, `PublicHouseMapper_mysql.xml`, `RoomCalendarMapper_mysql.xml`) 기준 사실 정리이며 동작 변경은 없다. 커밋 `9d92412`, dev 라이브. 예시는 dev 실측 응답 기반 **형식 예시**(이름 등 일부 값 가공, §5 응답은 실측 그대로).

---

## 1. 한눈에 보기

| API | 인증 | 용도 |
|---|---|---|
| `GET /public/houses/list` | **비로그인 가능** | 메인/검색 카드 목록(키워드·페이징) |
| `GET /public/houses/detail?houseSn=` | **비로그인 가능** | 고택 상세(이미지·편의시설·결제수단·객실요약·판매자 표시정보) |
| `GET /public/houses/availability?houseSn=&checkInDate=&checkOutDate=&guestCount=` | **비로그인 가능** | 날짜/인원 기준 객실별 예약 가능 여부·총액·최저가 |
| (연계) `POST /reservations` | **회원 로그인 필수** | 예약 생성 — §6 참고 수준만, 상세 계약은 별도 문서 |

- 공통 응답 envelope: `{"resultCode","resultMessage","result"}`.
- ⚠ **기존 `GET /availability`는 보호 API로 유지**된다(무토큰 401). 손님 화면은 반드시 `/public/houses/availability`를 사용할 것.
- 화이트리스트는 위 3경로 **정확매칭**만 공개 — `/public/` 하위라도 이 문서에 없는 경로는 공개가 아니다.

---

## 2. 공개 노출(가시성) 기준 — 3 API 공통

아래를 **전부** 충족하는 데이터만 노출된다. 목록/상세/존재확인은 같은 SQL 조각(`publicVisibleWhere`)을 공유하고, 가용검색은 동일 기준을 자체 쿼리에 내장한다(계약 기준 동일 — 회귀 테스트로 고정):

1. 고택 `HOUSE_STTS_CD = 'ACTIVE'` (+ 미삭제)
2. 객실 `ROOM_STTS_CD = 'ACTIVE'` (+ 미삭제) — 상세의 rooms, 가용검색의 rooms 모두
3. 소유 파트너 승인 `APRV_STTS = 'APPROVED'`
4. 파트너 계정 활성 `ACNT_STTS = 'ACTIVE'` (운영자 정지 시 즉시 비노출)

**비공개/미존재는 전부 동일한 `404 HOUSE_NOT_FOUND`** — DRAFT/HIDDEN/SUSPENDED/삭제/미승인/정지를 구분해 주지 않는다(존재 여부 은닉). 프론트는 404를 "없는 상품" 하나로만 처리하면 된다.

---

## 3. `GET /public/houses/list`

### 요청 (query)

| 파라미터 | 필수 | 기본/제약 |
|---|---|---|
| `page` | X | 1부터. 기본 1. **1 미만은 1로 보정(에러 아님)** |
| `pageSize` | X | 기본 20, 최대 100. **1 미만→20, 100 초과→100으로 보정(에러 아님)** |
| `keyword` | X | 최대 100자(초과 시 400 `VALIDATION_ERROR`). 고택명/주소 부분일치(LIKE) |

### 응답 `result` — 페이지 공통형 `{items, total, page, pageSize, totalPages}`

```json
{
  "resultCode": "SUCCESS",
  "resultMessage": "정상 처리되었습니다.",
  "result": {
    "items": [
      {
        "houseSn": 76, "houseNm": "학봉종택",
        "sidoNm": "경상북도", "sigunguNm": "안동시", "regionText": "경상북도 안동시",
        "thumbnailUrl": "/files/download?atchFileSn=66",
        "bscPrsnCnt": 2, "maxPrsnCnt": 4,
        "priceFrom": null
      }
    ],
    "total": 8, "page": 1, "pageSize": 20, "totalPages": 1
  }
}
```

- `regionText` = "시도 시군구". 시도/시군구 중 **하나만 있으면 그 값만** 들어가고, **둘 다 없을 때만 null**(카드에서 숨김 처리).
- `thumbnailUrl` 없으면 null → 프론트 기본이미지. 이미지 URL은 전부 **백엔드 경유 상대경로**(`/files/download?atchFileSn=`) — 그대로 base URL에 붙여 사용, 비로그인 접근 가능.
- `priceFrom`은 **현재 항상 null**(날짜 미지정 카탈로그 단계 — 최저가는 가용검색으로).
- 정렬 최신 등록순(houseSn DESC) 고정.

---

## 4. `GET /public/houses/detail?houseSn={sn}`

### 응답 `result` (형식 예시 — 필드 구성은 dev 실측과 동일)

```json
{
  "houseSn": 76, "houseNm": "학봉종택",
  "sidoNm": null, "sigunguNm": null, "regionText": null,
  "addr": "서울 종로구 세종대로 175",
  "houseDc": "조선시대 한옥.",
  "chkinTime": "15:00", "chkoutTime": "11:00", "lateChkoutTime": "13:00",
  "adultAddChrgAmt": 0, "childAddChrgAmt": 0, "infantAddChrgAmt": 0,
  "rsvtCnfirmCd": "AUTO",
  "cnclRfndPlcyCn": "7일 전 100% 환불.", "pbtrnspGuideCn": "", "noticeCn": "",
  "payMethods": ["CARD"],
  "images": [ { "imageUrl": "/files/download?atchFileSn=66", "rprsYn": "Y", "sortSeq": 1 } ],
  "amenities": [ { "amenitySn": 5, "amenityCtgryCd": "OUTDOOR", "applyScopeCd": "HOUSE",
                   "amenityNm": "마당", "amenityKey": "yard", "iconKey": "ph-tree",
                   "sortSeq": 1, "useYn": "Y", "customYn": "N" } ],
  "rooms": [ { "roomSn": 69, "roomNm": "기본 객실", "bscPrsnCnt": 2, "maxPrsnCnt": 2,
               "minStayNgtCnt": 1, "roomDc": null, "areaSqm": null,
               "roomCnt": 1, "bathrmCnt": 1, "repImgUrl": "/files/download?atchFileSn=65" } ],
  "seller": { "conm": "고운재", "rprsvNm": "강대표", "brno": "9990001116", "mailOrderSalesNo": null }
}
```

### 필드 규칙

- **주소는 `addr`(기본주소)까지만 제공** — 상세주소(daddr)·우편번호(zip)는 공개 계약에 없다. 지도/위치 표기는 addr 기반으로.
- 시각 3종은 `"HH:mm"` 문자열(없으면 null). `rsvtCnfirmCd`: `AUTO`=즉시확정 / `MANUAL`=판매자 승인 후 확정 — 예약 버튼 문구 분기용.
- `payMethods`: 공통코드 `PAY_MTHD`(CARD/BANK_TRANSFER/ONSITE), 노출 순서 유지.
- `images`: sortSeq 오름차순, 대표(`rprsYn:"Y"`) 포함 전체 목록.
- `amenities`: 분류코드(`amenityCtgryCd`)는 `GET /codes/list?groupCd=AMENITY_CTGRY`로 라벨 조회(하드코딩 금지).
- `rooms`: **ACTIVE 객실만**. 가격/재고 없음(가용검색 소관). `minStayNgtCnt`로 날짜 선택 UI를 사전 제한 가능.
- `seller`: **4필드 고정** `conm/rprsvNm/brno/mailOrderSalesNo`(전부 nullable — 특히 통신판매번호는 미신고 파트너 null).
- **응답에 없는 것(금지 필드)**: `prtnrSn`, 상태코드(`houseSttsCd`/`roomSttsCd`), `zip`, `daddr`, 감사컬럼(`regDt`/`mdfcnDt` 등), 내부 재고설정(`dfltStockQty`), 담당자 연락처. 회귀 테스트로 잠겨 있으니 프론트가 이 필드를 기대하면 안 된다.

---

## 5. `GET /public/houses/availability`

### 요청 (query, 전부 필수)

| 파라미터 | 제약 |
|---|---|
| `houseSn` | 공개 고택(비공개/미존재 404) |
| `checkInDate` | `yyyy-MM-dd`. **KST 오늘 이전이면 400**(오늘 체크인은 허용) |
| `checkOutDate` | checkIn 초과(최소 1박), 최대 365박 |
| `guestCount` | 1 이상 |

### 판정 규칙

- 숙박일 = **`[checkInDate, checkOutDate)` 반개구간**(체크아웃일은 숙박하지 않음). `nights` = 그 일수.
- 객실이 `available: true`가 되려면 **모든 숙박일**이 다음을 충족: 캘린더 **행 존재**(없으면 판매불가) + `CLOSED_YN='N'` + `PRICE_AMT > 0` + `TOTAL_QTY - SOLD_QTY >= 1`. **하루라도 미충족이면 그 객실은 불가**(부분 예약 없음).
- 추가 조건: `maxPrsnCnt >= guestCount`, `nights >= minStayNgtCnt`.
- ⚠ **Redis 임시점유(HOLD)는 조회에 미반영**(MVP 한계) — 다른 손님이 결제 진행 중인 객실이 가용으로 보일 수 있고, 이 경우 예약 생성 시점에 매진 처리된다.

### 응답 `result` (dev 실측)

```json
{
  "houseSn": 76,
  "checkInDate": "2026-07-10", "checkOutDate": "2026-07-12", "nights": 2,
  "lowestTotalPrice": null,
  "rooms": [
    { "roomSn": 69, "roomNm": "기본 객실", "bscPrsnCnt": 2, "maxPrsnCnt": 2,
      "available": false, "reason": "SOLD_OUT", "totalPrice": null }
  ]
}
```

- `rooms`에는 **불가 객실도 포함**되며 `reason`으로 사유를 준다: `SOLD_OUT`(재고/마감/미설정/가격0) / `CAPACITY`(인원 초과) / `MIN_STAY`(최소숙박 미달). `available: true`면 `reason: null`. 복수 사유에 동시 해당하면 **하나만** 반환하며 우선순위는 `CAPACITY → MIN_STAY → SOLD_OUT`.
- `totalPrice` = 전 숙박일 단가 합계(KRW 정수), **가능 객실만** 채워짐(불가는 null).
- `lowestTotalPrice` = 가능 객실 `totalPrice`의 최솟값. **가능 객실이 0건이면 null**.
- 밤별 단가 배열은 제공하지 않는다 — 화면 표기는 `totalPrice / nights`(평균 1박가)로 계산. 밤별 확정 단가는 예약 생성 시 서버가 스냅샷한다.

---

## 6. 예약 생성 연계 (참고)

- 가용검색에서 `available: true`인 객실로 `POST /reservations`(회원 전용, `CHNL_LOGIN_USER`) 호출 — 비로그인이면 로그인 유도.
- **가격/재고의 최종 판정은 예약 생성이 다시 한다**(가용검색은 표시용). 조회~생성 사이 변동이 있으면 생성이 `RESERVATION_SOLD_OUT`/`RESERVATION_ROOM_UNAVAILABLE`(409)로 거절하므로 프론트는 이 두 에러의 안내 UX를 준비할 것.
- 중복 제출 방지용 `Idempotency-Key` 헤더(UUID) 첨부 권장 — **CORS 허용 헤더에 포함되어 브라우저 cross-origin에서도 사용 가능**(적용 커밋 이후). 멱등은 opt-in이라 미첨부 시 현행 동작이며, 서버측 (사용자, 키) UNIQUE 백스톱은 별개로 동작한다.
- 요청/응답 상세는 예약 API 계약 문서(작성 예정)에서 다룬다.

---

## 7. 에러 정리

| resultCode | HTTP | 발생 |
|---|---|---|
| `VALIDATION_ERROR` | 400 | 필수 파라미터 누락/형식 오류(houseSn 비숫자, 날짜 형식, guestCount<1 등) |
| `CALENDAR_INVALID_RANGE` | 400 | 체크아웃≤체크인, 365박 초과, **KST 과거 체크인**(availability) |
| `HOUSE_NOT_FOUND` | 404 | 비공개/미존재 고택(detail·availability — §2 은닉 규칙) |
| `UNAUTHORIZED` | 401 | (참고) 보호 API 무토큰 호출 — 기존 `/availability` 등. ⚠**공개 3경로도 위조·만료된 `Authorization` 헤더를 붙여 보내면 401이 날 수 있다**(무토큰이면 정상 200) — JWT 필터가 경로와 무관하게 유효하지 않은 토큰을 즉시 거부. §8-7 참고 |

---

## 8. 프론트 구현 주의

1. 가용검색은 `/public/houses/availability`만 사용 — 기존 `/availability`는 보호 API(401)로 남아 있으니 붙이지 말 것.
2. **`available: true`인 객실만 예약 버튼 활성화.** 불가 객실은 `reason`별 표기 권장: SOLD_OUT="매진" / CAPACITY="인원 초과" / MIN_STAY="최소 N박부터". N(`minStayNgtCnt`)은 **가용검색 응답에는 없으므로 상세 응답의 `rooms`를 `roomSn`으로 매칭**해 얻는다.
3. 주소는 `addr`까지만 온다 — 상세주소가 필요한 화면(예: 예약 확정 후 안내)은 별도 정책 결정 전까지 미지원.
4. 날짜 선택 UI: 과거 날짜 비활성(오늘 KST 포함 가능), 상세의 `minStayNgtCnt`로 최소 박수 사전 제한 가능.
5. 예약 직전 최신 가격은 가용검색 재호출로 갱신 권장(HOLD 미반영·시점 차이 존재 — §5 캐비엇, §6 재검증).
6. 이미지 URL은 상대경로 그대로 사용(비로그인 접근 가능, 별도 토큰 불요).
7. **공개 API도 401이 올 수 있다**: 로그인 사용자의 access 토큰이 만료된 채 `Authorization` 헤더가 붙으면 공개 경로라도 401(§7). 기존 axios 401→`/auth/refresh` 재시도 인터셉터를 공개 API 응답에도 동일 적용하면 자연 해소된다(무토큰 비로그인 사용자는 항상 200).
