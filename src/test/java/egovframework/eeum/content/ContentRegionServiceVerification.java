package egovframework.eeum.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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
import egovframework.eeum.content.service.region.dao.ContentRegionMapper;
import egovframework.eeum.content.service.region.impl.ContentRegionServiceImpl;
import egovframework.eeum.content.vo.ContentRegionVO;
import egovframework.eeum.content.web.dto.ContentRegionCreateRequest;
import egovframework.eeum.content.web.dto.ContentRegionResponse;
import egovframework.eeum.content.web.dto.ContentRegionSearchRequest;
import egovframework.eeum.region.service.region.RegionService;

/**
 * 콘텐츠 즐길거리 서비스 검증 — 이미지/카테고리/유형/지역/노출기간 validation + exposeMode + 조회수0 + 페이지네이션 + 404.
 */
class ContentRegionServiceVerification {

	private final ContentRegionMapper regionMapper = mock(ContentRegionMapper.class);
	private final CodeService codeService = mock(CodeService.class);
	private final RegionService regionService = mock(RegionService.class);
	private final ContentRegionServiceImpl service =
			new ContentRegionServiceImpl(regionMapper, codeService, regionService);

	private static final String VALID_IMG = "/files/download?atchFileSn=7";

	private static AuthUser user() {
		return new AuthUser(9L, "op@eeum.dev", "운영자", "OPERATOR", "ACTIVE", List.of("CONTENT_MNG"));
	}

	private void stubCategory() {
		when(codeService.list("REGION_CONTENT_CATEGORY")).thenReturn(List.of(
				new CodeItemResponse("REGION_CONTENT_CATEGORY", "EDITOR_PICK", "이번주 에디터 픽", 1),
				new CodeItemResponse("REGION_CONTENT_CATEGORY", "TRENDING", "지금 뜨는 즐길거리", 2),
				new CodeItemResponse("REGION_CONTENT_CATEGORY", "REC_COURSE", "에디터 추천 코스", 3)));
	}

	private void stubType() {
		when(codeService.list("REGION_CONTENT_TYPE")).thenReturn(List.of(
				new CodeItemResponse("REGION_CONTENT_TYPE", "DAY_COURSE", "당일 코스", 1),
				new CodeItemResponse("REGION_CONTENT_TYPE", "TOUR", "관광", 2)));
	}

	private ContentRegionCreateRequest req(String img, String cat, String type, String sido, LocalDate bgn,
			LocalDate end) {
		return new ContentRegionCreateRequest(cat, type, sido, img, "고택과 함께하는 구례 하루", "운조루 → 사성암", "<p>본문</p>",
				bgn, end, ContentExposeMode.IMMEDIATE);
	}

	private void stubSaved(long sn, String exposeYn) {
		ContentRegionVO saved = new ContentRegionVO();
		saved.setContentRegionSn(sn);
		saved.setCategoryCd("EDITOR_PICK");
		saved.setTypeCd("DAY_COURSE");
		saved.setSidoRegionCd("46");
		saved.setTitle("고택과 함께하는 구례 하루");
		saved.setExposeYn(exposeYn);
		when(regionMapper.selectRegionById(sn)).thenReturn(saved);
	}

	@Test
	@DisplayName("외부 임의 thumbnail → CONTENT_IMAGE_INVALID")
	void externalImage_rejected() {
		BusinessException e = assertThrows(() -> service.create(
				req("https://evil.example.com/x.jpg", "EDITOR_PICK", "DAY_COURSE", "46", null, null), user()));
		assertEquals(ContentErrorCode.CONTENT_IMAGE_INVALID, e.getErrorCode());
	}

	@Test
	@DisplayName("없는 카테고리 → CONTENT_REGION_CATEGORY_INVALID")
	void category_unknown_rejected() {
		stubCategory();
		BusinessException e = assertThrows(() -> service.create(
				req(VALID_IMG, "NOPE", "DAY_COURSE", "46", null, null), user()));
		assertEquals(ContentErrorCode.CONTENT_REGION_CATEGORY_INVALID, e.getErrorCode());
	}

	@Test
	@DisplayName("없는 유형 → CONTENT_REGION_TYPE_INVALID")
	void type_unknown_rejected() {
		stubCategory();
		stubType();
		BusinessException e = assertThrows(() -> service.create(
				req(VALID_IMG, "EDITOR_PICK", "NIGHT", "46", null, null), user()));
		assertEquals(ContentErrorCode.CONTENT_REGION_TYPE_INVALID, e.getErrorCode());
	}

	@Test
	@DisplayName("무효 시도코드(RegionService false) → CONTENT_REGION_SIDO_INVALID")
	void sido_invalid_rejected() {
		stubCategory();
		stubType();
		when(regionService.isValidHouseRegion("99", null)).thenReturn(false);
		BusinessException e = assertThrows(() -> service.create(
				req(VALID_IMG, "EDITOR_PICK", "DAY_COURSE", "99", null, null), user()));
		assertEquals(ContentErrorCode.CONTENT_REGION_SIDO_INVALID, e.getErrorCode());
	}

	@Test
	@DisplayName("IMMEDIATE 등록 성공 → EXPOSED, 조회수 0")
	void create_immediate_exposed() {
		stubCategory();
		stubType();
		when(regionService.isValidHouseRegion("46", null)).thenReturn(true);
		doAnswer(inv -> {
			inv.getArgument(0, ContentRegionVO.class).setContentRegionSn(100L);
			return 1;
		}).when(regionMapper).insertRegion(any());
		stubSaved(100L, "Y");
		ContentRegionResponse res = service.create(
				req(VALID_IMG, "EDITOR_PICK", "DAY_COURSE", "46", null, null), user());
		assertEquals(100L, res.contentId());
		assertEquals("EXPOSED", res.status());
		assertEquals(0, res.viewCount());
		assertEquals(0, res.commentCount());
	}

	@Test
	@DisplayName("목록 — count + offset 페이지네이션(총건수/페이지 반영)")
	void list_pagination() {
		ContentRegionVO vo = new ContentRegionVO();
		vo.setContentRegionSn(1L);
		vo.setExposeYn("Y");
		when(regionMapper.countRegion(isNull(), isNull(), eq("46"), isNull())).thenReturn(20L);
		when(regionMapper.selectRegionList(isNull(), isNull(), eq("46"), isNull(), eq(8), eq(8)))
				.thenReturn(List.of(vo));

		var page = service.list(new ContentRegionSearchRequest(null, null, "46", null, 2, 8));

		assertEquals(20L, page.total());
		assertEquals(2, page.page());
		assertEquals(8, page.pageSize());
		assertEquals(3, page.totalPages()); // ceil(20/8)
		assertEquals(1, page.items().size());
		verify(regionMapper).selectRegionList(isNull(), isNull(), eq("46"), isNull(), eq(8), eq(8)); // offset=(2-1)*8=8
	}

	@Test
	@DisplayName("없는 즐길거리 조회/토글/삭제 → CONTENT_NOT_FOUND")
	void notFound_paths() {
		when(regionMapper.selectRegionById(404L)).thenReturn(null);
		assertEquals(ContentErrorCode.CONTENT_NOT_FOUND, assertThrows(() -> service.get(404L)).getErrorCode());
		when(regionMapper.updateRegionExposeYn(anyLong(), any(), anyLong())).thenReturn(0);
		assertEquals(ContentErrorCode.CONTENT_NOT_FOUND,
				assertThrows(() -> service.toggle(404L, false, user())).getErrorCode());
		when(regionMapper.softDeleteRegion(anyLong(), anyLong())).thenReturn(0);
		assertEquals(ContentErrorCode.CONTENT_NOT_FOUND,
				assertThrows(() -> service.delete(404L, user())).getErrorCode());
	}

	@Test
	@DisplayName("삭제 성공 → softDeleteRegion 호출")
	void delete_success() {
		when(regionMapper.softDeleteRegion(5L, 9L)).thenReturn(1);
		service.delete(5L, user());
		verify(regionMapper).softDeleteRegion(5L, 9L);
	}

	// ------------------------------------------------------------------

	private static BusinessException assertThrows(org.junit.jupiter.api.function.Executable exec) {
		return org.junit.jupiter.api.Assertions.assertThrows(BusinessException.class, exec);
	}
}
