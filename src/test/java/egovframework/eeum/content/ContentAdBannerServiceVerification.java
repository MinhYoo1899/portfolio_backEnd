package egovframework.eeum.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
import org.mockito.ArgumentCaptor;

import egovframework.eeum.auth.vo.AuthUser;
import egovframework.eeum.base.exception.BusinessException;
import egovframework.eeum.code.service.code.CodeService;
import egovframework.eeum.code.web.dto.CodeItemResponse;
import egovframework.eeum.content.exception.ContentErrorCode;
import egovframework.eeum.content.service.adbanner.dao.ContentAdBannerMapper;
import egovframework.eeum.content.service.adbanner.impl.ContentAdBannerServiceImpl;
import egovframework.eeum.content.vo.ContentAdBannerVO;
import egovframework.eeum.content.web.dto.ContentAdBannerCreateRequest;
import egovframework.eeum.content.web.dto.ContentAdBannerResponse;
import egovframework.eeum.content.web.dto.ContentAdBannerUpdateRequest;

/**
 * 콘텐츠 광고배너 서비스 검증 — position/imageUrl/linkTarget/노출기간 validation + EXPOSE_YN 정책(생성=Y, 수정=보존) + 404 전이.
 * (Mockito 순수 단위 — 매퍼/코드서비스 목)
 */
class ContentAdBannerServiceVerification {

	private final ContentAdBannerMapper adBannerMapper = mock(ContentAdBannerMapper.class);
	private final CodeService codeService = mock(CodeService.class);
	private final ContentAdBannerServiceImpl service = new ContentAdBannerServiceImpl(adBannerMapper, codeService);

	private static final String VALID_IMG = "/files/download?atchFileSn=7";

	private static AuthUser user() {
		return new AuthUser(9L, "op@eeum.dev", "운영자", "OPERATOR", "ACTIVE", List.of("CONTENT_MNG"));
	}

	private void stubPositionCodes() {
		when(codeService.list("AD_BANNER_POSITION")).thenReturn(List.of(
				new CodeItemResponse("AD_BANNER_POSITION", "MAIN", "메인", 1),
				new CodeItemResponse("AD_BANNER_POSITION", "SEARCH", "검색", 2),
				new CodeItemResponse("AD_BANNER_POSITION", "RECOMMENDED_HOUSE", "추천고택", 3)));
	}

	private void stubLinkTargetCodes() {
		when(codeService.list("CONTENT_LINK_TARGET")).thenReturn(List.of(
				new CodeItemResponse("CONTENT_LINK_TARGET", "PRODUCT", "상품", 1),
				new CodeItemResponse("CONTENT_LINK_TARGET", "URL", "외부URL", 2),
				new CodeItemResponse("CONTENT_LINK_TARGET", "PROMOTION", "기획전", 3)));
	}

	private ContentAdBannerCreateRequest req(String img, String positionCd, String cd, String val,
			LocalDate bgn, LocalDate end) {
		return new ContentAdBannerCreateRequest("MICE 단체 문의", "세미나·워크숍", img, positionCd, cd, val, bgn, end);
	}

	private void stubSavedById(long sn, String exposeYn) {
		ContentAdBannerVO saved = new ContentAdBannerVO();
		saved.setContentAdBannerSn(sn);
		saved.setTitle("MICE 단체 문의");
		saved.setPositionCd("MAIN");
		saved.setExposeYn(exposeYn);
		when(adBannerMapper.selectAdBannerById(sn)).thenReturn(saved);
	}

	@Test
	@DisplayName("있는데 외부 임의 imageUrl → CONTENT_IMAGE_INVALID")
	void externalImageUrl_rejected() {
		BusinessException e = assertThrows(() -> service.create(
				req("https://evil.example.com/x.jpg", "MAIN", null, null, null, null), user()));
		assertEquals(ContentErrorCode.CONTENT_IMAGE_INVALID, e.getErrorCode());
	}

	@Test
	@DisplayName("노출 종료일 < 시작일 → CONTENT_DATE_RANGE_INVALID")
	void dateRange_reversed_rejected() {
		BusinessException e = assertThrows(() -> service.create(
				req(VALID_IMG, "MAIN", null, null, LocalDate.of(2026, 7, 10), LocalDate.of(2026, 7, 1)), user()));
		assertEquals(ContentErrorCode.CONTENT_DATE_RANGE_INVALID, e.getErrorCode());
	}

	@Test
	@DisplayName("공통코드에 없는 positionCd → CONTENT_POSITION_INVALID")
	void position_unknownCode_rejected() {
		stubPositionCodes();
		BusinessException e = assertThrows(() -> service.create(
				req(VALID_IMG, "NOWHERE", null, null, null, null), user()));
		assertEquals(ContentErrorCode.CONTENT_POSITION_INVALID, e.getErrorCode());
	}

	@Test
	@DisplayName("linkTargetCd 없이 값만 지정 → CONTENT_LINK_TARGET_INVALID")
	void linkTarget_valueWithoutCode_rejected() {
		stubPositionCodes();
		BusinessException e = assertThrows(() -> service.create(
				req(VALID_IMG, "MAIN", null, "1024", null, null), user()));
		assertEquals(ContentErrorCode.CONTENT_LINK_TARGET_INVALID, e.getErrorCode());
	}

	@Test
	@DisplayName("공통코드에 없는 linkTargetCd → CONTENT_LINK_TARGET_INVALID")
	void linkTarget_unknownCode_rejected() {
		stubPositionCodes();
		stubLinkTargetCodes();
		BusinessException e = assertThrows(() -> service.create(
				req(VALID_IMG, "MAIN", "FOO", "1", null, null), user()));
		assertEquals(ContentErrorCode.CONTENT_LINK_TARGET_INVALID, e.getErrorCode());
	}

	@Test
	@DisplayName("PRODUCT 인데 값 없음 → CONTENT_LINK_TARGET_VALUE_REQUIRED")
	void linkTarget_product_missingValue_rejected() {
		stubPositionCodes();
		stubLinkTargetCodes();
		BusinessException e = assertThrows(() -> service.create(
				req(VALID_IMG, "MAIN", "PRODUCT", null, null, null), user()));
		assertEquals(ContentErrorCode.CONTENT_LINK_TARGET_VALUE_REQUIRED, e.getErrorCode());
	}

	@Test
	@DisplayName("PRODUCT houseId 미존재 → CONTENT_LINK_TARGET_VALUE_INVALID")
	void linkTarget_product_houseNotExist_rejected() {
		stubPositionCodes();
		stubLinkTargetCodes();
		when(adBannerMapper.selectActiveHouseExists(1024L)).thenReturn(0);
		BusinessException e = assertThrows(() -> service.create(
				req(VALID_IMG, "MAIN", "PRODUCT", "1024", null, null), user()));
		assertEquals(ContentErrorCode.CONTENT_LINK_TARGET_VALUE_INVALID, e.getErrorCode());
	}

	@Test
	@DisplayName("PRODUCT houseId 비숫자 → CONTENT_LINK_TARGET_VALUE_INVALID")
	void linkTarget_product_nonNumeric_rejected() {
		stubPositionCodes();
		stubLinkTargetCodes();
		BusinessException e = assertThrows(() -> service.create(
				req(VALID_IMG, "MAIN", "PRODUCT", "abc", null, null), user()));
		assertEquals(ContentErrorCode.CONTENT_LINK_TARGET_VALUE_INVALID, e.getErrorCode());
	}

	@Test
	@DisplayName("URL 형식 오류 → CONTENT_LINK_TARGET_VALUE_INVALID")
	void linkTarget_url_invalidFormat_rejected() {
		stubPositionCodes();
		stubLinkTargetCodes();
		BusinessException e = assertThrows(() -> service.create(
				req(VALID_IMG, "MAIN", "URL", "not-a-url", null, null), user()));
		assertEquals(ContentErrorCode.CONTENT_LINK_TARGET_VALUE_INVALID, e.getErrorCode());
	}

	@Test
	@DisplayName("이미지 없이(선택 필드 미입력) 등록 성공 — EXPOSE_YN 기본 Y → EXPOSED")
	void create_noImage_defaultExposed_success() {
		stubPositionCodes();
		ArgumentCaptor<ContentAdBannerVO> captor = ArgumentCaptor.forClass(ContentAdBannerVO.class);
		doAnswer(inv -> {
			inv.getArgument(0, ContentAdBannerVO.class).setContentAdBannerSn(200L);
			return 1;
		}).when(adBannerMapper).insertAdBanner(any());
		stubSavedById(200L, "Y");

		ContentAdBannerResponse res = service.create(req(null, "MAIN", null, null, null, null), user());

		assertEquals(200L, res.bannerId());
		assertEquals("EXPOSED", res.status());
		verify(adBannerMapper).insertAdBanner(captor.capture());
		assertEquals("Y", captor.getValue().getExposeYn(), "생성 시 EXPOSE_YN 기본 Y");
		assertNull(captor.getValue().getImgUrl(), "이미지 미입력은 null 저장");
	}

	@Test
	@DisplayName("PRODUCT 유효(존재 고택) → 등록 성공, status 산출")
	void create_product_valid_success() {
		stubPositionCodes();
		stubLinkTargetCodes();
		when(adBannerMapper.selectActiveHouseExists(1024L)).thenReturn(1);
		doAnswer(inv -> {
			inv.getArgument(0, ContentAdBannerVO.class).setContentAdBannerSn(201L);
			return 1;
		}).when(adBannerMapper).insertAdBanner(any());
		stubSavedById(201L, "Y");

		ContentAdBannerResponse res = service.create(req(VALID_IMG, "MAIN", "PRODUCT", "1024", null, null), user());

		assertEquals(201L, res.bannerId());
		assertEquals("EXPOSED", res.status());
		verify(adBannerMapper).insertAdBanner(any());
	}

	@Test
	@DisplayName("PUT 수정은 EXPOSE_YN 을 건드리지 않는다(보존) — VO.exposeYn=null")
	void update_preservesExposeYn() {
		stubPositionCodes();
		ArgumentCaptor<ContentAdBannerVO> captor = ArgumentCaptor.forClass(ContentAdBannerVO.class);
		when(adBannerMapper.updateAdBanner(any())).thenReturn(1);
		stubSavedById(300L, "N");
		ContentAdBannerUpdateRequest r = new ContentAdBannerUpdateRequest(
				"MICE 단체 문의", "부제", VALID_IMG, "MAIN", null, null, null, null);

		service.update(300L, r, user());

		verify(adBannerMapper).updateAdBanner(captor.capture());
		assertNull(captor.getValue().getExposeYn(), "수정 시 EXPOSE_YN 미설정(보존)");
	}

	@Test
	@DisplayName("없는 배너 조회 → CONTENT_NOT_FOUND")
	void get_notFound() {
		when(adBannerMapper.selectAdBannerById(404L)).thenReturn(null);
		BusinessException e = assertThrows(() -> service.get(404L));
		assertEquals(ContentErrorCode.CONTENT_NOT_FOUND, e.getErrorCode());
	}

	@Test
	@DisplayName("없는 배너 수정 → CONTENT_NOT_FOUND")
	void update_notFound() {
		stubPositionCodes();
		when(adBannerMapper.updateAdBanner(any())).thenReturn(0);
		ContentAdBannerUpdateRequest r = new ContentAdBannerUpdateRequest(
				"제목", "부제", VALID_IMG, "MAIN", null, null, null, null);
		BusinessException e = assertThrows(() -> service.update(404L, r, user()));
		assertEquals(ContentErrorCode.CONTENT_NOT_FOUND, e.getErrorCode());
	}

	@Test
	@DisplayName("없는 배너 토글 → CONTENT_NOT_FOUND")
	void toggle_notFound() {
		when(adBannerMapper.updateAdBannerExposeYn(anyLong(), any(), anyLong())).thenReturn(0);
		BusinessException e = assertThrows(() -> service.toggle(404L, false, user()));
		assertEquals(ContentErrorCode.CONTENT_NOT_FOUND, e.getErrorCode());
	}

	@Test
	@DisplayName("토글 성공 → status 재산출(OFF=HIDDEN)")
	void toggle_success() {
		when(adBannerMapper.updateAdBannerExposeYn(anyLong(), any(), anyLong())).thenReturn(1);
		stubSavedById(301L, "N");
		ContentAdBannerResponse res = service.toggle(301L, false, user());
		assertEquals("HIDDEN", res.status());
	}

	@Test
	@DisplayName("없는 배너 삭제 → CONTENT_NOT_FOUND")
	void delete_notFound() {
		when(adBannerMapper.softDeleteAdBanner(anyLong(), anyLong())).thenReturn(0);
		BusinessException e = assertThrows(() -> service.delete(404L, user()));
		assertEquals(ContentErrorCode.CONTENT_NOT_FOUND, e.getErrorCode());
	}

	@Test
	@DisplayName("삭제 성공 → softDeleteAdBanner 호출(soft delete)")
	void delete_success() {
		when(adBannerMapper.softDeleteAdBanner(302L, 9L)).thenReturn(1);
		service.delete(302L, user());
		verify(adBannerMapper).softDeleteAdBanner(302L, 9L);
	}

	@Test
	@DisplayName("목록은 status 산출값 포함")
	void list_includesStatus() {
		ContentAdBannerVO vo = new ContentAdBannerVO();
		vo.setContentAdBannerSn(1L);
		vo.setTitle("MICE 단체 문의");
		vo.setPositionCd("MAIN");
		vo.setExposeYn("Y");
		when(adBannerMapper.selectAdBannerList()).thenReturn(List.of(vo));

		var page = service.list();

		assertEquals(1, page.items().size());
		assertEquals("EXPOSED", page.items().get(0).status());
	}

	// ------------------------------------------------------------------

	private static BusinessException assertThrows(org.junit.jupiter.api.function.Executable exec) {
		return org.junit.jupiter.api.Assertions.assertThrows(BusinessException.class, exec);
	}
}
