package egovframework.eeum.notice.service.notice.impl;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import egovframework.eeum.auth.vo.AuthUser;
import egovframework.eeum.base.audit.Audit;
import egovframework.eeum.base.exception.BusinessException;
import egovframework.eeum.base.response.PageResponse;
import egovframework.eeum.notice.exception.NoticeErrorCode;
import egovframework.eeum.notice.service.notice.NoticeService;
import egovframework.eeum.notice.service.notice.dao.NoticeMapper;
import egovframework.eeum.notice.vo.NoticeVO;
import egovframework.eeum.notice.web.dto.NoticeCreateRequest;
import egovframework.eeum.notice.web.dto.NoticeResponse;
import egovframework.eeum.notice.web.dto.NoticeSearchRequest;
import egovframework.eeum.notice.web.dto.NoticeUpdateRequest;
import egovframework.eeum.notification.service.notification.NotificationEmitter;
import egovframework.eeum.notification.service.notification.NotificationType;
import lombok.RequiredArgsConstructor;

/**
 * 공지사항 서비스 구현. 상태 저장 라이프사이클(DRAFT/SCHEDULED/PUBLISHED_ONGOING/CLOSED).
 * 작성 시 saveAsDraft/sendTiming 으로 상태 산정, 발행 시 알림 발송=스텁(FCM 미설정 — NOTIFY_SEND_YN 만 기록).
 */
@Service
@RequiredArgsConstructor
public class NoticeServiceImpl implements NoticeService {

	private static final Set<String> TARGETS = Set.of("ALL", "ALL_PARTNER", "MEMBER", "SPECIFIC_PARTNER");
	private static final Set<String> TIMINGS = Set.of("IMMEDIATE", "SCHEDULED");
	private static final String STTS_DRAFT = "DRAFT";
	private static final String STTS_SCHEDULED = "SCHEDULED";
	private static final String STTS_PUBLISHED = "PUBLISHED_ONGOING";

	private final NoticeMapper noticeMapper;
	private final NotificationEmitter notificationEmitter;

	@Override
	@Transactional(readOnly = true)
	public PageResponse<NoticeResponse> list(NoticeSearchRequest request) {
		String status = blankToNull(request.status());
		String target = blankToNull(request.target());
		String notifySend = blankToNull(request.notifySend());
		String keyword = blankToNull(request.keyword());
		int page = request.pageOrDefault();
		int size = request.sizeOrDefault();

		long total = noticeMapper.countNotice(status, target, notifySend, keyword);
		List<NoticeResponse> items = noticeMapper
				.selectNoticeList(status, target, notifySend, keyword, request.offset(), size).stream()
				.map(NoticeResponse::from)
				.toList();
		return PageResponse.of(items, total, page, size);
	}

	@Override
	@Transactional(readOnly = true)
	public NoticeResponse get(long noticeId) {
		return NoticeResponse.from(requireNotice(noticeId));
	}

	@Override
	@Transactional
	@Audit(actn = "NOTICE_INSERT", trgtType = "NOTICE", trgtId = "#result.noticeId()")
	public NoticeResponse create(NoticeCreateRequest request, AuthUser user) {
		validateTarget(request.targetTypeCd());
		validateTiming(request.sendTimingCd());

		NoticeVO vo = new NoticeVO();
		vo.setTitle(request.title());
		vo.setContent(contentOrEmpty(request.content()));
		vo.setTargetTypeCd(request.targetTypeCd());
		vo.setSendTimingCd(request.sendTimingCd());
		vo.setRgtrSn(user.userSn());

		boolean draft = Boolean.TRUE.equals(request.saveAsDraft());
		if (draft) {
			vo.setNoticeSttsCd(STTS_DRAFT);
			vo.setNotifySendYn("N");
			vo.setScheduledAt(toUtc(request.scheduledAt()));
		} else {
			requireContent(request.content());
			if (STTS_SCHEDULED.equals(request.sendTimingCd())) {
				if (request.scheduledAt() == null || !request.scheduledAt().isAfter(Instant.now())) {
					throw new BusinessException(NoticeErrorCode.NOTICE_SCHEDULED_AT_INVALID);
				}
				vo.setNoticeSttsCd(STTS_SCHEDULED);
				vo.setNotifySendYn("N");
				vo.setScheduledAt(toUtc(request.scheduledAt()));
			} else { // IMMEDIATE — 즉시 발행 + 알림(스텁)
				vo.setNoticeSttsCd(STTS_PUBLISHED);
				vo.setNotifySendYn("Y");
				vo.setPublishedAt(LocalDateTime.now(ZoneOffset.UTC));
			}
		}
		noticeMapper.insertNotice(vo);
		if (STTS_PUBLISHED.equals(vo.getNoticeSttsCd())) { // 즉시 게시(PUBLISHED_ONGOING)일 때만 운영자 알림 적재
			notificationEmitter.emit(user.userSn(), "NOTICE:" + vo.getNoticeSn(), NotificationType.NOTICE,
					"공지 게시", request.title(), String.valueOf(vo.getNoticeSn()));
		}
		return get(vo.getNoticeSn());
	}

	@Override
	@Transactional
	@Audit(actn = "NOTICE_UPDATE", trgtType = "NOTICE", trgtId = "#noticeId")
	public NoticeResponse update(long noticeId, NoticeUpdateRequest request, AuthUser user) {
		validateTarget(request.targetTypeCd());
		validateTiming(request.sendTimingCd());

		NoticeVO vo = new NoticeVO();
		vo.setNoticeSn(noticeId);
		vo.setTitle(request.title());
		vo.setContent(contentOrEmpty(request.content()));
		vo.setTargetTypeCd(request.targetTypeCd());
		vo.setSendTimingCd(request.sendTimingCd());
		vo.setScheduledAt(toUtc(request.scheduledAt()));
		vo.setMdfrSn(user.userSn());
		if (noticeMapper.updateNotice(vo) == 0) {
			throw new BusinessException(NoticeErrorCode.NOTICE_NOT_FOUND);
		}
		return get(noticeId);
	}

	@Override
	@Transactional
	@Audit(actn = "NOTICE_CLOSE", trgtType = "NOTICE", trgtId = "#noticeId")
	public NoticeResponse close(long noticeId, AuthUser user) {
		NoticeVO vo = requireNotice(noticeId);
		if (!STTS_PUBLISHED.equals(vo.getNoticeSttsCd())) {
			throw new BusinessException(NoticeErrorCode.NOTICE_NOT_PUBLISHED);
		}
		if (noticeMapper.closeNotice(noticeId, user.userSn()) == 0) {
			throw new BusinessException(NoticeErrorCode.NOTICE_NOT_PUBLISHED);
		}
		return get(noticeId);
	}

	@Override
	@Transactional
	@Audit(actn = "NOTICE_DELETE", trgtType = "NOTICE", trgtId = "#noticeId")
	public void delete(long noticeId, AuthUser user) {
		if (noticeMapper.softDeleteNotice(noticeId, user.userSn()) == 0) {
			throw new BusinessException(NoticeErrorCode.NOTICE_NOT_FOUND);
		}
	}

	@Override
	@Transactional
	public int promoteScheduled() {
		// SCHEDULED 중 예약시각 도래분을 게시중 전이 + 알림(스텁: FCM 미설정, NOTIFY_SEND_YN 만 기록)
		return noticeMapper.promoteScheduled();
	}

	// ------------------------------------------------------------------

	private NoticeVO requireNotice(long noticeId) {
		NoticeVO vo = noticeMapper.selectNoticeById(noticeId);
		if (vo == null) {
			throw new BusinessException(NoticeErrorCode.NOTICE_NOT_FOUND);
		}
		return vo;
	}

	private void validateTarget(String target) {
		if (target == null || !TARGETS.contains(target)) {
			throw new BusinessException(NoticeErrorCode.NOTICE_TARGET_INVALID);
		}
	}

	private void validateTiming(String timing) {
		if (timing == null || !TIMINGS.contains(timing)) {
			throw new BusinessException(NoticeErrorCode.NOTICE_SCHEDULED_AT_INVALID);
		}
	}

	private void requireContent(String content) {
		if (blankToNull(content) == null) {
			throw new BusinessException(NoticeErrorCode.NOTICE_CONTENT_REQUIRED);
		}
	}

	private static LocalDateTime toUtc(Instant instant) {
		return instant == null ? null : LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
	}

	private static String blankToNull(String s) {
		return (s == null || s.isBlank()) ? null : s;
	}

	/** 내용 저장값 — CONTENT NOT NULL 이므로 null/blank 는 빈 문자열로(임시저장 시 내용 없음 허용). */
	private static String contentOrEmpty(String s) {
		return (s == null || s.isBlank()) ? "" : s;
	}
}
