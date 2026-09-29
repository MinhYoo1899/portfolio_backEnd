package egovframework.eeum.account.service.operator;

import egovframework.eeum.account.web.dto.OperatorCreatedResponse;
import egovframework.eeum.account.web.dto.OperatorDetailResponse;
import egovframework.eeum.account.web.dto.OperatorInsertRequest;
import egovframework.eeum.account.web.dto.OperatorMeResponse;
import egovframework.eeum.account.web.dto.OperatorPasswordResetRequest;
import egovframework.eeum.account.web.dto.OperatorRoleChangeRequest;
import egovframework.eeum.account.web.dto.OperatorSearchRequest;
import egovframework.eeum.account.web.dto.OperatorSetupPasswordRequest;
import egovframework.eeum.account.web.dto.OperatorStatusChangeRequest;
import egovframework.eeum.account.web.dto.OperatorUpdateRequest;
import egovframework.eeum.base.response.PageResponse;

/**
 * 운영자 계정관리 서비스 — 직원 생성/조회/역할·상태 변경/비번 초기화/설정.
 *
 * <p>보안 불변식(전부 강제): 대상은 OPERATOR 한정, 허용역할(STAFF/SUPERVISOR)만, SUPER 보호,
 * SUPERVISOR↔STAFF 동시부여, 자기 자신 보호, 마지막 SUPERVISOR 보호, 비활성·강등·초기화 시 세션 무효화.
 */
public interface OperatorService {

	/** 직원 생성 — 계정(PENDING)+상세+역할+설정토큰. 응답에 1회성 토큰/URL. */
	OperatorCreatedResponse insert(OperatorInsertRequest request, long actorUserSn);

	/** 운영자 목록(서버페이징, 상태/역할 필터). */
	PageResponse<egovframework.eeum.account.web.dto.OperatorListItemResponse> list(OperatorSearchRequest request);

	/** 운영자 상세(OPERATOR 아니면 404). 연락처는 마스킹. */
	OperatorDetailResponse detail(long userSn);

	/** 운영자 상세 수정(부서/직위/연락처 — 생략 시 보존). 전화번호는 정규화·암호화·해시 동시 갱신. */
	void update(OperatorUpdateRequest request, long actorUserSn);

	/** 본인 정보 조회({@code /operator/me}) — 대상=토큰 본인. 연락처 전체 노출. OPERATOR 아니면 404. */
	OperatorMeResponse me(long userSn);

	/** 역할 변경 — STAFF↔SUPERVISOR(동시부여·강등 잔류). */
	void changeRole(OperatorRoleChangeRequest request, long actorUserSn);

	/** 상태 변경 — ACTIVE/SUSPENDED(조건부 전이 + 불변식). */
	void changeStatus(OperatorStatusChangeRequest request, long actorUserSn);

	/** 비밀번호 초기화 — 설정토큰 재발급(1회 노출) + 세션 무효화. */
	OperatorCreatedResponse resetPassword(OperatorPasswordResetRequest request, long actorUserSn);

	/** 비밀번호 설정(비인증) — 토큰 소비 + ENPSWD 설정 + PENDING→ACTIVE. 감사 대상 식별을 위해 설정된 userSn 반환. */
	Long setupPassword(OperatorSetupPasswordRequest request);
}
