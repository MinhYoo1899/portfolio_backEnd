-- =============================================================================
-- V14 : 이메일 소유확인(가입 이메일 인증) — USER_ACNT 검증 상태 컬럼 (2026-06-19)
--   완화형 정책(codex 교차검토): 가입 즉시 ACTIVE 유지 + EML_VRFD_YN 으로 검증여부를
--   ACNT_STTS(계정 생명주기)와 분리 추적. 미검증이어도 로그인 허용, 중요 기능(비번재설정 등)은
--   백엔드에서 검증 게이트로 강제(각 기능 시점에 적용).
--   기존 계정은 N(미검증) 기본. 소셜은 provider email_verified=true 면 가입 시 Y/GOOGLE.
--   인증 챌린지(6자리 코드)는 Redis(HMAC 저장·TTL·1회성) — DB에는 최종 검증 상태만 둔다.
-- =============================================================================

ALTER TABLE USER_ACNT
    ADD COLUMN EML_VRFD_YN CHAR(1) NOT NULL DEFAULT 'N'
        COMMENT '이메일검증여부(Y/N) — ACNT_STTS와 분리된 이메일 소유확인 상태' AFTER MBL_TELNO_VRFC_YN,
    ADD COLUMN EML_VRFD_DT DATETIME(6) NULL
        COMMENT '이메일검증완료일시(UTC)' AFTER EML_VRFD_YN,
    ADD COLUMN EML_VRFC_SRC VARCHAR(30) NULL
        COMMENT '이메일검증출처(EMAIL_CODE/GOOGLE/ADMIN/LEGACY_ASSUMED)' AFTER EML_VRFD_DT;

-- 검증여부는 Y/N 만 허용 (codex SHOULD — 잘못된 값 유입 차단)
ALTER TABLE USER_ACNT
    ADD CONSTRAINT CK_USER_ACNT_EML_VRFD_YN CHECK (EML_VRFD_YN IN ('Y', 'N'));
