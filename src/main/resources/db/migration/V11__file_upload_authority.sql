-- =============================================================================
-- V11 : 파일 업로드 권한(FILE_UPLOAD) — 운영자·파트너 한정 (D-2 첨부파일 저장소 정책)
--   공개 이미지 업로드를 "로그인한 누구나"에서 운영자(STAFF/SUPERVISOR)·파트너로 제한
--   (저장소 고갈·공개 파일 호스팅 악용 방어 — codex 상의 2026-06-18).
--   일반 사용자 첨부(문의 등 비공개)는 해당 기능 구현 시 "비공개 스코프 + 접근제어 다운로드"로 별도 설계.
--   타임존 표준(docs/timezone-standard.md): 감사 instant 는 UTC_TIMESTAMP(6).
-- =============================================================================

-- 1) 권한 정의 (멱등 — active 기준: soft-deleted 행이 있어도 새 active 권한을 생성, codex 교차검토)
INSERT INTO AUTH_MST (AUTH_CD, AUTH_NM, USE_YN, REG_DT)
SELECT 'FILE_UPLOAD', '파일 업로드(공개 이미지)', 'Y', UTC_TIMESTAMP(6)
 WHERE NOT EXISTS (SELECT 1 FROM AUTH_MST WHERE AUTH_CD = 'FILE_UPLOAD' AND DEL_YN = 'N');

-- 1-1) 기존 active 권한이 비활성(USE_YN<>'Y')이면 활성화 — 재실행/재사용 안전(권한 조회는 USE_YN='Y' 전제)
UPDATE AUTH_MST SET USE_YN = 'Y'
 WHERE AUTH_CD = 'FILE_UPLOAD' AND DEL_YN = 'N' AND USE_YN <> 'Y';

-- 2) 역할 매핑: 운영자(일반직원·수퍼바이저) + 파트너 (멱등)
INSERT INTO ROLE_AUTH (ROLE_SN, AUTH_SN, REG_DT)
SELECT R.ROLE_SN, A.AUTH_SN, UTC_TIMESTAMP(6)
  FROM ROLE_MST R
  JOIN AUTH_MST A ON A.AUTH_CD = 'FILE_UPLOAD' AND A.DEL_YN = 'N'
 WHERE R.ROLE_CD IN ('OPERATOR_STAFF', 'OPERATOR_SUPERVISOR', 'PARTNER') AND R.DEL_YN = 'N'
   AND NOT EXISTS (SELECT 1 FROM ROLE_AUTH RA
                    WHERE RA.ROLE_SN = R.ROLE_SN AND RA.AUTH_SN = A.AUTH_SN AND RA.DEL_YN = 'N');

-- 3) SCAN_STTS_CD 허용값에 NOT_REQUIRED 명시(코멘트 갱신) — AV 미연동 시 검사면제를 NULL 대신 명시 기록
ALTER TABLE ATCH_FILE MODIFY COLUMN SCAN_STTS_CD VARCHAR(20) NULL
    COMMENT '보안검사상태코드(NOT_REQUIRED/PENDING/PASSED/FAILED — AV 미연동 시 NOT_REQUIRED)';

-- 4) 업로드 쿼터 조회 인덱스 — (RGTR_SN, REG_DT) 최근 24시간 본인 업로드 집계 가속(데이터 증가 시 full-scan 방지, codex 교차검토)
ALTER TABLE ATCH_FILE ADD INDEX IX_ATCH_FILE_RGTR_REG (RGTR_SN, REG_DT);
