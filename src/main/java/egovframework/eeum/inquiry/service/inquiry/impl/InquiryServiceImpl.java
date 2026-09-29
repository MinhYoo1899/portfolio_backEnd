package egovframework.eeum.inquiry.service.inquiry.impl;

import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import egovframework.eeum.auth.vo.AuthUser;
import egovframework.eeum.base.audit.Audit;
import egovframework.eeum.base.exception.BusinessException;
import egovframework.eeum.base.exception.CommonErrorCode;
import egovframework.eeum.inquiry.exception.InquiryErrorCode;
import egovframework.eeum.inquiry.service.inquiry.InquiryAttachmentService;
import egovframework.eeum.inquiry.service.inquiry.InquiryService;
import egovframework.eeum.inquiry.service.inquiry.dao.InquiryMapper;
import egovframework.eeum.inquiry.vo.InqrAnsInfoVO;
import egovframework.eeum.inquiry.vo.InqrInfoVO;
import egovframework.eeum.inquiry.web.dto.InqrAnswerInsertRequest;
import egovframework.eeum.inquiry.web.dto.InqrAnswerResponse;
import egovframework.eeum.inquiry.web.dto.InqrAnswerUpdateRequest;
import egovframework.eeum.inquiry.web.dto.InqrBlindRequest;
import egovframework.eeum.inquiry.web.dto.InqrDetailResponse;
import egovframework.eeum.inquiry.web.dto.InqrInsertRequest;
import egovframework.eeum.inquiry.web.dto.InqrListItemResponse;
import egovframework.eeum.inquiry.web.dto.InqrListResponse;
import egovframework.eeum.inquiry.web.dto.InqrSearchRequest;
import egovframework.eeum.inquiry.web.dto.InqrUpdateRequest;
import egovframework.eeum.notification.service.notification.NotificationEmitter;
import egovframework.eeum.notification.service.notification.NotificationType;
import lombok.RequiredArgsConstructor;

/**
 * 문의 서비스 구현 — codex 교차검토 반영(비밀글/블라인드 접근제어, 답변 동시성, 소유권 USER_SN).
 */
@Service
@RequiredArgsConstructor
public class InquiryServiceImpl implements InquiryService {

	private static final String Y = "Y";
	private static final String N = "N";
	private static final String STTS_PENDING = "PENDING";
	private static final String STTS_ANSWERED = "ANSWERED";
	private static final String PSTG_BLINDED = "BLINDED";
	private static final String AUTH_ANS = "INQR_ANS";
	private static final String AUTH_MODER = "INQR_MODER";
	/** 비로그인 viewer — 어떤 USER_SN 과도 불일치(마스킹 강제). */
	private static final long ANONYMOUS = -1L;
	private static final int DEFAULT_PAGE_SIZE = 20;
	private static final int MAX_PAGE_SIZE = 100;
	/** 유형별 기본 비밀글(개인정보 섞일 가능성 — codex 권고). */
	private static final Set<String> SECRET_DEFAULT_TYPES = Set.of("RESERVATION", "PAYMENT", "ACCOUNT");

	private final InquiryMapper inquiryMapper;
	private final InquiryAttachmentService attachmentService;
	private final NotificationEmitter notificationEmitter;

	@Override
	@Transactional
	@Audit(actn = "INQUIRY_INSERT", trgtType = "INQR_INFO", trgtId = "#result.inqrSn()")
	public InqrDetailResponse insert(InqrInsertRequest request, AuthUser writer) {
		AuthUser user = requireUser(writer);
		validateType(request.inqrTypeCd());
		String prvtPstYn = resolveSecret(request.secret(), request.inqrTypeCd());
		InqrInfoVO vo = InqrInfoVO.builder()
				.userSn(user.userSn())
				.inqrTypeCd(request.inqrTypeCd())
				.inqrTtl(request.inqrTtl())
				.inqrCn(request.inqrCn())
				.prvtPstYn(prvtPstYn)
				.build();
		inquiryMapper.insertInquiry(vo);
		// 본문 저장 + 첨부 연결은 같은 트랜잭션(6중검증 + 공개성 확정 — 비밀글이면 PRIVATE 강제)
		attachmentService.syncAttachments(vo.getInqrSn(), request.attachments(), Y.equals(prvtPstYn), user);
		notificationEmitter.emit(user.userSn(), "INQUIRY:" + vo.getInqrSn(), NotificationType.INQUIRY,
				"새 문의 등록", request.inqrTtl(), String.valueOf(vo.getInqrSn()));
		return detail(vo.getInqrSn(), user);
	}

	@Override
	@Transactional(readOnly = true)
	public InqrListResponse list(InqrSearchRequest request, AuthUser viewer) {
		boolean mine = Boolean.TRUE.equals(request.mine());
		if (mine && viewer == null) {
			throw BusinessException.unauthorized();
		}
		long viewerSn = viewer == null ? ANONYMOUS : viewer.userSn();
		String isOperator = isOperatorView(viewer) ? Y : N;
		String inqrType = trimToNull(request.inqrType());
		String ansStts = trimToNull(request.ansStts());
		String keyword = escapeLike(trimToNull(request.keyword()));

		if (request.isCursorMode()) {
			int size = clamp(request.size());
			List<InqrInfoVO> rows = inquiryMapper.selectInquiryListCursor(
					viewerSn, isOperator, mine, inqrType, ansStts, keyword, request.cursor(), size + 1);
			boolean hasNext = rows.size() > size;
			if (hasNext) {
				rows = rows.subList(0, size);
			}
			Long nextCursor = (hasNext && !rows.isEmpty()) ? rows.get(rows.size() - 1).getInqrSn() : null;
			return InqrListResponse.cursor(toItems(rows), nextCursor, hasNext);
		}

		int page = (request.page() == null || request.page() < 1) ? 1 : request.page();
		int pageSize = clamp(request.pageSize());
		long total = inquiryMapper.countInquiryList(viewerSn, isOperator, mine, inqrType, ansStts, keyword);
		List<InqrInfoVO> rows = inquiryMapper.selectInquiryListOffset(
				viewerSn, isOperator, mine, inqrType, ansStts, keyword, pageSize, (page - 1) * pageSize);
		return InqrListResponse.offset(toItems(rows), total, page, pageSize);
	}

	@Override
	@Transactional(readOnly = true)
	public InqrDetailResponse detail(long inqrSn, AuthUser viewer) {
		InqrInfoVO vo = inquiryMapper.selectInquiryDetail(inqrSn);
		if (vo == null) {
			throw new BusinessException(InquiryErrorCode.INQUIRY_NOT_FOUND);
		}
		boolean operator = isOperatorView(viewer);
		boolean owner = viewer != null && viewer.userSn().equals(vo.getUserSn());
		boolean restricted = PSTG_BLINDED.equals(vo.getPstgSttsCd()) || Y.equals(vo.getPrvtPstYn());
		// 블라인드/비밀글은 작성자·운영자만 — 비인가는 404(열거 방지, codex)
		if (restricted && !owner && !operator) {
			throw new BusinessException(InquiryErrorCode.INQUIRY_NOT_FOUND);
		}
		List<InqrAnswerResponse> answers = inquiryMapper.selectAnswersByInquiry(inqrSn).stream()
				.map(InqrAnswerResponse::from)
				.toList();
		return InqrDetailResponse.from(vo, answers, attachmentService.attachmentsForDetail(inqrSn));
	}

	@Override
	@Transactional
	@Audit(actn = "INQUIRY_UPDATE", trgtType = "INQR_INFO", trgtId = "#request.inqrSn()")
	public void update(InqrUpdateRequest request, AuthUser writer) {
		AuthUser user = requireUser(writer);
		InqrInfoVO vo = inquiryMapper.selectInquiryForUpdate(request.inqrSn());
		requireActiveOwned(vo, user);
		// 답변 전(PENDING)이며 활성 답변이 없어야 수정 가능(codex — 상태값만 믿지 않음)
		if (!STTS_PENDING.equals(vo.getAnsSttsCd()) || inquiryMapper.countActiveAnswers(request.inqrSn()) > 0) {
			throw new BusinessException(InquiryErrorCode.INQUIRY_LOCKED);
		}
		validateType(request.inqrTypeCd());
		String newPrvtPstYn = resolveSecret(request.secret(), request.inqrTypeCd());
		inquiryMapper.updateInquiry(request.inqrSn(), user.userSn(), request.inqrTypeCd(),
				request.inqrTtl(), request.inqrCn(), newPrvtPstYn);
		// 첨부 동기화(유지/연결/해제) + 공개성 확정. 비밀글이면 전체 PRIVATE 강제(공개→비밀 전환 포함, 전이 규칙).
		// 비밀→공개 전환 시 기존 첨부는 PRIVATE 유지(자동 공개 안 함) — 신규 연결만 희망값 반영.
		attachmentService.syncAttachments(request.inqrSn(), request.attachments(), Y.equals(newPrvtPstYn), user);
	}

	@Override
	@Transactional
	@Audit(actn = "INQUIRY_DELETE", trgtType = "INQR_INFO", trgtId = "#inqrSn")
	public void delete(long inqrSn, AuthUser writer) {
		AuthUser user = requireUser(writer);
		InqrInfoVO vo = inquiryMapper.selectInquiryForUpdate(inqrSn);
		requireActiveOwned(vo, user);
		inquiryMapper.softDeleteInquiry(inqrSn, user.userSn());
		// 부모 문의 삭제 시 연결 첨부도 같은 트랜잭션에서 정리(LINKED 잔존·물리파일 누수 방지, codex Med 2). 권한은 위에서 확인.
		attachmentService.detachAllByInquiry(inqrSn, user);
	}

	@Override
	@Transactional
	@Audit(actn = "INQUIRY_ANSWER_INSERT", trgtType = "INQR_INFO", trgtId = "#request.inqrSn()")
	public InqrDetailResponse answer(InqrAnswerInsertRequest request, AuthUser operator) {
		AuthUser op = requireUser(operator);
		InqrInfoVO vo = inquiryMapper.selectInquiryForUpdate(request.inqrSn());
		requireActive(vo);
		inquiryMapper.insertAnswer(InqrAnsInfoVO.builder()
				.inqrSn(request.inqrSn())
				.ansUserSn(op.userSn())
				.ansCn(request.ansCn())
				.build());
		inquiryMapper.updateAnswStts(request.inqrSn(), STTS_ANSWERED, op.userSn());
		return detail(request.inqrSn(), op);
	}

	@Override
	@Transactional
	@Audit(actn = "INQUIRY_ANSWER_UPDATE", trgtType = "INQR_ANS_INFO", trgtId = "#request.ansSn()")
	public void updateAnswer(InqrAnswerUpdateRequest request, AuthUser operator) {
		AuthUser op = requireUser(operator);
		InqrAnsInfoVO answer = inquiryMapper.selectAnswer(request.ansSn());
		if (answer == null || Y.equals(answer.getDelYn())) {
			throw new BusinessException(InquiryErrorCode.INQUIRY_ANSWER_NOT_FOUND);
		}
		requireActive(inquiryMapper.selectInquiryForUpdate(answer.getInqrSn())); // 부모 선잠금+활성 검증(codex)
		inquiryMapper.updateAnswer(request.ansSn(), request.ansCn(), op.userSn());
	}

	@Override
	@Transactional
	@Audit(actn = "INQUIRY_ANSWER_DELETE", trgtType = "INQR_ANS_INFO", trgtId = "#ansSn")
	public void deleteAnswer(long ansSn, AuthUser operator) {
		AuthUser op = requireUser(operator);
		InqrAnsInfoVO answer = inquiryMapper.selectAnswer(ansSn);
		if (answer == null || Y.equals(answer.getDelYn())) {
			throw new BusinessException(InquiryErrorCode.INQUIRY_ANSWER_NOT_FOUND);
		}
		long inqrSn = answer.getInqrSn();
		requireActive(inquiryMapper.selectInquiryForUpdate(inqrSn)); // 부모 선잠금+활성 검증(상태 재계산 정합, codex)
		inquiryMapper.softDeleteAnswer(ansSn, op.userSn());
		if (inquiryMapper.countActiveAnswers(inqrSn) == 0) {
			inquiryMapper.updateAnswStts(inqrSn, STTS_PENDING, op.userSn());
		}
	}

	@Override
	@Transactional
	@Audit(actn = "INQUIRY_BLIND", trgtType = "INQR_INFO", trgtId = "#request.inqrSn()")
	public void blind(InqrBlindRequest request, AuthUser operator) {
		AuthUser op = requireUser(operator);
		if (inquiryMapper.blindInquiry(request.inqrSn(), request.blndRsn(), op.userSn()) == 0) {
			throw new BusinessException(InquiryErrorCode.INQUIRY_NOT_FOUND);
		}
	}

	@Override
	@Transactional
	@Audit(actn = "INQUIRY_UNBLIND", trgtType = "INQR_INFO", trgtId = "#inqrSn")
	public void unblind(long inqrSn, AuthUser operator) {
		AuthUser op = requireUser(operator);
		if (inquiryMapper.unblindInquiry(inqrSn, op.userSn()) == 0) {
			throw new BusinessException(InquiryErrorCode.INQUIRY_NOT_FOUND);
		}
	}

	// ------------------------------------------------------------------

	private static List<InqrListItemResponse> toItems(List<InqrInfoVO> rows) {
		return rows.stream().map(InqrListItemResponse::from).toList();
	}

	/** INQR 관리 권한(답변·게시관리 중 하나라도) 보유 = 비밀글/블라인드 열람 가능한 운영자. */
	private static boolean isOperatorView(AuthUser user) {
		return user != null
				&& (user.permissions().contains(AUTH_ANS) || user.permissions().contains(AUTH_MODER));
	}

	private static AuthUser requireUser(AuthUser user) {
		if (user == null) {
			throw BusinessException.unauthorized();
		}
		return user;
	}

	private static void requireActive(InqrInfoVO vo) {
		if (vo == null || Y.equals(vo.getDelYn())) {
			throw new BusinessException(InquiryErrorCode.INQUIRY_NOT_FOUND);
		}
	}

	/** 활성 + 본인 소유(USER_SN 기준 — 감사컬럼 아님). */
	private static void requireActiveOwned(InqrInfoVO vo, AuthUser user) {
		requireActive(vo);
		if (!user.userSn().equals(vo.getUserSn())) {
			throw new BusinessException(InquiryErrorCode.INQUIRY_FORBIDDEN);
		}
	}

	/** 문의유형 코드 유효성(공통코드 INQR_TYPE 존재) — 임의 타입 저장 차단(codex 코드검토). */
	private void validateType(String inqrTypeCd) {
		if (inquiryMapper.countActiveInqrType(inqrTypeCd) == 0) {
			throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, "유효하지 않은 문의유형입니다.");
		}
	}

	/** LIKE 와일드카드(%, _, \) 이스케이프 — 리터럴 검색(매퍼 ESCAPE '\\' 와 짝). */
	private static String escapeLike(String s) {
		if (s == null) {
			return null;
		}
		return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
	}

	private static String resolveSecret(Boolean secret, String inqrTypeCd) {
		if (secret != null) {
			return secret ? Y : N;
		}
		return SECRET_DEFAULT_TYPES.contains(inqrTypeCd) ? Y : N;
	}

	private static int clamp(Integer size) {
		if (size == null || size < 1) {
			return DEFAULT_PAGE_SIZE;
		}
		return Math.min(size, MAX_PAGE_SIZE);
	}

	private static String trimToNull(String s) {
		return (s == null || s.isBlank()) ? null : s.trim();
	}
}
