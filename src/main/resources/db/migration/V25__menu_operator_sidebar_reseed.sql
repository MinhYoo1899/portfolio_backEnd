-- =============================================================================
-- V25 : 운영자(OPERATOR) 사이드바 메뉴 재시드 — 프론트 스펙(2단계 그룹/항목, Phosphor 아이콘)
-- 결정(사용자): 전체 노출(AUTH_CD=NULL — 게이팅은 각 기능·권한 생길 때 적용, 지금은 플레이스홀더 포함).
--   sub-page(파트너 등록/리스트/상세, 상품 등록/리스트 등)는 사이드바 메뉴가 아니라 미시드.
--   1단계=그룹라벨(URL/ICON=NULL, UPPER=NULL), 2단계=항목(URL/ICON, UPPER=그룹SN).
-- 아이콘: Phosphor 클래스 문자열 그대로 ICON_NM 저장(프론트 <i class="ph {ICON_NM}">).
-- 명명: 감사 insert 수정일=등록일([[convention-mdfcndt-equals-regdt-on-insert]]).
-- =============================================================================

-- 기존 OPERATOR 메뉴 전부 정리(soft delete) 후 새 구조로 재시드
UPDATE MENU_INFO SET DEL_YN = 'Y', MDFCN_DT = UTC_TIMESTAMP(6)
 WHERE MENU_CHNL_CD = 'OPERATOR' AND DEL_YN = 'N';

-- ── 1. HOME ──────────────────────────────────────────────────────────────────
INSERT INTO MENU_INFO (MENU_CHNL_CD, UPPER_MENU_SN, MENU_NM, URL_ADDR, ICON_NM, AUTH_CD, LGN_NEED_YN, SORT_SEQ, REG_DT, MDFCN_DT)
VALUES ('OPERATOR', NULL, 'HOME', NULL, NULL, NULL, 'Y', 1, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6));
SET @home = LAST_INSERT_ID();
INSERT INTO MENU_INFO (MENU_CHNL_CD, UPPER_MENU_SN, MENU_NM, URL_ADDR, ICON_NM, AUTH_CD, LGN_NEED_YN, SORT_SEQ, REG_DT, MDFCN_DT) VALUES
 ('OPERATOR', @home, '대시보드', '/', 'ph-squares-four', NULL, 'Y', 1, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6));

-- ── 2. 운영 ──────────────────────────────────────────────────────────────────
INSERT INTO MENU_INFO (MENU_CHNL_CD, UPPER_MENU_SN, MENU_NM, URL_ADDR, ICON_NM, AUTH_CD, LGN_NEED_YN, SORT_SEQ, REG_DT, MDFCN_DT)
VALUES ('OPERATOR', NULL, '운영', NULL, NULL, NULL, 'Y', 2, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6));
SET @ops = LAST_INSERT_ID();
INSERT INTO MENU_INFO (MENU_CHNL_CD, UPPER_MENU_SN, MENU_NM, URL_ADDR, ICON_NM, AUTH_CD, LGN_NEED_YN, SORT_SEQ, REG_DT, MDFCN_DT) VALUES
 ('OPERATOR', @ops, '파트너 관리', '/partner', 'ph-storefront', NULL, 'Y', 1, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
 ('OPERATOR', @ops, '상품 관리', '/product', 'ph-house', NULL, 'Y', 2, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
 ('OPERATOR', @ops, '회원 관리', '/member', 'ph-users', NULL, 'Y', 3, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6));

-- ── 3. 예약 ──────────────────────────────────────────────────────────────────
INSERT INTO MENU_INFO (MENU_CHNL_CD, UPPER_MENU_SN, MENU_NM, URL_ADDR, ICON_NM, AUTH_CD, LGN_NEED_YN, SORT_SEQ, REG_DT, MDFCN_DT)
VALUES ('OPERATOR', NULL, '예약', NULL, NULL, NULL, 'Y', 3, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6));
SET @rsv = LAST_INSERT_ID();
INSERT INTO MENU_INFO (MENU_CHNL_CD, UPPER_MENU_SN, MENU_NM, URL_ADDR, ICON_NM, AUTH_CD, LGN_NEED_YN, SORT_SEQ, REG_DT, MDFCN_DT) VALUES
 ('OPERATOR', @rsv, '예약 관리', '/reservation', 'ph-calendar-check', NULL, 'Y', 1, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
 ('OPERATOR', @rsv, '콘텐츠 관리', '/content', 'ph-article', NULL, 'Y', 2, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
 ('OPERATOR', @rsv, '정산 관리', '/settlement', 'ph-receipt', NULL, 'Y', 3, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
 ('OPERATOR', @rsv, '문의', '/inquiry', 'ph-chat-dots', NULL, 'Y', 4, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6));

-- ── 4. 마케팅 ────────────────────────────────────────────────────────────────
INSERT INTO MENU_INFO (MENU_CHNL_CD, UPPER_MENU_SN, MENU_NM, URL_ADDR, ICON_NM, AUTH_CD, LGN_NEED_YN, SORT_SEQ, REG_DT, MDFCN_DT)
VALUES ('OPERATOR', NULL, '마케팅', NULL, NULL, NULL, 'Y', 4, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6));
SET @mkt = LAST_INSERT_ID();
INSERT INTO MENU_INFO (MENU_CHNL_CD, UPPER_MENU_SN, MENU_NM, URL_ADDR, ICON_NM, AUTH_CD, LGN_NEED_YN, SORT_SEQ, REG_DT, MDFCN_DT) VALUES
 ('OPERATOR', @mkt, 'DM 관리', '/dm', 'ph-paper-plane-tilt', NULL, 'Y', 1, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
 ('OPERATOR', @mkt, '쿠폰 관리', '/coupon', 'ph-ticket', NULL, 'Y', 2, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
 ('OPERATOR', @mkt, '공지사항', '/notice', 'ph-megaphone', NULL, 'Y', 3, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6));

-- ── 5. 시스템 ────────────────────────────────────────────────────────────────
INSERT INTO MENU_INFO (MENU_CHNL_CD, UPPER_MENU_SN, MENU_NM, URL_ADDR, ICON_NM, AUTH_CD, LGN_NEED_YN, SORT_SEQ, REG_DT, MDFCN_DT)
VALUES ('OPERATOR', NULL, '시스템', NULL, NULL, NULL, 'Y', 5, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6));
SET @sys = LAST_INSERT_ID();
INSERT INTO MENU_INFO (MENU_CHNL_CD, UPPER_MENU_SN, MENU_NM, URL_ADDR, ICON_NM, AUTH_CD, LGN_NEED_YN, SORT_SEQ, REG_DT, MDFCN_DT) VALUES
 ('OPERATOR', @sys, '권한 관리', '/auth', 'ph-shield-check', NULL, 'Y', 1, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
 ('OPERATOR', @sys, '약관 관리', '/terms', 'ph-file-text', NULL, 'Y', 2, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
 ('OPERATOR', @sys, '시스템 설정', '/system', 'ph-gear', NULL, 'Y', 3, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6));
