package egovframework.eeum.content.service.popup.impl;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import egovframework.eeum.auth.vo.AuthUser;
import egovframework.eeum.base.audit.Audit;
import egovframework.eeum.base.exception.BusinessException;
import egovframework.eeum.base.response.PageResponse;
import egovframework.eeum.code.service.code.CodeService;
import egovframework.eeum.content.exception.ContentErrorCode;
import egovframework.eeum.content.service.banner.ContentBannerStatus;
import egovframework.eeum.content.service.popup.ContentPopupService;
import egovframework.eeum.content.service.popup.dao.ContentPopupMapper;
import egovframework.eeum.content.vo.ContentPopupVO;
import egovframework.eeum.content.web.dto.ContentPopupCreateRequest;
import egovframework.eeum.content.web.dto.ContentPopupResponse;
import egovframework.eeum.content.web.dto.ContentPopupSearchRequest;
import egovframework.eeum.content.web.dto.ContentPopupUpdateRequest;
import lombok.RequiredArgsConstructor;

/**
 * 콘텐츠 팝업 서비스 구현. 배너와 동형 — status 는 조회 시 {@link ContentBannerStatus#resolve} 산출.
 * 생성/수정은 exposeMode 로 EXPOSE_YN 갱신(토글은 PATCH 전용), 삭제는 soft delete.
 */
@Service
@RequiredArgsConstructor
public class ContentPopupServiceImpl implements ContentPopupService {

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");
	private static final String LINK_TARGET_GROUP = "CONTENT_LINK_TARGET";
	private static final String LOCATION_GROUP = "POPUP_EXPOSE_LOCATION";
	private static final String TARGET_GROUP = "POPUP_EXPOSE_TARGET";
	private static final Pattern INTERNAL_IMAGE_URL = Pattern.compile("^/files/download\\?atchFileSn=\\d+$");
	private static final Pattern HTTP_URL = Pattern.compile("^https?://.+");

	private final ContentPopupMapper popupMapper;
	private final CodeService codeService;

	@Override
	@Transactional(readOnly = true)
	public PageResponse<ContentPopupResponse> list(ContentPopupSearchRequest request) {
		LocalDate today = LocalDate.now(KST);
		String status = blankToNull(request.status());
		List<ContentPopupResponse> items = popupMapper
				.selectPopupList(blankToNull(request.exposeLocationCd()), blankToNull(request.keyword())).stream()
				.map(vo -> toResponse(vo, today))
				.filter(res -> status == null || status.equals(res.status()))
				.toList();
		return PageResponse.of(items, items.size(), 1, items.isEmpty() ? 20 : items.size());
	}

	@Override
	@Transactional(readOnly = true)
	public ContentPopupResponse get(long popupId) {
		ContentPopupVO vo = popupMapper.selectPopupById(popupId);
		if (vo == null) {
			throw new BusinessException(ContentErrorCode.CONTENT_NOT_FOUND);
		}
		return toResponse(vo, LocalDate.now(KST));
	}

	@Override
	@Transactional
	@Audit(actn = "CONTENT_POPUP_INSERT", trgtType = "CONTENT_POPUP", trgtId = "#result.popupId()")
	public ContentPopupResponse create(ContentPopupCreateRequest request, AuthUser user) {
		validateImageUrl(request.imageUrl());
		validateDateRange(request.exposeBgnDate(), request.exposeEndDate());
		validateCode(LOCATION_GROUP, request.exposeLocationCd(), ContentErrorCode.CONTENT_POPUP_LOCATION_INVALID);
		validateCode(TARGET_GROUP, request.exposeTargetCd(), ContentErrorCode.CONTENT_POPUP_TARGET_INVALID);
		validateLinkTarget(request.linkTargetCd(), request.linkTargetVal());

		ContentPopupVO vo = new ContentPopupVO();
		vo.setImgUrl(request.imageUrl());
		vo.setTitle(request.title());
		vo.setExposeLocationCd(request.exposeLocationCd());
		vo.setExposeTargetCd(request.exposeTargetCd());
		vo.setLinkTargetCd(blankToNull(request.linkTargetCd()));
		vo.setLinkTargetVal(blankToNull(request.linkTargetVal()));
		vo.setExposeBgnDate(request.exposeBgnDate());
		vo.setExposeEndDate(request.exposeEndDate());
		vo.setExposeYn(request.exposeMode().toExposeYn());
		vo.setRgtrSn(user.userSn());
		popupMapper.insertPopup(vo);

		return get(vo.getContentPopupSn());
	}

	@Override
	@Transactional
	@Audit(actn = "CONTENT_POPUP_UPDATE", trgtType = "CONTENT_POPUP", trgtId = "#popupId")
	public ContentPopupResponse update(long popupId, ContentPopupUpdateRequest request, AuthUser user) {
		validateImageUrl(request.imageUrl());
		validateDateRange(request.exposeBgnDate(), request.exposeEndDate());
		validateCode(LOCATION_GROUP, request.exposeLocationCd(), ContentErrorCode.CONTENT_POPUP_LOCATION_INVALID);
		validateCode(TARGET_GROUP, request.exposeTargetCd(), ContentErrorCode.CONTENT_POPUP_TARGET_INVALID);
		validateLinkTarget(request.linkTargetCd(), request.linkTargetVal());

		ContentPopupVO vo = new ContentPopupVO();
		vo.setContentPopupSn(popupId);
		vo.setImgUrl(request.imageUrl());
		vo.setTitle(request.title());
		vo.setExposeLocationCd(request.exposeLocationCd());
		vo.setExposeTargetCd(request.exposeTargetCd());
		vo.setLinkTargetCd(blankToNull(request.linkTargetCd()));
		vo.setLinkTargetVal(blankToNull(request.linkTargetVal()));
		vo.setExposeBgnDate(request.exposeBgnDate());
		vo.setExposeEndDate(request.exposeEndDate());
		vo.setExposeYn(request.exposeMode().toExposeYn());
		vo.setMdfrSn(user.userSn());
		if (popupMapper.updatePopup(vo) == 0) {
			throw new BusinessException(ContentErrorCode.CONTENT_NOT_FOUND);
		}
		return get(popupId);
	}

	@Override
	@Transactional
	@Audit(actn = "CONTENT_POPUP_TOGGLE", trgtType = "CONTENT_POPUP", trgtId = "#popupId")
	public ContentPopupResponse toggle(long popupId, boolean isActive, AuthUser user) {
		if (popupMapper.updatePopupExposeYn(popupId, isActive ? "Y" : "N", user.userSn()) == 0) {
			throw new BusinessException(ContentErrorCode.CONTENT_NOT_FOUND);
		}
		return get(popupId);
	}

	@Override
	@Transactional
	@Audit(actn = "CONTENT_POPUP_DELETE", trgtType = "CONTENT_POPUP", trgtId = "#popupId")
	public void delete(long popupId, AuthUser user) {
		if (popupMapper.softDeletePopup(popupId, user.userSn()) == 0) {
			throw new BusinessException(ContentErrorCode.CONTENT_NOT_FOUND);
		}
	}

	// ------------------------------------------------------------------

	private ContentPopupResponse toResponse(ContentPopupVO vo, LocalDate today) {
		ContentBannerStatus status = ContentBannerStatus.resolve(
				"Y".equals(vo.getExposeYn()), vo.getExposeBgnDate(), vo.getExposeEndDate(), today);
		return ContentPopupResponse.from(vo, status);
	}

	private void validateImageUrl(String imageUrl) {
		if (imageUrl == null || !INTERNAL_IMAGE_URL.matcher(imageUrl).matches()) {
			throw new BusinessException(ContentErrorCode.CONTENT_IMAGE_INVALID);
		}
	}

	private void validateDateRange(LocalDate bgn, LocalDate end) {
		if (bgn != null && end != null && end.isBefore(bgn)) {
			throw new BusinessException(ContentErrorCode.CONTENT_DATE_RANGE_INVALID);
		}
	}

	/** 필수 코드가 지정 공통코드 그룹에 존재하는지 — 아니면 지정 오류코드. */
	private void validateCode(String groupCd, String cdRaw, ContentErrorCode error) {
		String cd = blankToNull(cdRaw);
		if (cd == null || codeService.list(groupCd).stream().noneMatch(item -> item.cd().equals(cd))) {
			throw new BusinessException(error);
		}
	}

	/** linkTarget 검증(배너와 동일 규칙). */
	private void validateLinkTarget(String cdRaw, String valRaw) {
		String cd = blankToNull(cdRaw);
		String val = blankToNull(valRaw);
		if (cd == null) {
			if (val != null) {
				throw new BusinessException(ContentErrorCode.CONTENT_LINK_TARGET_INVALID);
			}
			return;
		}
		if (codeService.list(LINK_TARGET_GROUP).stream().noneMatch(item -> item.cd().equals(cd))) {
			throw new BusinessException(ContentErrorCode.CONTENT_LINK_TARGET_INVALID);
		}
		if (val == null) {
			throw new BusinessException(ContentErrorCode.CONTENT_LINK_TARGET_VALUE_REQUIRED);
		}
		switch (cd) {
			case "PRODUCT" -> {
				long houseSn;
				try {
					houseSn = Long.parseLong(val);
				} catch (NumberFormatException e) {
					throw new BusinessException(ContentErrorCode.CONTENT_LINK_TARGET_VALUE_INVALID);
				}
				if (popupMapper.selectActiveHouseExists(houseSn) <= 0) {
					throw new BusinessException(ContentErrorCode.CONTENT_LINK_TARGET_VALUE_INVALID);
				}
			}
			case "URL" -> {
				if (!HTTP_URL.matcher(val).matches()) {
					throw new BusinessException(ContentErrorCode.CONTENT_LINK_TARGET_VALUE_INVALID);
				}
			}
			default -> {
				// PROMOTION 등 — 문자열 필수만(위에서 val != null 확인). 대상 존재검증 보류.
			}
		}
	}

	private static String blankToNull(String s) {
		return (s == null || s.isBlank()) ? null : s;
	}
}
