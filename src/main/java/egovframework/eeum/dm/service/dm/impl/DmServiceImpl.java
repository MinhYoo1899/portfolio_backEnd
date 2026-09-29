package egovframework.eeum.dm.service.dm.impl;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import egovframework.eeum.auth.vo.AuthUser;
import egovframework.eeum.base.audit.Audit;
import egovframework.eeum.base.exception.BusinessException;
import egovframework.eeum.base.response.PageResponse;
import egovframework.eeum.dm.exception.DmErrorCode;
import egovframework.eeum.dm.service.dm.DmService;
import egovframework.eeum.dm.service.dm.dao.DmMapper;
import egovframework.eeum.dm.vo.DmMessageVO;
import egovframework.eeum.dm.web.dto.DmCreateRequest;
import egovframework.eeum.dm.web.dto.DmResponse;
import egovframework.eeum.dm.web.dto.DmSearchRequest;
import egovframework.eeum.notification.service.notification.NotificationEmitter;
import egovframework.eeum.notification.service.notification.NotificationType;
import lombok.RequiredArgsConstructor;

/**
 * DM 서비스 구현. 상태 저장 라이프사이클(SENT/SCHEDULED/DRAFT/FAILED).
 * 작성 시 saveAsDraft/sendTiming 으로 상태 산정, 실 전송은 스텁(알림톡/SMS 벤더 미선정 — SENT 기록만).
 */
@Service
@RequiredArgsConstructor
public class DmServiceImpl implements DmService {

	private static final Set<String> CHANNELS = Set.of("ALIMTALK", "SMS");
	private static final Set<String> TARGETS = Set.of("ALL", "SELECTED");
	private static final Set<String> TIMINGS = Set.of("NOW", "SCHEDULED");
	private static final Pattern INTERNAL_IMAGE_URL = Pattern.compile("^/files/download\\?atchFileSn=\\d+$");
	private static final String STTS_DRAFT = "DRAFT";
	private static final String STTS_SCHEDULED = "SCHEDULED";
	private static final String STTS_SENT = "SENT";

	private final DmMapper dmMapper;
	private final NotificationEmitter notificationEmitter;

	@Override
	@Transactional(readOnly = true)
	public PageResponse<DmResponse> list(DmSearchRequest request) {
		String status = blankToNull(request.status());
		String channelCd = blankToNull(request.channelCd());
		String keyword = blankToNull(request.keyword());
		int page = request.pageOrDefault();
		int size = request.sizeOrDefault();

		long total = dmMapper.countDm(status, channelCd, keyword);
		List<DmResponse> items = dmMapper.selectDmList(status, channelCd, keyword, request.offset(), size).stream()
				.map(DmResponse::from)
				.toList();
		return PageResponse.of(items, total, page, size);
	}

	@Override
	@Transactional(readOnly = true)
	public DmResponse get(long dmId) {
		DmMessageVO vo = dmMapper.selectDmById(dmId);
		if (vo == null) {
			throw new BusinessException(DmErrorCode.DM_NOT_FOUND);
		}
		return DmResponse.from(vo);
	}

	@Override
	@Transactional
	@Audit(actn = "DM_CREATE", trgtType = "DM_MESSAGE", trgtId = "#result.dmId()")
	public DmResponse create(DmCreateRequest request, AuthUser user) {
		validateChannel(request.channelCd());
		validateTarget(request.targetTypeCd());
		validateTiming(request.sendTimingCd());
		validateImage(request.imageUrl());

		DmMessageVO vo = new DmMessageVO();
		vo.setTitle(request.title());
		vo.setContent(blankToNull(request.content()));
		vo.setChannelCd(request.channelCd());
		vo.setImgUrl(blankToNull(request.imageUrl()));
		vo.setTargetTypeCd(request.targetTypeCd());
		vo.setTargetCnt(request.targetCount());
		vo.setSendTimingCd(request.sendTimingCd());
		vo.setRgtrSn(user.userSn());

		boolean draft = Boolean.TRUE.equals(request.saveAsDraft());
		if (draft) {
			vo.setDmSttsCd(STTS_DRAFT);
			vo.setScheduledAt(toUtc(request.scheduledAt()));
		} else {
			requireContent(request.content());
			if (STTS_SCHEDULED.equals(request.sendTimingCd())) {
				if (request.scheduledAt() == null || !request.scheduledAt().isAfter(Instant.now())) {
					throw new BusinessException(DmErrorCode.DM_SCHEDULED_AT_INVALID);
				}
				vo.setDmSttsCd(STTS_SCHEDULED);
				vo.setScheduledAt(toUtc(request.scheduledAt()));
			} else { // NOW — 즉시 발송(스텁)
				vo.setDmSttsCd(STTS_SENT);
				vo.setSentAt(LocalDateTime.now(ZoneOffset.UTC));
			}
		}
		dmMapper.insertDm(vo);
		if (STTS_SENT.equals(vo.getDmSttsCd())) { // 즉시 발송(SENT)일 때만 운영자 알림 적재
			notificationEmitter.emit(user.userSn(), "DM:" + vo.getDmMessageSn(), NotificationType.DM,
					"DM 발송 완료", request.title(), String.valueOf(vo.getDmMessageSn()));
		}
		return get(vo.getDmMessageSn());
	}

	@Override
	@Transactional
	@Audit(actn = "DM_DELETE", trgtType = "DM_MESSAGE", trgtId = "#dmId")
	public void delete(long dmId, AuthUser user) {
		if (dmMapper.softDeleteDm(dmId, user.userSn()) == 0) {
			throw new BusinessException(DmErrorCode.DM_NOT_FOUND);
		}
	}

	@Override
	@Transactional
	public int promoteScheduled() {
		// SCHEDULED 중 예약시각 도래분을 SENT 로(스텁: 벤더 미선정, 발송기록만)
		return dmMapper.promoteScheduled();
	}

	// ------------------------------------------------------------------

	private void validateChannel(String channel) {
		if (channel == null || !CHANNELS.contains(channel)) {
			throw new BusinessException(DmErrorCode.DM_CHANNEL_INVALID);
		}
	}

	private void validateTarget(String target) {
		if (target == null || !TARGETS.contains(target)) {
			throw new BusinessException(DmErrorCode.DM_TARGET_INVALID);
		}
	}

	private void validateTiming(String timing) {
		if (timing == null || !TIMINGS.contains(timing)) {
			throw new BusinessException(DmErrorCode.DM_SCHEDULED_AT_INVALID);
		}
	}

	private void validateImage(String imageUrl) {
		String url = blankToNull(imageUrl);
		if (url != null && !INTERNAL_IMAGE_URL.matcher(url).matches()) {
			throw new BusinessException(DmErrorCode.DM_IMAGE_INVALID);
		}
	}

	private void requireContent(String content) {
		if (blankToNull(content) == null) {
			throw new BusinessException(DmErrorCode.DM_CONTENT_REQUIRED);
		}
	}

	private static LocalDateTime toUtc(Instant instant) {
		return instant == null ? null : LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
	}

	private static String blankToNull(String s) {
		return (s == null || s.isBlank()) ? null : s;
	}
}
