package egovframework.eeum.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import egovframework.eeum.auth.vo.AuthUser;
import egovframework.eeum.base.exception.BusinessException;
import egovframework.eeum.code.service.code.CodeService;
import egovframework.eeum.code.web.dto.CodeItemResponse;
import egovframework.eeum.content.exception.ContentErrorCode;
import egovframework.eeum.content.service.banner.ContentExposeMode;
import egovframework.eeum.content.service.banner.dao.ContentBannerMapper;
import egovframework.eeum.content.service.banner.impl.ContentBannerServiceImpl;
import egovframework.eeum.content.vo.ContentMainBannerVO;
import egovframework.eeum.content.web.dto.ContentBannerCreateRequest;
import egovframework.eeum.content.web.dto.ContentBannerResponse;
import egovframework.eeum.content.web.dto.ContentBannerUpdateRequest;

/**
 * 콘텐츠 메인배너 서비스 검증 — imageUrl/linkTarget/노출기간 validation + 없음(404) 전이.
 * (Mockito 순수 단위 — 매퍼/코드서비스 목)
 */
class ContentBannerServiceVerification {

	private final ContentBannerMapper bannerMapper = mock(ContentBannerMapper.class);
	private final CodeService codeService = mock(CodeService.class);
	private final ContentBannerServiceImpl service = new ContentBannerServiceImpl(bannerMapper, codeService);

	private static final String VALID_IMG = "/files/download?atchFileSn=7";

	private static AuthUser user() {
		return new AuthUser(9L, "op@eeum.dev", "운영자", "OPERATOR", "ACTIVE", List.of("CONTENT_MNG"));
	}

	private void stubLinkTargetCodes() {
		when(codeService.list("CONTENT_LINK_TARGET")).thenReturn(List.of(
				new CodeItemResponse("CONTENT_LINK_TARGET", "PRODUCT", "상품", 1),
				new CodeItemResponse("CONTENT_LINK_TARGET", "URL", "외부URL", 2),
				new CodeItemResponse("CONTENT_LINK_TARGET", "PROMOTION", "기획전", 3)));
	}

	private ContentBannerCreateRequest req(String img, String cd, String val, LocalDate bgn, LocalDate end) {
		return new ContentBannerCreateRequest(img, "메인카피", "서브카피", cd, val, bgn, end, ContentExposeMode.IMMEDIATE);
	}

	@Test
	@DisplayName("외부 임의 imageUrl → CONTENT_IMAGE_INVALID")
	void externalImageUrl_rejected() {
		BusinessException e = assertThrows(() -> service.create(
				req("https://evil.example.com/x.jpg", null, null, null, null), user()));
		assertEquals(ContentErrorCode.CONTENT_IMAGE_INVALID, e.getErrorCode());
	}

	@Test
	@DisplayName("노출 종료일 < 시작일 → CONTENT_DATE_RANGE_INVALID")
	void dateRange_reversed_rejected() {
		BusinessException e = assertThrows(() -> service.create(
				req(VALID_IMG, null, null, LocalDate.of(2026, 7, 10), LocalDate.of(2026, 7, 1)), user()));
		assertEquals(ContentErrorCode.CONTENT_DATE_RANGE_INVALID, e.getErrorCode());
	}

	@Test
	@DisplayName("linkTargetCd 없이 값만 지정 → CONTENT_LINK_TARGET_INVALID")
	void linkTarget_valueWithoutCode_rejected() {
		BusinessException e = assertThrows(() -> service.create(
				req(VALID_IMG, null, "1024", null, null), user()));
		assertEquals(ContentErrorCode.CONTENT_LINK_TARGET_INVALID, e.getErrorCode());
	}

	@Test
	@DisplayName("공통코드에 없는 linkTargetCd → CONTENT_LINK_TARGET_INVALID")
	void linkTarget_unknownCode_rejected() {
		stubLinkTargetCodes();
		BusinessException e = assertThrows(() -> service.create(
				req(VALID_IMG, "FOO", "1", null, null), user()));
		assertEquals(ContentErrorCode.CONTENT_LINK_TARGET_INVALID, e.getErrorCode());
	}

	@Test
	@DisplayName("PRODUCT 인데 값 없음 → CONTENT_LINK_TARGET_VALUE_REQUIRED")
	void linkTarget_product_missingValue_rejected() {
		stubLinkTargetCodes();
		BusinessException e = assertThrows(() -> service.create(
				req(VALID_IMG, "PRODUCT", null, null, null), user()));
		assertEquals(ContentErrorCode.CONTENT_LINK_TARGET_VALUE_REQUIRED, e.getErrorCode());
	}

	@Test
	@DisplayName("PRODUCT houseId 미존재 → CONTENT_LINK_TARGET_VALUE_INVALID")
	void linkTarget_product_houseNotExist_rejected() {
		stubLinkTargetCodes();
		when(bannerMapper.selectActiveHouseExists(1024L)).thenReturn(0);
		BusinessException e = assertThrows(() -> service.create(
				req(VALID_IMG, "PRODUCT", "1024", null, null), user()));
		assertEquals(ContentErrorCode.CONTENT_LINK_TARGET_VALUE_INVALID, e.getErrorCode());
	}

	@Test
	@DisplayName("PRODUCT houseId 비숫자 → CONTENT_LINK_TARGET_VALUE_INVALID")
	void linkTarget_product_nonNumeric_rejected() {
		stubLinkTargetCodes();
		BusinessException e = assertThrows(() -> service.create(
				req(VALID_IMG, "PRODUCT", "abc", null, null), user()));
		assertEquals(ContentErrorCode.CONTENT_LINK_TARGET_VALUE_INVALID, e.getErrorCode());
	}

	@Test
	@DisplayName("URL 형식 오류 → CONTENT_LINK_TARGET_VALUE_INVALID")
	void linkTarget_url_invalidFormat_rejected() {
		stubLinkTargetCodes();
		BusinessException e = assertThrows(() -> service.create(
				req(VALID_IMG, "URL", "not-a-url", null, null), user()));
		assertEquals(ContentErrorCode.CONTENT_LINK_TARGET_VALUE_INVALID, e.getErrorCode());
	}

	@Test
	@DisplayName("PRODUCT 유효(존재 고택) → 등록 성공, status 산출")
	void create_product_valid_success() {
		stubLinkTargetCodes();
		when(bannerMapper.selectActiveHouseExists(1024L)).thenReturn(1);
		doAnswer(inv -> {
			inv.getArgument(0, ContentMainBannerVO.class).setContentMainBannerSn(100L);
			return 1;
		}).when(bannerMapper).insertBanner(any());
		ContentMainBannerVO saved = new ContentMainBannerVO();
		saved.setContentMainBannerSn(100L);
		saved.setImgUrl(VALID_IMG);
		saved.setMainCopy("메인카피");
		saved.setExposeYn("Y");
		when(bannerMapper.selectBannerById(100L)).thenReturn(saved);

		ContentBannerResponse res = service.create(req(VALID_IMG, "PRODUCT", "1024", null, null), user());

		assertEquals(100L, res.bannerId());
		assertEquals("EXPOSED", res.status());
		verify(bannerMapper).insertBanner(any());
	}

	@Test
	@DisplayName("PROMOTION 은 문자열 필수만 — 대상검증 없이 등록 성공")
	void create_promotion_stringOnly_success() {
		stubLinkTargetCodes();
		doAnswer(inv -> {
			inv.getArgument(0, ContentMainBannerVO.class).setContentMainBannerSn(101L);
			return 1;
		}).when(bannerMapper).insertBanner(any());
		ContentMainBannerVO saved = new ContentMainBannerVO();
		saved.setContentMainBannerSn(101L);
		saved.setExposeYn("N");
		when(bannerMapper.selectBannerById(101L)).thenReturn(saved);

		ContentBannerResponse res = service.create(req(VALID_IMG, "PROMOTION", "evt-2026", null, null), user());

		assertEquals(101L, res.bannerId());
		assertEquals("HIDDEN", res.status()); // exposeYn=N
		verify(bannerMapper, org.mockito.Mockito.never()).selectActiveHouseExists(anyLong());
	}

	@Test
	@DisplayName("없는 배너 조회 → CONTENT_NOT_FOUND")
	void get_notFound() {
		when(bannerMapper.selectBannerById(404L)).thenReturn(null);
		BusinessException e = assertThrows(() -> service.get(404L));
		assertEquals(ContentErrorCode.CONTENT_NOT_FOUND, e.getErrorCode());
	}

	@Test
	@DisplayName("없는 배너 수정 → CONTENT_NOT_FOUND")
	void update_notFound() {
		when(bannerMapper.updateBanner(any())).thenReturn(0);
		ContentBannerUpdateRequest r = new ContentBannerUpdateRequest(VALID_IMG, "메인", "서브", null, null,
				null, null, ContentExposeMode.IMMEDIATE);
		BusinessException e = assertThrows(() -> service.update(404L, r, user()));
		assertEquals(ContentErrorCode.CONTENT_NOT_FOUND, e.getErrorCode());
	}

	@Test
	@DisplayName("없는 배너 토글 → CONTENT_NOT_FOUND")
	void toggle_notFound() {
		when(bannerMapper.updateBannerExposeYn(anyLong(), any(), anyLong())).thenReturn(0);
		BusinessException e = assertThrows(() -> service.toggle(404L, false, user()));
		assertEquals(ContentErrorCode.CONTENT_NOT_FOUND, e.getErrorCode());
	}

	// ------------------------------------------------------------------

	private static BusinessException assertThrows(org.junit.jupiter.api.function.Executable exec) {
		return org.junit.jupiter.api.Assertions.assertThrows(BusinessException.class, exec);
	}
}
