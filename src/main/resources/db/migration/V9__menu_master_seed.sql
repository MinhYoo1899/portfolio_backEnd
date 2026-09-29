-- =============================================================================
-- V9 : 정식 메뉴 기준 데이터 (기획서 요구사항정의서 V1.0 "메뉴 구조도" 시트 기준 — 협의 확정·고정)
-- 사용자 확정(2026-06-12): ① 페이지 구성요소(메인 위젯·대시보드 위젯 등)는 메뉴 행 제외
--   ② "검색"류는 메뉴가 아니라 조건조회 API 기능 ③ 미확정 권한 = NULL(운영자 로그인 시 전부 노출,
--   확정 시 새 V로 UPDATE) ④ 예외: 시스템 관리·직원 관리(신설) = OPERATOR_MNG 예약(코드가 역할에
--   부여되기 전까지 비노출 → 후속 직원관리 API에서 SPV 부여 시 자동 노출) ⑤ 경로는 프론트 라우트 기준값.
-- 방식: 기존 개발 샘플 메뉴를 soft delete 후 정식 세트 주입(클린 슬레이트 — 재실행 시에도 안전).
-- =============================================================================

-- ----------------------------------------------------------------------------
-- 0. 기존 메뉴(개발 샘플 시드) 정리 — R__dev_menu_sample 은 V9 부터 무해화(stub) 처리됨
-- ----------------------------------------------------------------------------
UPDATE MENU_INFO SET DEL_YN = 'Y', MDFCN_DT = NOW(6) WHERE DEL_YN = 'N';

-- ----------------------------------------------------------------------------
-- 1. 사용자 (USER)
-- ----------------------------------------------------------------------------
INSERT INTO MENU_INFO (MENU_CHNL_CD, UPPER_MENU_SN, MENU_NM, URL_ADDR, AUTH_CD, LGN_NEED_YN, SORT_SEQ, USE_YN, REG_DT) VALUES
    ('USER', NULL, '로그인',        '/login',         NULL, 'N', 1, 'Y', NOW(6)),
    ('USER', NULL, '회원 가입',     '/signup',        NULL, 'N', 2, 'Y', NOW(6)),
    ('USER', NULL, '메인',          '/',              NULL, 'N', 3, 'Y', NOW(6)),
    ('USER', NULL, 'MICE 단체 문의','/mice',          NULL, 'N', 4, 'Y', NOW(6)),
    ('USER', NULL, '예약',          '/booking',       NULL, 'Y', 5, 'Y', NOW(6)),
    ('USER', NULL, '즐길거리',      '/attractions',   NULL, 'N', 6, 'Y', NOW(6)),
    ('USER', NULL, '문의',          '/inquiries',     NULL, 'Y', 7, 'Y', NOW(6)),
    ('USER', NULL, '알림',          '/notifications', NULL, 'Y', 8, 'Y', NOW(6)),
    ('USER', NULL, '마이페이지',    NULL,             NULL, 'Y', 9, 'Y', NOW(6));

INSERT INTO MENU_INFO (MENU_CHNL_CD, UPPER_MENU_SN, MENU_NM, URL_ADDR, AUTH_CD, LGN_NEED_YN, SORT_SEQ, USE_YN, REG_DT)
SELECT 'USER', P.MENU_SN, T.NM, T.URL, NULL, 'Y', T.SEQ, 'Y', NOW(6)
  FROM MENU_INFO P
 CROSS JOIN (SELECT '프로필 수정' NM,    '/mypage/profile'      URL, 1 SEQ
       UNION ALL SELECT '예약 현황 관리','/mypage/reservations', 2
       UNION ALL SELECT '찜 관리',       '/mypage/favorites',    3
       UNION ALL SELECT '후기 관리',     '/mypage/reviews',      4
       UNION ALL SELECT '쿠폰함',        '/mypage/coupons',      5
       UNION ALL SELECT '설정',          '/mypage/settings',     6) T
 WHERE P.MENU_CHNL_CD = 'USER' AND P.MENU_NM = '마이페이지' AND P.URL_ADDR IS NULL AND P.DEL_YN = 'N';

-- ----------------------------------------------------------------------------
-- 2. 파트너 (PARTNER)
-- ----------------------------------------------------------------------------
INSERT INTO MENU_INFO (MENU_CHNL_CD, UPPER_MENU_SN, MENU_NM, URL_ADDR, AUTH_CD, LGN_NEED_YN, SORT_SEQ, USE_YN, REG_DT) VALUES
    ('PARTNER', NULL, '로그인',    '/login',     NULL, 'N', 1, 'Y', NOW(6)),
    ('PARTNER', NULL, '회원 가입', '/signup',    NULL, 'N', 2, 'Y', NOW(6)),
    ('PARTNER', NULL, '대시보드',  '/dashboard', NULL, 'Y', 3, 'Y', NOW(6)),
    ('PARTNER', NULL, '예약 관리', NULL,         NULL, 'Y', 4, 'Y', NOW(6)),
    ('PARTNER', NULL, '고택 관리', NULL,         NULL, 'Y', 5, 'Y', NOW(6)),
    ('PARTNER', NULL, '문의 관리', NULL,         NULL, 'Y', 6, 'Y', NOW(6)),
    ('PARTNER', NULL, '정산',      NULL,         NULL, 'Y', 7, 'Y', NOW(6)),
    ('PARTNER', NULL, '통계',      '/stats',     NULL, 'Y', 8, 'Y', NOW(6));

INSERT INTO MENU_INFO (MENU_CHNL_CD, UPPER_MENU_SN, MENU_NM, URL_ADDR, AUTH_CD, LGN_NEED_YN, SORT_SEQ, USE_YN, REG_DT)
SELECT 'PARTNER', P.MENU_SN, T.NM, T.URL, NULL, 'Y', T.SEQ, 'Y', NOW(6)
  FROM MENU_INFO P
  JOIN (SELECT '예약 관리' PARENT, '예약 현황 캘린더' NM, '/reservations/calendar' URL, 1 SEQ
  UNION ALL SELECT '예약 관리', '예약 리스트',     '/reservations',          2
  UNION ALL SELECT '예약 관리', '예약 수기 등록',  '/reservations/manual',   3
  UNION ALL SELECT '고택 관리', '고택 정보 수정',  '/house',                 1
  UNION ALL SELECT '고택 관리', '요금 관리',       '/house/rates',           2
  UNION ALL SELECT '문의 관리', '문의 리스트',     '/inquiries',             1
  UNION ALL SELECT '문의 관리', '문의 설정',       '/inquiries/settings',    2
  UNION ALL SELECT '정산',      '정산 리스트',     '/settlements',           1
  UNION ALL SELECT '정산',      '정산 통계',       '/settlements/stats',     2) T
    ON T.PARENT = P.MENU_NM
 WHERE P.MENU_CHNL_CD = 'PARTNER' AND P.URL_ADDR IS NULL AND P.DEL_YN = 'N';

-- ----------------------------------------------------------------------------
-- 3. 운영자 (OPERATOR) — 메뉴=VIEW 권한 기준. 미확정 권한은 NULL(전부 노출, 확정 시 새 V로 UPDATE)
--    시스템 관리·직원 관리 = OPERATOR_MNG 예약(역할 부여 전까지 비노출 — SPV 전용 확정 메뉴)
-- ----------------------------------------------------------------------------
INSERT INTO MENU_INFO (MENU_CHNL_CD, UPPER_MENU_SN, MENU_NM, URL_ADDR, AUTH_CD, LGN_NEED_YN, SORT_SEQ, USE_YN, REG_DT) VALUES
    ('OPERATOR', NULL, '로그인',      '/login',     NULL,           'N', 1, 'Y', NOW(6)),
    ('OPERATOR', NULL, '대시보드',    '/dashboard', NULL,           'Y', 2, 'Y', NOW(6)),
    ('OPERATOR', NULL, '파트너 관리', NULL,         'PRTNR_VIEW',   'Y', 3, 'Y', NOW(6)),
    ('OPERATOR', NULL, '상품 관리',   NULL,         NULL,           'Y', 4, 'Y', NOW(6)),
    ('OPERATOR', NULL, '정산 관리',   NULL,         NULL,           'Y', 5, 'Y', NOW(6)),
    ('OPERATOR', NULL, '회원 관리',   NULL,         NULL,           'Y', 6, 'Y', NOW(6)),
    ('OPERATOR', NULL, '마케팅 관리', NULL,         NULL,           'Y', 7, 'Y', NOW(6)),
    ('OPERATOR', NULL, '시스템 관리', NULL,         'OPERATOR_MNG', 'Y', 8, 'Y', NOW(6));

INSERT INTO MENU_INFO (MENU_CHNL_CD, UPPER_MENU_SN, MENU_NM, URL_ADDR, AUTH_CD, LGN_NEED_YN, SORT_SEQ, USE_YN, REG_DT)
SELECT 'OPERATOR', P.MENU_SN, T.NM, T.URL, T.AUTH, 'Y', T.SEQ, 'Y', NOW(6)
  FROM MENU_INFO P
  JOIN (SELECT '파트너 관리' PARENT, '파트너 등록' NM, '/partners/insert' URL, 'PRTNR_VIEW' AUTH, 1 SEQ
  UNION ALL SELECT '파트너 관리', '파트너 리스트',      '/partners',            'PRTNR_VIEW',   2
  UNION ALL SELECT '파트너 관리', '파트너 상세 정보',   '/partners/detail',     'PRTNR_VIEW',   3
  UNION ALL SELECT '상품 관리',   '상품 등록',          '/products/insert',     NULL,           1
  UNION ALL SELECT '상품 관리',   '상품 리스트',        '/products',            NULL,           2
  UNION ALL SELECT '상품 관리',   '상품 관리',          '/products/manage',     NULL,           3
  UNION ALL SELECT '정산 관리',   '정산 리스트(파트너별)', '/settlements',      NULL,           1
  UNION ALL SELECT '정산 관리',   '정산 통계',          '/settlements/stats',   NULL,           2
  UNION ALL SELECT '정산 관리',   '정산 설정',          '/settlements/settings',NULL,           3
  UNION ALL SELECT '회원 관리',   '회원 리스트',        '/accounts',            NULL,           1
  UNION ALL SELECT '회원 관리',   '회원 상세',          '/accounts/detail',     NULL,           2
  UNION ALL SELECT '마케팅 관리', 'DM 발송 관리',       '/marketing/dm',        NULL,           1
  UNION ALL SELECT '마케팅 관리', '쿠폰관리',           '/marketing/coupons',   NULL,           2
  UNION ALL SELECT '마케팅 관리', '공지사항 관리',      '/marketing/notices',   NULL,           3
  UNION ALL SELECT '시스템 관리', '권한 관리',          '/system/roles',        'OPERATOR_MNG', 1
  UNION ALL SELECT '시스템 관리', '직원 관리',          '/system/operators',    'OPERATOR_MNG', 2
  UNION ALL SELECT '시스템 관리', '약관 관리',          '/system/terms',        NULL,           3) T
    ON T.PARENT = P.MENU_NM
 WHERE P.MENU_CHNL_CD = 'OPERATOR' AND P.URL_ADDR IS NULL AND P.DEL_YN = 'N';
