package egovframework.eeum.account.service.member;

import egovframework.eeum.auth.vo.AuthUser;
import egovframework.eeum.base.response.PageResponse;
import egovframework.eeum.account.web.dto.MemberDetailResponse;
import egovframework.eeum.account.web.dto.MemberListItemResponse;
import egovframework.eeum.account.web.dto.MemberSearchRequest;
import egovframework.eeum.account.web.dto.MemberStatusChangeRequest;

/**
 * 일반회원 관리 서비스(운영자 콘솔) — 조회/상태관리. 대상은 USER_TYPE_CD='MEMBER' 고정.
 *
 * <p>회원은 자가가입이라 운영자가 생성·비번재설정은 하지 않는다. 상태변경(정지/해제/탈퇴처리)만 제공.
 */
public interface MemberService {

	/** 회원 목록(마스킹 노출, 서버사이드 페이징) — 목록조회 감사기록. */
	PageResponse<MemberListItemResponse> list(MemberSearchRequest request);

	/** 회원 상세(개인정보 전체) — PII 접근 감사기록. 없으면 404. */
	MemberDetailResponse detail(long userSn);

	/** 상태변경(정지/해제/탈퇴처리) — 상태머신 검증 + 사유 감사기록. */
	void changeStatus(MemberStatusChangeRequest request, AuthUser operator);
}
