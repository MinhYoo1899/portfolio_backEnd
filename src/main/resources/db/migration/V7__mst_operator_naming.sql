-- =============================================================================
-- V7 : 기준 테이블 _MST 접미 + 운영자 명칭 MASTER → OPERATOR (2026-06-12 사용자 확정)
-- 규칙: ① 지정 기준 테이블 4종은 `_MST`(마스터/기준) 접미 — 테이블 COMMENT 에 정체 명기
--       ② 운영자 계정 유형 명칭 = OPERATOR (기존 '마스터/MASTER/MSTR' 폐기 — _MST 접미와의 혼동 제거)
--       ③ 컬럼명은 변경 없음(스템 유지: AUTH_*, ROLE_*, PRTNR_*, TRMS_*) — 프론트 계약(필드명) 보호
-- ⚠ 구버전 코드와 비호환(테이블명+코드값 변경) — 적용 후 신코드 배포 전까지 기존 서버 API 오류(계획된 중단).
-- ⚠ 기존 발급 토큰의 chnl=MASTER 세션은 무효화됨(재로그인 필요 — dev 수용).
-- =============================================================================

-- ----------------------------------------------------------------------------
-- 1. 기준 테이블 개명 (_MST) + 운영자 상세 (FK 는 RENAME TABLE 시 자동 추종)
-- ----------------------------------------------------------------------------
RENAME TABLE
    AUTH          TO AUTH_MST,
    ROLE          TO ROLE_MST,
    PRTNR         TO PRTNR_MST,
    TRMS          TO TRMS_MST,
    MSTR_DTL_INFO TO OPERATOR_DTL_INFO;

-- ----------------------------------------------------------------------------
-- 2. 테이블 COMMENT — 무슨 테이블인지 명기 (사용자 지시)
-- ----------------------------------------------------------------------------
ALTER TABLE AUTH_MST          COMMENT = '권한 기준(MST) — 시스템 기능 권한(AUTH_CD) 정의';
ALTER TABLE ROLE_MST          COMMENT = '역할 기준(MST) — 계정에 부여하는 역할 정의';
ALTER TABLE PRTNR_MST         COMMENT = '파트너 기준(MST) — 고택 사업자(파트너) 정보';
ALTER TABLE TRMS_MST          COMMENT = '약관 기준(MST) — 약관 문서 버전 원본(증적, 행 수정 금지)';
ALTER TABLE OPERATOR_DTL_INFO COMMENT = '운영자 상세정보 — 운영자(OPERATOR) 계정의 부가 정보(1:1)';

-- ----------------------------------------------------------------------------
-- 3. 스템 바뀐 제약 이름 정리 (MSTR → OPERATOR)
-- ----------------------------------------------------------------------------
ALTER TABLE OPERATOR_DTL_INFO DROP FOREIGN KEY FK_MSTR_DTL_INFO_USER;
ALTER TABLE OPERATOR_DTL_INFO ADD CONSTRAINT FK_OPERATOR_DTL_INFO_USER FOREIGN KEY (USER_SN) REFERENCES USER_ACNT (USER_SN);

-- ----------------------------------------------------------------------------
-- 4. 운영자 코드값 MASTER → OPERATOR (데이터 변경)
-- ----------------------------------------------------------------------------
UPDATE COMM_CD SET CD = 'OPERATOR', CD_NM = '운영자', MDFCN_DT = NOW(6)
 WHERE GROUP_CD = 'USER_TYPE' AND CD = 'MASTER';
UPDATE COMM_CD SET CD = 'OPERATOR', CD_NM = '운영자 SPA', MDFCN_DT = NOW(6)
 WHERE GROUP_CD = 'MENU_CHNL' AND CD = 'MASTER';

UPDATE ROLE_MST SET ROLE_CD = 'OPERATOR', ROLE_NM = '운영자', MDFCN_DT = NOW(6)
 WHERE ROLE_CD = 'MASTER' AND DEL_YN = 'N';

UPDATE AUTH_MST SET AUTH_CD = 'CHNL_LOGIN_OPERATOR', AUTH_NM = '로그인 채널 자격: 운영자 SPA', MDFCN_DT = NOW(6)
 WHERE AUTH_CD = 'CHNL_LOGIN_MASTER' AND DEL_YN = 'N';

UPDATE MENU_INFO SET MENU_CHNL_CD = 'OPERATOR', MDFCN_DT = NOW(6)
 WHERE MENU_CHNL_CD = 'MASTER' AND DEL_YN = 'N';

UPDATE USER_ACNT SET USER_TYPE_CD = 'OPERATOR', MDFCN_DT = NOW(6)
 WHERE USER_TYPE_CD = 'MASTER' AND DEL_YN = 'N';
