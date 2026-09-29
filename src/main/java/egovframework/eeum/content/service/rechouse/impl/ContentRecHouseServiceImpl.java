package egovframework.eeum.content.service.rechouse.impl;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import egovframework.eeum.auth.vo.AuthUser;
import egovframework.eeum.base.audit.Audit;
import egovframework.eeum.base.exception.BusinessException;
import egovframework.eeum.base.response.PageResponse;
import egovframework.eeum.code.service.code.CodeService;
import egovframework.eeum.content.exception.ContentErrorCode;
import egovframework.eeum.content.service.banner.ContentBannerStatus;
import egovframework.eeum.content.service.rechouse.ContentRecHouseService;
import egovframework.eeum.content.service.rechouse.dao.ContentRecHouseMapper;
import egovframework.eeum.content.vo.ContentRecHouseVO;
import egovframework.eeum.content.web.dto.ContentRecHouseCreateRequest;
import egovframework.eeum.content.web.dto.ContentRecHouseOrderRequest;
import egovframework.eeum.content.web.dto.ContentRecHouseResponse;
import egovframework.eeum.content.web.dto.ContentRecHouseUpdateRequest;
import lombok.RequiredArgsConstructor;

/**
 * 콘텐츠 추천고택 서비스 구현. 배너와 동형 — status 는 조회 시 {@link ContentBannerStatus#resolve} 산출.
 * 생성/수정은 exposeMode 로 EXPOSE_YN, 순서는 reorder 전용, 삭제는 soft delete.
 */
@Service
@RequiredArgsConstructor
public class ContentRecHouseServiceImpl implements ContentRecHouseService {

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");
	private static final String CATEGORY_GROUP = "REC_HOUSE_CATEGORY";

	private final ContentRecHouseMapper recHouseMapper;
	private final CodeService codeService;

	@Override
	@Transactional(readOnly = true)
	public PageResponse<ContentRecHouseResponse> list(String recCategoryCd) {
		LocalDate today = LocalDate.now(KST);
		List<ContentRecHouseResponse> items = recHouseMapper.selectRecHouseList(blankToNull(recCategoryCd)).stream()
				.map(vo -> toResponse(vo, today))
				.toList();
		return PageResponse.of(items, items.size(), 1, items.isEmpty() ? 20 : items.size());
	}

	@Override
	@Transactional(readOnly = true)
	public ContentRecHouseResponse get(long recId) {
		ContentRecHouseVO vo = recHouseMapper.selectRecHouseById(recId);
		if (vo == null) {
			throw new BusinessException(ContentErrorCode.CONTENT_NOT_FOUND);
		}
		return toResponse(vo, LocalDate.now(KST));
	}

	@Override
	@Transactional
	@Audit(actn = "CONTENT_REC_HOUSE_INSERT", trgtType = "CONTENT_REC_HOUSE", trgtId = "#result.recId()")
	public ContentRecHouseResponse create(ContentRecHouseCreateRequest request, AuthUser user) {
		validateCategory(request.recCategoryCd());
		validateHouse(request.houseSn());
		validateDateRange(request.exposeBgnDate(), request.exposeEndDate());

		ContentRecHouseVO vo = new ContentRecHouseVO();
		vo.setRecCategoryCd(request.recCategoryCd());
		vo.setHouseSn(request.houseSn());
		vo.setSortOrder(0);
		vo.setExposeBgnDate(request.exposeBgnDate());
		vo.setExposeEndDate(request.exposeEndDate());
		vo.setExposeYn(request.exposeMode().toExposeYn());
		vo.setRgtrSn(user.userSn());
		recHouseMapper.insertRecHouse(vo);

		return get(vo.getContentRecHouseSn());
	}

	@Override
	@Transactional
	@Audit(actn = "CONTENT_REC_HOUSE_UPDATE", trgtType = "CONTENT_REC_HOUSE", trgtId = "#recId")
	public ContentRecHouseResponse update(long recId, ContentRecHouseUpdateRequest request, AuthUser user) {
		validateCategory(request.recCategoryCd());
		validateHouse(request.houseSn());
		validateDateRange(request.exposeBgnDate(), request.exposeEndDate());

		ContentRecHouseVO vo = new ContentRecHouseVO();
		vo.setContentRecHouseSn(recId);
		vo.setRecCategoryCd(request.recCategoryCd());
		vo.setHouseSn(request.houseSn());
		vo.setExposeBgnDate(request.exposeBgnDate());
		vo.setExposeEndDate(request.exposeEndDate());
		vo.setExposeYn(request.exposeMode().toExposeYn());
		vo.setMdfrSn(user.userSn());
		if (recHouseMapper.updateRecHouse(vo) == 0) {
			throw new BusinessException(ContentErrorCode.CONTENT_NOT_FOUND);
		}
		return get(recId);
	}

	@Override
	@Transactional
	@Audit(actn = "CONTENT_REC_HOUSE_TOGGLE", trgtType = "CONTENT_REC_HOUSE", trgtId = "#recId")
	public ContentRecHouseResponse toggle(long recId, boolean isActive, AuthUser user) {
		if (recHouseMapper.updateRecHouseExposeYn(recId, isActive ? "Y" : "N", user.userSn()) == 0) {
			throw new BusinessException(ContentErrorCode.CONTENT_NOT_FOUND);
		}
		return get(recId);
	}

	@Override
	@Transactional
	@Audit(actn = "CONTENT_REC_HOUSE_REORDER", trgtType = "CONTENT_REC_HOUSE", trgtId = "#request.items().size()")
	public void reorder(ContentRecHouseOrderRequest request, AuthUser user) {
		for (ContentRecHouseOrderRequest.OrderItem item : request.items()) {
			if (recHouseMapper.updateRecHouseSortOrder(item.recId(), item.sortOrder(), user.userSn()) == 0) {
				throw new BusinessException(ContentErrorCode.CONTENT_NOT_FOUND);
			}
		}
	}

	@Override
	@Transactional
	@Audit(actn = "CONTENT_REC_HOUSE_DELETE", trgtType = "CONTENT_REC_HOUSE", trgtId = "#recId")
	public void delete(long recId, AuthUser user) {
		if (recHouseMapper.softDeleteRecHouse(recId, user.userSn()) == 0) {
			throw new BusinessException(ContentErrorCode.CONTENT_NOT_FOUND);
		}
	}

	// ------------------------------------------------------------------

	private ContentRecHouseResponse toResponse(ContentRecHouseVO vo, LocalDate today) {
		ContentBannerStatus status = ContentBannerStatus.resolve(
				"Y".equals(vo.getExposeYn()), vo.getExposeBgnDate(), vo.getExposeEndDate(), today);
		return ContentRecHouseResponse.from(vo, status);
	}

	/** 추천분류 검증 — REC_HOUSE_CATEGORY 공통코드에 존재. */
	private void validateCategory(String cdRaw) {
		String cd = blankToNull(cdRaw);
		if (cd == null || codeService.list(CATEGORY_GROUP).stream().noneMatch(item -> item.cd().equals(cd))) {
			throw new BusinessException(ContentErrorCode.CONTENT_REC_CATEGORY_INVALID);
		}
	}

	/** 대상 고택 검증 — 존재+미삭제. */
	private void validateHouse(Long houseSn) {
		if (houseSn == null || recHouseMapper.selectActiveHouseExists(houseSn) <= 0) {
			throw new BusinessException(ContentErrorCode.CONTENT_REC_HOUSE_INVALID);
		}
	}

	private void validateDateRange(LocalDate bgn, LocalDate end) {
		if (bgn != null && end != null && end.isBefore(bgn)) {
			throw new BusinessException(ContentErrorCode.CONTENT_DATE_RANGE_INVALID);
		}
	}

	private static String blankToNull(String s) {
		return (s == null || s.isBlank()) ? null : s;
	}
}
