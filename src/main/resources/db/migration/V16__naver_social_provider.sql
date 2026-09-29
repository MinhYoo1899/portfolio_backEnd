-- =============================================================================
-- V16 : 소셜 제공자에 네이버 추가 (2026-06-22)
--   SOCIAL_PRVDR 공통코드에 NAVER 추가. 네이버 로그인은 레거시 OAuth2.0(access_token + /v1/nid/me)
--   경로로 연동한다(codex 교차검토 — OIDC 섹션은 있으나 런타임 검증 불가로 출시 경로는 OAuth2 확정).
--   식별자=nid/me response.id(앱별 고유). USER_SOCIAL_ACNT 의 PRVDR_CD+PRVDR_USER_ID 유니크로 충분(클라이언트 차원 불요).
-- =============================================================================

-- 공통코드 테이블은 V6 naming refactor 에서 COM_CD → COMM_CD 로 개명됨.
INSERT INTO COMM_CD (GROUP_CD, CD, CD_NM, SORT_SEQ, USE_YN, REG_DT) VALUES
    ('SOCIAL_PRVDR', 'NAVER', '네이버', 4, 'Y', UTC_TIMESTAMP(6));
