package egovframework.eeum.content.service.adbanner.impl;

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
import egovframework.eeum.content.service.adbanner.ContentAdBannerService;
import egovframework.eeum.content.service.adbanner.dao.ContentAdBannerMapper;
import egovframework.eeum.content.service.banner.ContentBannerStatus;
import egovframework.eeum.content.vo.ContentAdBannerVO;
import egovframework.eeum.content.web.dto.ContentAdBannerCreateRequest;
import egovframework.eeum.content.web.dto.ContentAdBannerResponse;
import egovframework.eeum.content.web.dto.ContentAdBannerUpdateRequest;
import lombok.RequiredArgsConstructor;

/**
 * 콘텐츠 광고배너 서비스 구현.
 *
 * <p>콘텐츠는 플랫폼(운영자) 소유 자원이라 파트너 소유권 필터가 없다 — 접근통제는 컨트롤러 @PreAuthorize.
 * status(EXPOSED/HIDDEN/ENDED)는 저장하지 않고 조회 시 {@link ContentBannerStatus#resolve} 로 산출(메인배너와 동일).
 * 생성 시 EXPOSE_YN='Y' 기본, 수정은 EXPOSE_YN 보존(변경은 PATCH toggle 전용), 삭제는 soft delete.
 */
@Service
@RequiredArgsConstructor
public class ContentAdBannerServiceImpl implements ContentAdBannerService {

	/** 노출기간·산출 기준일 타임존. */
	private static final ZoneId KST = ZoneId.of("Asia/Seoul");
	/** linkTarget 공통코드 그룹(메인배너와 공유 — V39). */
	private static final String LINK_TARGET_GROUP = "CONTENT_LINK_TARGET";
	/** 광고배너 노출위치 공통코드 그룹(V40). */
	private static final String POSITION_GROUP = "AD_BANNER_POSITION";
	/** 자체 파일인프라 반환 경로만 허용(외부 임의 URL 차단). */
	private static final Pattern INTERNAL_IMAGE_URL = Pattern.compile("^/files/download\\?atchFileSn=\\d+$");
	/** URL linkTarget 값 형식. */
	private static final Pattern HTTP_URL = Pattern.compile("^https?://.+");

	private final ContentAdBannerMapper adBannerMapper;
	private final CodeService codeService;

	@Override
	@Transactional(readOnly = true)
	public PageResponse<ContentAdBannerResponse> list() {
		LocalDate today = LocalDate.now(KST);
		List<ContentAdBannerResponse> items = adBannerMapper.selectAdBannerList().stream()
				.map(vo -> toResponse(vo, today))
				.toList();
		return PageResponse.of(items, items.size(), 1, items.isEmpty() ? 20 : items.size());
	}

	@Override
	@Transactional(readOnly = true)
	public ContentAdBannerResponse get(long adBannerId) {
		ContentAdBannerVO vo = adBannerMapper.selectAdBannerById(adBannerId);
		if (vo == null) {
			throw new BusinessException(ContentErrorCode.CONTENT_NOT_FOUND);
		}
		return toResponse(vo, LocalDate.now(KST));
	}

	@Override
	@Transactional
	@Audit(actn = "CONTENT_AD_BANNER_INSERT", trgtType = "CONTENT_AD_BANNER", trgtId = "#result.bannerId()")
	public ContentAdBannerResponse create(ContentAdBannerCreateRequest request, AuthUser user) {
		validateImageUrl(request.imageUrl());
		validateDateRange(request.exposeBgnDate(), request.exposeEndDate());
		validatePosition(request.positionCd());
		validateLinkTarget(request.linkTargetCd(), request.linkTargetVal());

		ContentAdBannerVO vo = new ContentAdBannerVO();
		vo.setTitle(request.title());
		vo.setSubCopy(blankToNull(request.subCopy()));
		vo.setImgUrl(blankToNull(request.imageUrl()));
		vo.setPositionCd(request.positionCd());
		vo.setLinkTargetCd(blankToNull(request.linkTargetCd()));
		vo.setLinkTargetVal(blankToNull(request.linkTargetVal()));
		vo.setExposeBgnDate(request.exposeBgnDate());
		vo.setExposeEndDate(request.exposeEndDate());
		vo.setExposeYn("Y"); // 생성 기본 노출
		vo.setRgtrSn(user.userSn());
		adBannerMapper.insertAdBanner(vo);

		return get(vo.getContentAdBannerSn());
	}

	@Override
	@Transactional
	@Audit(actn = "CONTENT_AD_BANNER_UPDATE", trgtType = "CONTENT_AD_BANNER", trgtId = "#adBannerId")
	public ContentAdBannerResponse update(long adBannerId, ContentAdBannerUpdateRequest request, AuthUser user) {
		validateImageUrl(request.imageUrl());
		validateDateRange(request.exposeBgnDate(), request.exposeEndDate());
		validatePosition(request.positionCd());
		validateLinkTarget(request.linkTargetCd(), request.linkTargetVal());

		ContentAdBannerVO vo = new ContentAdBannerVO();
		vo.setContentAdBannerSn(adBannerId);
		vo.setTitle(request.title());
		vo.setSubCopy(blankToNull(request.subCopy()));
		vo.setImgUrl(blankToNull(request.imageUrl()));
		vo.setPositionCd(request.positionCd());
		vo.setLinkTargetCd(blankToNull(request.linkTargetCd()));
		vo.setLinkTargetVal(blankToNull(request.linkTargetVal()));
		vo.setExposeBgnDate(request.exposeBgnDate());
		vo.setExposeEndDate(request.exposeEndDate());
		// EXPOSE_YN 은 세팅하지 않는다 — 수정 SQL 이 건드리지 않아 기존 노출값 보존(토글은 PATCH 전용).
		vo.setMdfrSn(user.userSn());
		if (adBannerMapper.updateAdBanner(vo) == 0) {
			throw new BusinessException(ContentErrorCode.CONTENT_NOT_FOUND);
		}
		return get(adBannerId);
	}

	@Override
	@Transactional
	@Audit(actn = "CONTENT_AD_BANNER_TOGGLE", trgtType = "CONTENT_AD_BANNER", trgtId = "#adBannerId")
	public ContentAdBannerResponse toggle(long adBannerId, boolean isActive, AuthUser user) {
		if (adBannerMapper.updateAdBannerExposeYn(adBannerId, isActive ? "Y" : "N", user.userSn()) == 0) {
			throw new BusinessException(ContentErrorCode.CONTENT_NOT_FOUND);
		}
		return get(adBannerId);
	}

	@Override
	@Transactional
	@Audit(actn = "CONTENT_AD_BANNER_DELETE", trgtType = "CONTENT_AD_BANNER", trgtId = "#adBannerId")
	public void delete(long adBannerId, AuthUser user) {
		if (adBannerMapper.softDeleteAdBanner(adBannerId, user.userSn()) == 0) {
			throw new BusinessException(ContentErrorCode.CONTENT_NOT_FOUND);
		}
	}

	// ------------------------------------------------------------------

	private ContentAdBannerResponse toResponse(ContentAdBannerVO vo, LocalDate today) {
		ContentBannerStatus status = ContentBannerStatus.resolve(
				"Y".equals(vo.getExposeYn()), vo.getExposeBgnDate(), vo.getExposeEndDate(), today);
		return ContentAdBannerResponse.from(vo, status);
	}

	/** 이미지 URL 은 선택 — 있으면 자체 업로드 API 반환 경로만 허용(외부 임의 URL 차단). */
	private void validateImageUrl(String imageUrl) {
		String url = blankToNull(imageUrl);
		if (url == null) {
			return; // 선택 필드 — 미입력 허용
		}
		if (!INTERNAL_IMAGE_URL.matcher(url).matches()) {
			throw new BusinessException(ContentErrorCode.CONTENT_IMAGE_INVALID);
		}
	}

	private void validateDateRange(LocalDate bgn, LocalDate end) {
		if (bgn != null && end != null && end.isBefore(bgn)) {
			throw new BusinessException(ContentErrorCode.CONTENT_DATE_RANGE_INVALID);
		}
	}

	/** 노출위치 검증 — AD_BANNER_POSITION 공통코드에 존재해야 함(필수). */
	private void validatePosition(String positionCdRaw) {
		String positionCd = blankToNull(positionCdRaw);
		if (positionCd == null) {
			throw new BusinessException(ContentErrorCode.CONTENT_POSITION_INVALID);
		}
		boolean known = codeService.list(POSITION_GROUP).stream()
				.anyMatch(item -> item.cd().equals(positionCd));
		if (!known) {
			throw new BusinessException(ContentErrorCode.CONTENT_POSITION_INVALID);
		}
	}

	/**
	 * linkTarget 검증(메인배너와 동일 규칙):
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
				if (adBannerMapper.selectActiveHouseExists(houseSn) <= 0) {
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
