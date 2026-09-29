# 🏷 공통코드 사용 가이드 (프론트엔드)

> 상태값·유형값(예약상태, 사용자유형 등)은 **하드코딩하지 않고 공통코드로** 받아 씁니다.
> ✅ 조회 API **구현 완료(2026-06-15)** — `GET /codes/list?groupCd=` (공개, 비로그인 호출 가능). 아래 계약대로 동작합니다.

---

## 1. 구조

- `COMM_CD_GROUP`(그룹) → `COMM_CD`(코드) 2계층.
- 코드 1건 = `{ groupCd, cd, cdNm, sortSeq }` — **값 비교는 `cd`, 화면 표시는 `cdNm`, 정렬은 `sortSeq`.**

## 2. 조회 API (계약 초안)

```
GET /codes/list?groupCd=ACNT_STTS

→ 200
{ "resultCode": "SUCCESS", "resultMessage": "정상 처리되었습니다.",
  "result": { "items": [
      { "groupCd": "ACNT_STTS", "cd": "ACTIVE",    "cdNm": "정상",     "sortSeq": 1 },
      { "groupCd": "ACNT_STTS", "cd": "SUSPENDED", "cdNm": "이용정지", "sortSeq": 2 }
  ] } }
```

## 3. 프론트 사용 규칙

1. **`cd`로 분기, `cdNm`으로 표시** — `cdNm`은 운영 중 바뀔 수 있음(분기 금지).
2. **캐싱 권장**: 공통코드는 자주 안 바뀜 → 앱 시작 시(또는 그룹별 최초 사용 시) 받아 메모리 캐시. 새로고침 주기는 세션 단위면 충분.
3. **셀렉트박스/필터**: `sortSeq` 순 정렬, `useYn='Y'`만 노출(서버가 기본 필터링 — 구현됨).
4. 코드 추가/변경은 백엔드가 **변경 공지**(추후 API 변경 로그 채널)로 알립니다.

## 4. 코드그룹 (현재 시드된 것 — 도메인 진행하며 추가)

**지금 조회 가능(시드 완료):**
| groupCd | 용도 |
| --- | --- |
| `USER_TYPE` | 사용자유형 (MEMBER/PARTNER/OPERATOR) |
| `SOCIAL_PRVDR` | 소셜제공자 (GOOGLE/KAKAO/NAVER/APPLE) ｜ NAVER ✅ 2026-06-22 추가(V16) |
| `TRMS_TYPE` | 약관유형 (SERVICE/PRIVACY/MARKETING) |
| `AGRE_CHNL` | 동의채널 (WEB/APP/ADMIN) |
| `INQR_TYPE` | 문의유형 (RESERVATION/PAYMENT/ACCOUNT/ETC) ✅ 2026-06-16 |

**예정(도메인 진행 시 추가):** `ACNT_STTS`(계정상태), 예약상태, 결제상태, 지역, 비품/시설 등.

> 핵심 상태값(예약/결제 상태머신)은 공통코드와 별개로 **API 계약에 enum 문자열로 명시**됩니다 — 해당 값은 각 도메인 API 문서에서 확정 공지.
