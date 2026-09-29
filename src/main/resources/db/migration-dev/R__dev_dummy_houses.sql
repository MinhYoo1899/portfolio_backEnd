-- =============================================================================
-- R__ (dev 전용) : 더미 고택 8개 + 소유 파트너 승인/활성화
--   퍼블리싱 메인 디자인의 하드코딩 고택 8개를 동적 로딩용 더미로 시드.
--   ※ dev location 에서만 적용(운영 미적용). 멱등(NOT EXISTS / 조건부 UPDATE) — 재실행 안전.
--   전제: partner@eeum.kr (BRNO 9990001111) 파트너 계정이 가입 API 로 먼저 생성돼 있어야 함.
--         (없으면 전부 0행 — 안전한 no-op)
--   지역코드: V20 REGION 시드(시도/시군구). 상세주소는 더미(실제 위치 특정 회피).
-- =============================================================================

-- ---------- 1) 파트너 승인 (REQUESTED → APPROVED, 승인자=dev 마스터) ----------
UPDATE PRTNR_MST P
   JOIN USER_ACNT A ON A.EML_ADDR = 'master@eeum.dev' AND A.DEL_YN = 'N'
   SET P.APRV_STTS = 'APPROVED', P.APRV_USER_SN = A.USER_SN, P.APRV_DT = UTC_TIMESTAMP(6)
 WHERE P.BRNO = '9990001111' AND P.DEL_YN = 'N' AND P.APRV_STTS = 'REQUESTED';

-- ---------- 2) 파트너 로그인 계정 활성화 (PENDING → ACTIVE) ----------
UPDATE USER_ACNT
   SET ACNT_STTS = 'ACTIVE'
 WHERE EML_ADDR = 'partner@eeum.kr' AND DEL_YN = 'N' AND ACNT_STTS <> 'ACTIVE';

-- ---------- 3) 더미 고택 8개 (ACTIVE, 실제 지역코드) ----------
INSERT INTO HOUSE (PRTNR_SN, HOUSE_NM, HOUSE_STTS_CD, SIDO_REGION_CD, SIGUNGU_REGION_CD, ADDR, DADDR, HOUSE_DC, REG_DT, RGTR_SN)
SELECT P.PRTNR_SN, X.NM, 'ACTIVE', X.SIDO, X.SIGUNGU, X.ADDR, '(더미 상세주소)', X.DC, UTC_TIMESTAMP(6), P.USER_SN
  FROM PRTNR_MST P
  JOIN (
        SELECT '내 앞댁'        NM, '47' SIDO, '47770' SIGUNGU, '경상북도 영덕군 (더미로)' ADDR, '동해가 보이는 고즈넉한 종택' DC
  UNION ALL SELECT '학봉종택',      '47', '47170', '경상북도 안동시 (더미로)', '안동 전통 양반가옥 한옥스테이'
  UNION ALL SELECT '월영채',        '44', '44790', '충청남도 청양군 (더미로)', '달빛 아래 멋스러운 청양 한옥'
  UNION ALL SELECT '이든민박',      '52', '52730', '전북특별자치도 무주군 (더미로)', '무주 산자락의 아늑한 민박'
  UNION ALL SELECT '안동스테이',    '47', '47170', '경상북도 안동시 (더미로)', '안동 도심 근처 모던 한옥'
  UNION ALL SELECT '한옥스테이 루가', '51', '51150', '강원특별자치도 강릉시 (더미로)', '강릉 바다 가까운 한옥'
  UNION ALL SELECT '서린당',        '46', '46820', '전라남도 해남군 (더미로)', '땅끝마을 해남의 정갈한 고택'
  UNION ALL SELECT '필굿한옥',      '43', '43760', '충청북도 괴산군 (더미로)', '괴산 산촌의 힐링 한옥'
       ) X
 WHERE P.BRNO = '9990001111' AND P.DEL_YN = 'N'
   AND NOT EXISTS (SELECT 1 FROM HOUSE H WHERE H.PRTNR_SN = P.PRTNR_SN AND H.HOUSE_NM = X.NM AND H.DEL_YN = 'N');

-- ---------- 4) 고택별 기본 객실 1개 (ACTIVE — 인원 표시용) ----------
INSERT INTO ROOM (HOUSE_SN, ROOM_NM, ROOM_STTS_CD, BSC_PRSN_CNT, MAX_PRSN_CNT, DFLT_STOCK_QTY, MIN_STAY_NGT_CNT, ROOM_DC, REG_DT, RGTR_SN)
SELECT H.HOUSE_SN, '기본 객실', 'ACTIVE', 2, 4, 1, 1, '(더미) 기본 객실', UTC_TIMESTAMP(6), P.USER_SN
  FROM HOUSE H
  JOIN PRTNR_MST P ON P.PRTNR_SN = H.PRTNR_SN
 WHERE P.BRNO = '9990001111' AND H.DEL_YN = 'N' AND H.HOUSE_STTS_CD = 'ACTIVE'
   AND NOT EXISTS (SELECT 1 FROM ROOM R WHERE R.HOUSE_SN = H.HOUSE_SN AND R.DEL_YN = 'N');

-- ---------- 5) 수정일시=등록일시 백필(기존 더미행 — [[convention-mdfcndt-equals-regdt-on-insert]]) ----------
UPDATE HOUSE H JOIN PRTNR_MST P ON P.PRTNR_SN = H.PRTNR_SN
   SET H.MDFCN_DT = H.REG_DT
 WHERE P.BRNO = '9990001111' AND H.DEL_YN = 'N' AND H.MDFCN_DT IS NULL;

UPDATE ROOM R JOIN HOUSE H ON H.HOUSE_SN = R.HOUSE_SN JOIN PRTNR_MST P ON P.PRTNR_SN = H.PRTNR_SN
   SET R.MDFCN_DT = R.REG_DT
 WHERE P.BRNO = '9990001111' AND R.DEL_YN = 'N' AND R.MDFCN_DT IS NULL;

-- ---------- 6) 통신판매업신고번호 backfill (파트너 전곳 null → dev 더미. COALESCE 로 기존값 보존) ----------
--   OPS 지적: seller.mailOrderSalesNo 전곳 null. dev 더미(진짜 값 아님) — VARCHAR(40).
UPDATE PRTNR_MST
   SET MAIL_ORDER_SALES_NO = CONCAT('2026-DEV-', LPAD(PRTNR_SN, 6, '0')),
       MDFCN_DT = UTC_TIMESTAMP(6)
 WHERE DEL_YN = 'N'
   AND (MAIL_ORDER_SALES_NO IS NULL OR MAIL_ORDER_SALES_NO = '');

-- ---------- 7) 더미 고택 정책/시간/우편/교통/공지 backfill (null 필드만 — COALESCE 로 실데이터 보존) ----------
--   OPS 지적: 시드 고택 우편번호·체크인/취소정책·교통 null → 미리보기/드로어가 빔. 재현성 위해 시드에 명시.
UPDATE HOUSE H JOIN PRTNR_MST P ON P.PRTNR_SN = H.PRTNR_SN
   SET H.ZIP               = COALESCE(H.ZIP, '36709'),
       H.CHKIN_TIME        = COALESCE(H.CHKIN_TIME, '15:00:00'),
       H.CHKOUT_TIME       = COALESCE(H.CHKOUT_TIME, '11:00:00'),
       H.CNCL_RFND_PLCY_CN = COALESCE(H.CNCL_RFND_PLCY_CN, '체크인 7일 전 100% 환불 / 3일 전 50% / 1일 전·당일 환불 불가. (dev 더미 정책)'),
       H.NOTICE_CN         = COALESCE(H.NOTICE_CN, '전 객실 금연 · 반려동물 동반 불가 · 정숙 요망. 문의는 예약 확정 후 안내드립니다. (dev 더미 공지)'),
       H.PBTRNSP_GUIDE_CN  = COALESCE(H.PBTRNSP_GUIDE_CN, 'KTX 안동역/시외버스터미널에서 차량 약 20분 · 무료 주차 가능 · 대중교통 편도 40분. (dev 더미 교통안내)'),
       H.MDFCN_DT          = UTC_TIMESTAMP(6)
 WHERE P.BRNO = '9990001111' AND H.DEL_YN = 'N'
   AND (H.ZIP IS NULL OR H.CHKIN_TIME IS NULL OR H.CHKOUT_TIME IS NULL
        OR H.CNCL_RFND_PLCY_CN IS NULL OR H.NOTICE_CN IS NULL OR H.PBTRNSP_GUIDE_CN IS NULL);

-- ---------- 8) 객실 방/화장실 수 backfill (발행게이트 '재고·욕실' 통과 + 드로어 표출) ----------
UPDATE ROOM R JOIN HOUSE H ON H.HOUSE_SN = R.HOUSE_SN JOIN PRTNR_MST P ON P.PRTNR_SN = H.PRTNR_SN
   SET R.ROOM_CNT   = COALESCE(R.ROOM_CNT, 1),
       R.BATHRM_CNT = COALESCE(R.BATHRM_CNT, 1),
       R.MDFCN_DT   = UTC_TIMESTAMP(6)
 WHERE P.BRNO = '9990001111' AND R.DEL_YN = 'N'
   AND (R.ROOM_CNT IS NULL OR R.BATHRM_CNT IS NULL);

-- ---------- 9) 결제수단 CARD (없으면 추가 — NOT EXISTS 멱등, MDFCN_DT=REG_DT) ----------
INSERT INTO HOUSE_PAY_MTHD (HOUSE_SN, PAY_MTHD_CD, SORT_SEQ, REG_DT, MDFCN_DT, RGTR_SN)
SELECT H.HOUSE_SN, 'CARD', 0, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), P.USER_SN
  FROM HOUSE H JOIN PRTNR_MST P ON P.PRTNR_SN = H.PRTNR_SN
 WHERE P.BRNO = '9990001111' AND H.DEL_YN = 'N'
   AND NOT EXISTS (SELECT 1 FROM HOUSE_PAY_MTHD M
                    WHERE M.HOUSE_SN = H.HOUSE_SN AND M.PAY_MTHD_CD = 'CARD' AND M.DEL_YN = 'N');

-- ⚠ 날짜별 요금달력(ROOM_CALENDAR)은 이 시드에서 제외 — per-night 생성은 복잡/고위험이라 별도.
--   (OPS 가 price-stock/set 로 런타임 채움 상태. 재현 시드 필요하면 후속 R__ 로 date-range 생성.)
