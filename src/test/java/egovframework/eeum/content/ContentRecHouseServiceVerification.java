package egovframework.eeum.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
import egovframework.eeum.content.service.rechouse.dao.ContentRecHouseMapper;
import egovframework.eeum.content.service.rechouse.impl.ContentRecHouseServiceImpl;
import egovframework.eeum.content.vo.ContentRecHouseVO;
import egovframework.eeum.content.web.dto.ContentRecHouseCreateRequest;
import egovframework.eeum.content.web.dto.ContentRecHouseOrderRequest;
import egovframework.eeum.content.web.dto.ContentRecHouseResponse;

/**
 * 콘텐츠 추천고택 서비스 검증 — 분류/고택/노출기간 validation + exposeMode + 순서저장 + 404.
 */
class ContentRecHouseServiceVerification {

	private final ContentRecHouseMapper recHouseMapper = mock(ContentRecHouseMapper.class);
	private final CodeService codeService = mock(CodeService.class);
	private final ContentRecHouseServiceImpl service = new ContentRecHouseServiceImpl(recHouseMapper, codeService);

	private static AuthUser user() {
		return new AuthUser(9L, "op@eeum.dev", "운영자", "OPERATOR", "ACTIVE", List.of("CONTENT_MNG"));
	}

	private void stubCategory() {
		when(codeService.list("REC_HOUSE_CATEGORY")).thenReturn(List.of(
				new CodeItemResponse("REC_HOUSE_CATEGORY", "TODAY_DEAL", "당일 특가", 1),
				new CodeItemResponse("REC_HOUSE_CATEGORY", "BEST", "BEST", 2),
				new CodeItemResponse("REC_HOUSE_CATEGORY", "MD_PICK", "MD 추천", 3)));
	}

	private ContentRecHouseCreateRequest req(String cat, Long houseSn, LocalDate bgn, LocalDate end,
			ContentExposeMode mode) {
		return new ContentRecHouseCreateRequest(cat, houseSn, bgn, end, mode);
	}

	private void stubSaved(long sn, String exposeYn) {
		ContentRecHouseVO saved = new ContentRecHouseVO();
		saved.setContentRecHouseSn(sn);
		saved.setRecCategoryCd("MD_PICK");
		saved.setHouseSn(78L);
		saved.setHouseNm("영월관");
		saved.setExposeYn(exposeYn);
		when(recHouseMapper.selectRecHouseById(sn)).thenReturn(saved);
	}

	@Test
	@DisplayName("없는 분류코드 → CONTENT_REC_CATEGORY_INVALID")
	void category_unknown_rejected() {
		stubCategory();
		BusinessException e = assertThrows(() -> service.create(
				req("VIP", 78L, null, null, ContentExposeMode.IMMEDIATE), user()));
		assertEquals(ContentErrorCode.CONTENT_REC_CATEGORY_INVALID, e.getErrorCode());
	}

	@Test
	@DisplayName("미존재 고택 → CONTENT_REC_HOUSE_INVALID")
	void house_notExist_rejected() {
		stubCategory();
		when(recHouseMapper.selectActiveHouseExists(999L)).thenReturn(0);
		BusinessException e = assertThrows(() -> service.create(
				req("MD_PICK", 999L, null, null, ContentExposeMode.IMMEDIATE), user()));
		assertEquals(ContentErrorCode.CONTENT_REC_HOUSE_INVALID, e.getErrorCode());
	}

	@Test
	@DisplayName("노출 종료일 < 시작일 → CONTENT_DATE_RANGE_INVALID")
	void dateReversed_rejected() {
		stubCategory();
		when(recHouseMapper.selectActiveHouseExists(78L)).thenReturn(1);
		BusinessException e = assertThrows(() -> service.create(
				req("MD_PICK", 78L, LocalDate.of(2026, 7, 10), LocalDate.of(2026, 7, 1), ContentExposeMode.IMMEDIATE),
				user()));
		assertEquals(ContentErrorCode.CONTENT_DATE_RANGE_INVALID, e.getErrorCode());
	}

	@Test
	@DisplayName("IMMEDIATE 등록 성공 → EXPOSED, 고택명 포함")
	void create_immediate_exposed() {
		stubCategory();
		when(recHouseMapper.selectActiveHouseExists(78L)).thenReturn(1);
		doAnswer(inv -> {
			inv.getArgument(0, ContentRecHouseVO.class).setContentRecHouseSn(100L);
			return 1;
		}).when(recHouseMapper).insertRecHouse(any());
		stubSaved(100L, "Y");
		ContentRecHouseResponse res = service.create(
				req("MD_PICK", 78L, null, null, ContentExposeMode.IMMEDIATE), user());
		assertEquals(100L, res.recId());
		assertEquals("EXPOSED", res.status());
		assertEquals("영월관", res.houseName());
	}

	@Test
	@DisplayName("SAVE_INACTIVE 등록 → HIDDEN")
	void create_saveInactive_hidden() {
		stubCategory();
		when(recHouseMapper.selectActiveHouseExists(78L)).thenReturn(1);
		doAnswer(inv -> {
			inv.getArgument(0, ContentRecHouseVO.class).setContentRecHouseSn(101L);
			return 1;
		}).when(recHouseMapper).insertRecHouse(any());
		stubSaved(101L, "N");
		ContentRecHouseResponse res = service.create(
				req("BEST", 78L, null, null, ContentExposeMode.SAVE_INACTIVE), user());
		assertEquals("HIDDEN", res.status());
	}

	@Test
	@DisplayName("순서 저장 — 각 항목 SORT_ORDER 갱신 호출")
	void reorder_success() {
		when(recHouseMapper.updateRecHouseSortOrder(anyLong(), anyInt(), anyLong())).thenReturn(1);
		service.reorder(new ContentRecHouseOrderRequest(List.of(
				new ContentRecHouseOrderRequest.OrderItem(10L, 0),
				new ContentRecHouseOrderRequest.OrderItem(11L, 1))), user());
		verify(recHouseMapper).updateRecHouseSortOrder(10L, 0, 9L);
		verify(recHouseMapper).updateRecHouseSortOrder(11L, 1, 9L);
	}

	@Test
	@DisplayName("순서 저장 — 없는 항목 포함 시 404")
	void reorder_notFound() {
		when(recHouseMapper.updateRecHouseSortOrder(404L, 0, 9L)).thenReturn(0);
		BusinessException e = assertThrows(() -> service.reorder(new ContentRecHouseOrderRequest(
				List.of(new ContentRecHouseOrderRequest.OrderItem(404L, 0))), user()));
		assertEquals(ContentErrorCode.CONTENT_NOT_FOUND, e.getErrorCode());
	}

	@Test
	@DisplayName("없는 추천 조회/토글/삭제 → CONTENT_NOT_FOUND")
	void notFound_paths() {
		when(recHouseMapper.selectRecHouseById(404L)).thenReturn(null);
		assertEquals(ContentErrorCode.CONTENT_NOT_FOUND, assertThrows(() -> service.get(404L)).getErrorCode());
		when(recHouseMapper.updateRecHouseExposeYn(anyLong(), any(), anyLong())).thenReturn(0);
		assertEquals(ContentErrorCode.CONTENT_NOT_FOUND,
				assertThrows(() -> service.toggle(404L, false, user())).getErrorCode());
		when(recHouseMapper.softDeleteRecHouse(anyLong(), anyLong())).thenReturn(0);
		assertEquals(ContentErrorCode.CONTENT_NOT_FOUND,
				assertThrows(() -> service.delete(404L, user())).getErrorCode());
	}

	@Test
	@DisplayName("삭제 성공 → softDeleteRecHouse 호출")
	void delete_success() {
		when(recHouseMapper.softDeleteRecHouse(5L, 9L)).thenReturn(1);
		service.delete(5L, user());
		verify(recHouseMapper).softDeleteRecHouse(5L, 9L);
		verify(recHouseMapper, never()).selectActiveHouseExists(anyLong());
	}

	@Test
	@DisplayName("목록 — 분류 필터 위임 + status 산출")
	void list_delegatesFilter() {
		ContentRecHouseVO vo = new ContentRecHouseVO();
		vo.setContentRecHouseSn(1L);
		vo.setRecCategoryCd("MD_PICK");
		vo.setHouseSn(78L);
		vo.setExposeYn("Y");
		when(recHouseMapper.selectRecHouseList("MD_PICK")).thenReturn(List.of(vo));
		var page = service.list("MD_PICK");
		assertEquals(1, page.items().size());
		assertEquals("EXPOSED", page.items().get(0).status());
	}

	// ------------------------------------------------------------------

	private static BusinessException assertThrows(org.junit.jupiter.api.function.Executable exec) {
		return org.junit.jupiter.api.Assertions.assertThrows(BusinessException.class, exec);
	}
}
