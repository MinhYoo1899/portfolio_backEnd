package egovframework.eeum.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import egovframework.eeum.account.exception.AccountErrorCode;
import egovframework.eeum.account.service.member.dao.MemberMapper;
import egovframework.eeum.account.service.member.impl.MemberServiceImpl;
import egovframework.eeum.account.vo.AccountVO;
import egovframework.eeum.account.web.dto.MemberDetailResponse;
import egovframework.eeum.account.web.dto.MemberListItemResponse;
import egovframework.eeum.account.web.dto.MemberSearchRequest;
import egovframework.eeum.account.web.dto.MemberStatusChangeRequest;
import egovframework.eeum.auth.vo.AuthUser;
import egovframework.eeum.base.audit.AuditRecorder;
import egovframework.eeum.base.exception.BusinessException;

/**
 * 일반회원 관리 검증 — 계정유형 격리(404)/상태머신(정지·해제·탈퇴)/마스킹/감사(DB 없이 mock).
 */
class MemberServiceVerification {

	private final MemberMapper memberMapper = mock(MemberMapper.class);
	private final AuditRecorder auditRecorder = mock(AuditRecorder.class);
	private final MemberServiceImpl service = new MemberServiceImpl(memberMapper, auditRecorder);

	private static AuthUser operator(long sn) {
		return new AuthUser(sn, "op@eeum.kr", "운영자", "OPERATOR", "ACTIVE", List.of("MEMBER_MNG"));
	}

	private static AccountVO member(long userSn, String stts) {
		AccountVO vo = new AccountVO();
		vo.setUserSn(userSn);
		vo.setUserTypeCd("MEMBER");
		vo.setAcntStts(stts);
		vo.setEmlAddr("hong@example.com");
		vo.setUserNm("홍길동");
		vo.setMblTelno("01012345678");
		return vo;
	}

	@Test
	@DisplayName("목록 — 조회 후 목록조회 감사기록(trgtId=null)")
	void listAudits() {
		MemberSearchRequest req = new MemberSearchRequest();
		when(memberMapper.countMemberList(null, null)).thenReturn(1L);
		when(memberMapper.selectMemberListOffset(null, null, 20, 0)).thenReturn(List.of(member(1L, "ACTIVE")));

		service.list(req);

		verify(memberMapper).selectMemberListOffset(null, null, 20, 0);
		verify(auditRecorder).recordSuccess(any(), eq("MEMBER"), any());
	}

	@Test
	@DisplayName("목록 — 한 글자 검색어는 무시(PII 탐색 방지, keyword=null 전달)")
	void listIgnoresShortKeyword() {
		MemberSearchRequest req = new MemberSearchRequest();
		req.setKeyword("홍");
		when(memberMapper.countMemberList(null, null)).thenReturn(0L);
		when(memberMapper.selectMemberListOffset(null, null, 20, 0)).thenReturn(List.of());

		service.list(req);

		verify(memberMapper).countMemberList(null, null);
		verify(memberMapper).selectMemberListOffset(null, null, 20, 0);
	}

	@Test
	@DisplayName("상세 — 없으면(비MEMBER/삭제 포함) 404")
	void detailNotFound() {
		when(memberMapper.selectMemberDetail(5L)).thenReturn(null);
		BusinessException e = assertThrows(BusinessException.class, () -> service.detail(5L));
		assertEquals(AccountErrorCode.MEMBER_NOT_FOUND, e.getErrorCode());
	}

	@Test
	@DisplayName("상세 — 존재하면 개인정보 전체 노출")
	void detailFull() {
		when(memberMapper.selectMemberDetail(5L)).thenReturn(member(5L, "ACTIVE"));
		MemberDetailResponse res = service.detail(5L);
		assertEquals("hong@example.com", res.emlAddr());
		assertEquals("01012345678", res.mblTelno());
	}

	@Test
	@DisplayName("상태변경 — 정지(ACTIVE→SUSPENDED) 성공 + 사유 감사기록")
	void suspendFromActive() {
		when(memberMapper.selectMemberForUpdate(5L)).thenReturn(member(5L, "ACTIVE"));
		when(memberMapper.updateMemberStatus(5L, "SUSPENDED", 9L)).thenReturn(1);

		service.changeStatus(new MemberStatusChangeRequest("5", "SUSPENDED", "신고 누적"), operator(9));

		verify(memberMapper).updateMemberStatus(5L, "SUSPENDED", 9L);
		verify(auditRecorder).recordSuccess(any(), eq("MEMBER"), eq("5"));
	}

	@Test
	@DisplayName("상태변경 — 해제(SUSPENDED→ACTIVE) 성공")
	void releaseFromSuspended() {
		when(memberMapper.selectMemberForUpdate(5L)).thenReturn(member(5L, "SUSPENDED"));
		when(memberMapper.updateMemberStatus(5L, "ACTIVE", 9L)).thenReturn(1);
		service.changeStatus(new MemberStatusChangeRequest("5", "ACTIVE", "정상화"), operator(9));
		verify(memberMapper).updateMemberStatus(5L, "ACTIVE", 9L);
	}

	@Test
	@DisplayName("상태변경 — 탈퇴처리(ACTIVE→WITHDRAWN) 성공")
	void withdrawFromActive() {
		when(memberMapper.selectMemberForUpdate(5L)).thenReturn(member(5L, "ACTIVE"));
		when(memberMapper.updateMemberStatus(5L, "WITHDRAWN", 9L)).thenReturn(1);
		service.changeStatus(new MemberStatusChangeRequest("5", "WITHDRAWN", "본인 요청"), operator(9));
		verify(memberMapper).updateMemberStatus(5L, "WITHDRAWN", 9L);
	}

	@Test
	@DisplayName("상태변경 — 동일상태(ACTIVE→ACTIVE)는 409, 갱신 진입 안 함")
	void sameStateRejected() {
		when(memberMapper.selectMemberForUpdate(5L)).thenReturn(member(5L, "ACTIVE"));
		BusinessException e = assertThrows(BusinessException.class,
				() -> service.changeStatus(new MemberStatusChangeRequest("5", "ACTIVE", "사유"), operator(9)));
		assertEquals(AccountErrorCode.MEMBER_STATUS_NOT_ALLOWED, e.getErrorCode());
		verify(memberMapper, never()).updateMemberStatus(anyLong(), anyString(), anyLong());
	}

	@Test
	@DisplayName("상태변경 — 탈퇴(WITHDRAWN) 계정은 비가역(409), 갱신 진입 안 함")
	void fromWithdrawnRejected() {
		when(memberMapper.selectMemberForUpdate(5L)).thenReturn(member(5L, "WITHDRAWN"));
		assertThrows(BusinessException.class,
				() -> service.changeStatus(new MemberStatusChangeRequest("5", "ACTIVE", "사유"), operator(9)));
		verify(memberMapper, never()).updateMemberStatus(anyLong(), anyString(), anyLong());
	}

	@Test
	@DisplayName("상태변경 — 대상 없으면(비MEMBER 포함) 404")
	void changeStatusNotFound() {
		when(memberMapper.selectMemberForUpdate(5L)).thenReturn(null);
		BusinessException e = assertThrows(BusinessException.class,
				() -> service.changeStatus(new MemberStatusChangeRequest("5", "SUSPENDED", "사유"), operator(9)));
		assertEquals(AccountErrorCode.MEMBER_NOT_FOUND, e.getErrorCode());
		verify(memberMapper, never()).updateMemberStatus(anyLong(), anyString(), anyLong());
	}

	@Test
	@DisplayName("목록 마스킹 — 이메일/휴대폰 부분마스킹")
	void listMasking() {
		MemberListItemResponse item = MemberListItemResponse.from(member(1L, "ACTIVE"));
		assertEquals("h***@example.com", item.emlAddr());
		assertEquals("010-****-5678", item.mblTelno());
	}
}
