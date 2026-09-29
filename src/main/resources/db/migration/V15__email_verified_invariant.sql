-- =============================================================================
-- V15 : 이메일 검증 불변식 — EML_VRFD_YN='Y' ⇒ EML_VRFD_DT/EML_VRFC_SRC NOT NULL (2026-06-22)
--   배경(codex 교차검토): V14 도입 후 소셜 가입(email_verified=true)은 EML_VRFD_YN='Y'/SRC만
--   기록하고 EML_VRFD_DT 를 비워 둘 수 있었다. 매퍼 insert 는 Y면 DT도 함께 넣도록 고쳤고(코드),
--   여기서는 (1) 기존 적재 데이터 backfill 후 (2) "Y면 DT·SRC 모두 존재" 를 DB CHECK 로 강제한다.
--   완료시각 컬럼이 정말 비어 있던 행은 보수적으로 가입시각(REG_DT)으로, 출처 미상은 LEGACY_ASSUMED 로 보정.
-- =============================================================================

-- (1) backfill — Y 인데 DT/SRC 가 비어 있는 기존 행 보정 (CHECK 추가 전 선행 필수)
UPDATE USER_ACNT
   SET EML_VRFD_DT = COALESCE(EML_VRFD_DT, REG_DT),
       EML_VRFC_SRC = COALESCE(EML_VRFC_SRC, 'LEGACY_ASSUMED')
 WHERE EML_VRFD_YN = 'Y'
   AND (EML_VRFD_DT IS NULL OR EML_VRFC_SRC IS NULL);

-- (2) 불변식 — Y면 검증일시·출처가 반드시 있어야 한다 (N이면 NULL 허용)
ALTER TABLE USER_ACNT
    ADD CONSTRAINT CK_USER_ACNT_EML_VRFD_INVARIANT
        CHECK (EML_VRFD_YN <> 'Y' OR (EML_VRFD_DT IS NOT NULL AND EML_VRFC_SRC IS NOT NULL));
