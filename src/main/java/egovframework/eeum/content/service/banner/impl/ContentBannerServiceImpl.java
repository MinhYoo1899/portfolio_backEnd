package egovframework.eeum.content.service.banner.impl;

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
import egovframework.eeum.content.service.banner.ContentBannerService;
import egovframework.eeum.content.service.banner.ContentBannerStatus;
import egovframework.eeum.content.service.banner.dao.ContentBannerMapper;
import egovframework.eeum.content.vo.ContentMainBannerVO;
import egovframework.eeum.content.web.dto.ContentBannerCreateRequest;
import egovframework.eeum.content.web.dto.ContentBannerResponse;
import egovframework.eeum.content.web.dto.ContentBannerUpdateRequest;
import lombok.RequiredArgsConstructor;

/**
 * 콘텐츠 메인배너 서비스 구현.
 *
 * <p>콘텐츠는 플랫폼(운영자) 소유 자원이라 파트너 소유권 필터가 없다 — 접근통제는 컨트롤러 @PreAuthorize.
 * status(EXPOSED/HIDDEN/ENDED)는 저장하지 않고 조회 시 {@link ContentBannerStatus#resolve} 로 산출한다.
 */
@Service
@RequiredArgsConstructor
public class ContentBannerServiceImpl implements ContentBannerService {

	/** 노출기간·산출 기준일 타임존. */
	private static final ZoneId KST = ZoneId.of("Asia/Seoul");
	/** linkTarget 공통코드 그룹. */
	private static final String LINK_TARGET_GROUP = "CONTENT_LINK_TARGET";
	/** 자체 파일인프라 반환 경로만 허용(외부 임의 URL 차단). */
	private static final Pattern INTERNAL_IMAGE_URL = Pattern.compile("^/files/download\\?atchFileSn=\\d+$");
	/** URL linkTarget 값 형식. */
	private static final Pattern HTTP_URL = Pattern.compile("^https?://.+");

	private final ContentBannerMapper bannerMapper;
	private final CodeService codeService;

	@Override
	@Transactional(readOnly = true)
	public PageResponse<ContentBannerResponse> list() {
		LocalDate today = LocalDate.now(KST);
		List<ContentBannerResponse> items = bannerMapper.selectBannerList().stream()
				.map(vo -> toResponse(vo, today))
				.toList();
		return PageResponse.of(items, items.size(), 1, items.isEmpty() ? 20 : items.size());
	}

	@Override
	@Transactional(readOnly = true)
	public ContentBannerResponse get(long bannerId) {
		ContentMainBannerVO vo = bannerMapper.selectBannerById(bannerId);
		if (vo == null) {
			throw new BusinessException(ContentErrorCode.CONTENT_NOT_FOUND);
		}
		return toResponse(vo, LocalDate.now(KST));
	}

	@Override
	@Transactional
	@Audit(actn = "CONTENT_BANNER_INSERT", trgtType = "CONTENT_MAIN_BANNER", trgtId = "#result.bannerId()")
	public ContentBannerResponse create(ContentBannerCreateRequest request, AuthUser user) {
		validateImageUrl(request.imageUrl());
		validateDateRange(request.exposeBgnDate(), request.exposeEndDate());
		validateLinkTarget(request.linkTargetCd(), request.linkTargetVal());

		ContentMainBannerVO vo = new ContentMainBannerVO();
		vo.setSortOrder(0);
		vo.setImgUrl(request.imageUrl());
		vo.setMainCopy(request.mainCopy());
		vo.setSubCopy(blankToNull(request.subCopy()));
		vo.setLinkTargetCd(blankToNull(request.linkTargetCd()));
		vo.setLinkTargetVal(blankToNull(request.linkTargetVal()));
		vo.setExposeBgnDate(request.exposeBgnDate());
		vo.setExposeEndDate(request.exposeEndDate());
		vo.setExposeYn(request.exposeMode().toExposeYn());
		vo.setRgtrSn(user.userSn());
		bannerMapper.insertBanner(vo);

		return get(vo.getContentMainBannerSn());
	}

	@Override
	@Transactional
	@Audit(actn = "CONTENT_BANNER_UPDATE", trgtType = "CONTENT_MAIN_BANNER", trgtId = "#bannerId")
	public ContentBannerResponse update(long bannerId, ContentBannerUpdateRequest request, AuthUser user) {
		validateImageUrl(request.imageUrl());
		validateDateRange(request.exposeBgnDate(), request.exposeEndDate());
		validateLinkTarget(request.linkTargetCd(), request.linkTargetVal());

		ContentMainBannerVO vo = new ContentMainBannerVO();
		vo.setContentMainBannerSn(bannerId);
		vo.setImgUrl(request.imageUrl());
		vo.setMainCopy(request.mainCopy());
		vo.setSubCopy(blankToNull(request.subCopy()));
		vo.setLinkTargetCd(blankToNull(request.linkTargetCd()));
		vo.setLinkTargetVal(blankToNull(request.linkTargetVal()));
		vo.setExposeBgnDate(request.exposeBgnDate());
		vo.setExposeEndDate(request.exposeEndDate());
		vo.setExposeYn(request.exposeMode().toExposeYn());
		vo.setMdfrSn(user.userSn());
		if (bannerMapper.updateBanner(vo) == 0) {
			throw new BusinessException(ContentErrorCode.CONTENT_NOT_FOUND);
		}
		return get(bannerId);
	}

	@Override
	@Transactional
	@Audit(actn = "CONTENT_BANNER_TOGGLE", trgtType = "CONTENT_MAIN_BANNER", trgtId = "#bannerId")
	public ContentBannerResponse toggle(long bannerId, boolean isActive, AuthUser user) {
		if (bannerMapper.updateBannerExposeYn(bannerId, isActive ? "Y" : "N", user.userSn()) == 0) {
			throw new BusinessException(ContentErrorCode.CONTENT_NOT_FOUND);
		}
		return get(bannerId);
	}

	// ------------------------------------------------------------------

	private ContentBannerResponse toResponse(ContentMainBannerVO vo, LocalDate today) {
		ContentBannerStatus status = ContentBannerStatus.resolve(
				"Y".equals(vo.getExposeYn()), vo.getExposeBgnDate(), vo.getExposeEndDate(), today);
		return ContentBannerResponse.from(vo, status);
	}

	/** imageUrl 은 자체 업로드 API 반환 경로만 허용 — 외부 임의 URL 차단. */
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

	/**
	 * linkTarget 검증(PM 확정):
	 * <ul>
	 *   <li>cd null → val 도 blank 여야 함(값만 지정 불가)</li>
	 *   <li>cd 지정 → 공통코드(CONTENT_LINK_TARGET) 존재 + val 필수</li>
	 *   <li>PRODUCT → val=숫자 houseId, 존재+미삭제 고택</li>
	 *   <li>URL → val=http|https</li>
	 *   <li>PROMOTION → 문자열 필수만(대상 존재검증 보류)</li>
	 * </ul>
	 */
	private void validateLinkTarget(String cdRaw, String valRaw) {
		String cd = blankToNull(cdRaw);
		String val = blankToNull(valRaw);
		if (cd == null) {
			if (val != null) {
				throw new BusinessException(ContentErrorCode.CONTENT_LINK_TARGET_INVALID);
			}
			return;
		}
		boolean known = codeService.list(LINK_TARGET_GROUP).stream()
				.anyMatch(item -> item.cd().equals(cd));
		if (!known) {
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
				if (bannerMapper.selectActiveHouseExists(houseSn) <= 0) {
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
