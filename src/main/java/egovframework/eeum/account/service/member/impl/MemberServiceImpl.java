package egovframework.eeum.account.service.member.impl;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import egovframework.eeum.account.exception.AccountErrorCode;
import egovframework.eeum.account.service.member.MemberService;
import egovframework.eeum.account.service.member.dao.MemberMapper;
import egovframework.eeum.account.vo.AccountVO;
import egovframework.eeum.account.web.dto.MemberDetailResponse;
import egovframework.eeum.account.web.dto.MemberListItemResponse;
import egovframework.eeum.account.web.dto.MemberSearchRequest;
import egovframework.eeum.account.web.dto.MemberStatusChangeRequest;
import egovframework.eeum.auth.vo.AuthUser;
import egovframework.eeum.base.audit.Audit;
import egovframework.eeum.base.audit.AuditRecorder;
import egovframework.eeum.base.exception.BusinessException;
import egovframework.eeum.base.response.PageResponse;
import lombok.RequiredArgsConstructor;

/**
 * 일반회원 관리 서비스 구현(운영자 콘솔).
 *
 * <p>모든 조회/변경은 매퍼에서 {@code USER_TYPE_CD='MEMBER'} 고정(계정유형 격리). 상태변경은 부모행 선잠금 +
 * 상태머신(정지/해제/탈퇴처리)으로 강제하고, 목록/상세/상태변경 모두 감사기록을 남긴다(PII 접근 추적).
 */
@Service
@RequiredArgsConstructor
public class MemberServiceImpl implements MemberService {

	private static final String STTS_ACTIVE = "ACTIVE";
	private static final String STTS_SUSPENDED = "SUSPENDED";
	private static final String STTS_WITHDRAWN = "WITHDRAWN";
	private static final int KEYWORD_MIN_LEN = 2;

	private final MemberMapper memberMapper;
	private final AuditRecorder auditRecorder;

	@Override
	@Transactional(readOnly = true)
	public PageResponse<MemberListItemResponse> list(MemberSearchRequest request) {
		String status = trimToNull(request.getAcntStts());
		String keyword = normalizeKeyword(request.getKeyword());
		long total = memberMapper.countMemberList(status, keyword);
		List<MemberListItemResponse> items = memberMapper
				.selectMemberListOffset(status, keyword, request.getPageSize(), request.getOffset()).stream()
				.map(MemberListItemResponse::from)
				.toList();
		// 대량 열람 성격 — 검색조건/건수 명시 기록(원본 PII 미기록)
		auditRecorder.recordSuccess(
				String.format("MEMBER_LIST_VIEW {\"page\":%d,\"size\":%d,\"stts\":%s,\"hasKeyword\":%b,\"resultCount\":%d}",
						request.getPage(), request.getPageSize(),
						status == null ? "null" : "\"" + status + "\"", keyword != null, items.size()),
				"MEMBER", null);
		return PageResponse.of(items, total, request.getPage(), request.getPageSize());
	}

	@Override
	@Transactional(readOnly = true)
	@Audit(actn = "MEMBER_DETAIL_VIEW", trgtType = "MEMBER", trgtId = "#userSn")
	public MemberDetailResponse detail(long userSn) {
		AccountVO member = memberMapper.selectMemberDetail(userSn);
		if (member == null) {
			throw new BusinessException(AccountErrorCode.MEMBER_NOT_FOUND);
		}
		return MemberDetailResponse.from(member);
	}

	@Override
	@Transactional
	public void changeStatus(MemberStatusChangeRequest request, AuthUser operator) {
		if (operator == null) {
			throw BusinessException.unauthorized();
		}
		long userSn = request.userSnAsLong();
		AccountVO member = memberMapper.selectMemberForUpdate(userSn);
		if (member == null) {
			throw new BusinessException(AccountErrorCode.MEMBER_NOT_FOUND);
		}
		validateTransition(member.getAcntStts(), request.acntStts());
		if (memberMapper.updateMemberStatus(userSn, request.acntStts(), operator.userSn()) == 0) {
			throw new BusinessException(AccountErrorCode.MEMBER_NOT_FOUND);
		}
		// 조치 사유 포함 감사기록(커밋 정합) — 운영자 조치 추적성
		auditRecorder.recordSuccess(
				String.format("MEMBER_STATUS_CHANGE {\"userSn\":%d,\"from\":\"%s\",\"to\":\"%s\",\"rsn\":\"%s\"}",
						userSn, jsonSafe(member.getAcntStts()), jsonSafe(request.acntStts()), jsonSafe(request.rsn())),
				"MEMBER", String.valueOf(userSn));
	}

	// ------------------------------------------------------------------

	/**
	 * 허용 전이만 통과(아니면 409). 정지/해제/탈퇴처리:
	 * ACTIVE→SUSPENDED, SUSPENDED→ACTIVE, (ACTIVE|SUSPENDED)→WITHDRAWN.
	 * 동일상태·WITHDRAWN 출발·PENDING/REJECTED 출발은 모두 차단.
	 */
	private static void validateTransition(String from, String to) {
		boolean allowed = switch (to) {
			case STTS_ACTIVE -> STTS_SUSPENDED.equals(from);
			case STTS_SUSPENDED -> STTS_ACTIVE.equals(from);
			case STTS_WITHDRAWN -> STTS_ACTIVE.equals(from) || STTS_SUSPENDED.equals(from);
			default -> false;
		};
		if (!allowed) {
			throw new BusinessException(AccountErrorCode.MEMBER_STATUS_NOT_ALLOWED);
		}
	}

	/** 검색어 정규화 — 2자 미만(한 글자 PII 탐색)은 무시. LIKE 와일드카드 이스케이프. */
	private static String normalizeKeyword(String keyword) {
		String v = trimToNull(keyword);
		if (v == null || v.length() < KEYWORD_MIN_LEN) {
			return null;
		}
		return v.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
	}

	/** 감사 메시지 JSON 안전화 — 따옴표/역슬래시/개행 제거(로그 인젝션 방지). */
	private static String jsonSafe(String s) {
		if (s == null) {
			return "";
		}
		return s.replaceAll("[\"\\\\\\r\\n]", " ");
	}

	private static String trimToNull(String s) {
		return (s == null || s.isBlank()) ? null : s.trim();
	}
}
