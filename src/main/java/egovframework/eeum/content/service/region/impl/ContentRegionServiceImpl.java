package egovframework.eeum.content.service.region.impl;

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
import egovframework.eeum.content.service.region.ContentRegionService;
import egovframework.eeum.content.service.region.dao.ContentRegionMapper;
import egovframework.eeum.content.vo.ContentRegionVO;
import egovframework.eeum.content.web.dto.ContentRegionCreateRequest;
import egovframework.eeum.content.web.dto.ContentRegionResponse;
import egovframework.eeum.content.web.dto.ContentRegionSearchRequest;
import egovframework.eeum.content.web.dto.ContentRegionUpdateRequest;
import egovframework.eeum.region.service.region.RegionService;
import lombok.RequiredArgsConstructor;

/**
 * 콘텐츠 지역별 즐길거리 서비스 구현. status 는 조회 시 {@link ContentBannerStatus#resolve} 산출.
 * 생성/수정은 exposeMode 로 EXPOSE_YN, 조회수/댓글수는 읽기전용(수정 시 보존), 삭제는 soft delete.
 */
@Service
@RequiredArgsConstructor
public class ContentRegionServiceImpl implements ContentRegionService {

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");
	private static final String CATEGORY_GROUP = "REGION_CONTENT_CATEGORY";
	private static final String TYPE_GROUP = "REGION_CONTENT_TYPE";
	private static final Pattern INTERNAL_IMAGE_URL = Pattern.compile("^/files/download\\?atchFileSn=\\d+$");

	private final ContentRegionMapper regionMapper;
	private final CodeService codeService;
	private final RegionService regionService;

	@Override
	@Transactional(readOnly = true)
	public PageResponse<ContentRegionResponse> list(ContentRegionSearchRequest request) {
		LocalDate today = LocalDate.now(KST);
		String category = blankToNull(request.category());
		String type = blankToNull(request.type());
		String region = blankToNull(request.region());
		String keyword = blankToNull(request.keyword());
		int page = request.pageOrDefault();
		int size = request.sizeOrDefault();

		long total = regionMapper.countRegion(category, type, region, keyword);
		List<ContentRegionResponse> items = regionMapper
				.selectRegionList(category, type, region, keyword, request.offset(), size).stream()
				.map(vo -> toResponse(vo, today))
				.toList();
		return PageResponse.of(items, total, page, size);
	}

	@Override
	@Transactional(readOnly = true)
	public ContentRegionResponse get(long contentId) {
		ContentRegionVO vo = regionMapper.selectRegionById(contentId);
		if (vo == null) {
			throw new BusinessException(ContentErrorCode.CONTENT_NOT_FOUND);
		}
		return toResponse(vo, LocalDate.now(KST));
	}

	@Override
	@Transactional
	@Audit(actn = "CONTENT_REGION_INSERT", trgtType = "CONTENT_REGION", trgtId = "#result.contentId()")
	public ContentRegionResponse create(ContentRegionCreateRequest request, AuthUser user) {
		validateImageUrl(request.thumbnailUrl());
		validateDateRange(request.exposeBgnDate(), request.exposeEndDate());
		validateCategory(request.categoryCd());
		validateType(request.typeCd());
		validateSido(request.sidoRegionCd());

		ContentRegionVO vo = new ContentRegionVO();
		vo.setCategoryCd(request.categoryCd());
		vo.setTypeCd(request.typeCd());
		vo.setSidoRegionCd(request.sidoRegionCd());
		vo.setThumbnailUrl(request.thumbnailUrl());
		vo.setTitle(request.title());
		vo.setRouteSummary(blankToNull(request.routeSummary()));
		vo.setBodyCn(blankToNull(request.body()));
		vo.setExposeBgnDate(request.exposeBgnDate());
		vo.setExposeEndDate(request.exposeEndDate());
		vo.setExposeYn(request.exposeMode().toExposeYn());
		vo.setRgtrSn(user.userSn());
		regionMapper.insertRegion(vo);

		return get(vo.getContentRegionSn());
	}

	@Override
	@Transactional
	@Audit(actn = "CONTENT_REGION_UPDATE", trgtType = "CONTENT_REGION", trgtId = "#contentId")
	public ContentRegionResponse update(long contentId, ContentRegionUpdateRequest request, AuthUser user) {
		validateImageUrl(request.thumbnailUrl());
		validateDateRange(request.exposeBgnDate(), request.exposeEndDate());
		validateCategory(request.categoryCd());
		validateType(request.typeCd());
		validateSido(request.sidoRegionCd());

		ContentRegionVO vo = new ContentRegionVO();
		vo.setContentRegionSn(contentId);
		vo.setCategoryCd(request.categoryCd());
		vo.setTypeCd(request.typeCd());
		vo.setSidoRegionCd(request.sidoRegionCd());
		vo.setThumbnailUrl(request.thumbnailUrl());
		vo.setTitle(request.title());
		vo.setRouteSummary(blankToNull(request.routeSummary()));
		vo.setBodyCn(blankToNull(request.body()));
		vo.setExposeBgnDate(request.exposeBgnDate());
		vo.setExposeEndDate(request.exposeEndDate());
		vo.setExposeYn(request.exposeMode().toExposeYn());
		vo.setMdfrSn(user.userSn());
		if (regionMapper.updateRegion(vo) == 0) {
			throw new BusinessException(ContentErrorCode.CONTENT_NOT_FOUND);
		}
		return get(contentId);
	}

	@Override
	@Transactional
	@Audit(actn = "CONTENT_REGION_TOGGLE", trgtType = "CONTENT_REGION", trgtId = "#contentId")
	public ContentRegionResponse toggle(long contentId, boolean isActive, AuthUser user) {
		if (regionMapper.updateRegionExposeYn(contentId, isActive ? "Y" : "N", user.userSn()) == 0) {
			throw new BusinessException(ContentErrorCode.CONTENT_NOT_FOUND);
		}
		return get(contentId);
	}

	@Override
	@Transactional
	@Audit(actn = "CONTENT_REGION_DELETE", trgtType = "CONTENT_REGION", trgtId = "#contentId")
	public void delete(long contentId, AuthUser user) {
		if (regionMapper.softDeleteRegion(contentId, user.userSn()) == 0) {
			throw new BusinessException(ContentErrorCode.CONTENT_NOT_FOUND);
		}
	}

	// ------------------------------------------------------------------

	private ContentRegionResponse toResponse(ContentRegionVO vo, LocalDate today) {
		ContentBannerStatus status = ContentBannerStatus.resolve(
				"Y".equals(vo.getExposeYn()), vo.getExposeBgnDate(), vo.getExposeEndDate(), today);
		return ContentRegionResponse.from(vo, status);
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

	private void validateCategory(String cdRaw) {
		String cd = blankToNull(cdRaw);
		if (cd == null || codeService.list(CATEGORY_GROUP).stream().noneMatch(item -> item.cd().equals(cd))) {
			throw new BusinessException(ContentErrorCode.CONTENT_REGION_CATEGORY_INVALID);
		}
	}

	private void validateType(String cdRaw) {
		String cd = blankToNull(cdRaw);
		if (cd == null || codeService.list(TYPE_GROUP).stream().noneMatch(item -> item.cd().equals(cd))) {
			throw new BusinessException(ContentErrorCode.CONTENT_REGION_TYPE_INVALID);
		}
	}

	/** 지역(시도) 검증 — RegionService 재사용(시도 필수·유효). */
	private void validateSido(String sidoRaw) {
		String sido = blankToNull(sidoRaw);
		if (sido == null || !regionService.isValidHouseRegion(sido, null)) {
			throw new BusinessException(ContentErrorCode.CONTENT_REGION_SIDO_INVALID);
		}
	}

	private static String blankToNull(String s) {
		return (s == null || s.isBlank()) ? null : s;
	}
}
