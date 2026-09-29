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
import egovframework.eeum.content.service.popup.dao.ContentPopupMapper;
import egovframework.eeum.content.service.popup.impl.ContentPopupServiceImpl;
import egovframework.eeum.content.vo.ContentPopupVO;
import egovframework.eeum.content.web.dto.ContentPopupCreateRequest;
import egovframework.eeum.content.web.dto.ContentPopupResponse;
import egovframework.eeum.content.web.dto.ContentPopupSearchRequest;

/**
 * 콘텐츠 팝업 서비스 검증 — 이미지/위치/대상/linkTarget/노출기간 validation + exposeMode + 404 + 상태필터.
 */
class ContentPopupServiceVerification {

	private final ContentPopupMapper popupMapper = mock(ContentPopupMapper.class);
	private final CodeService codeService = mock(CodeService.class);
	private final ContentPopupServiceImpl service = new ContentPopupServiceImpl(popupMapper, codeService);

	private static final String VALID_IMG = "/files/download?atchFileSn=7";

	private static AuthUser user() {
		return new AuthUser(9L, "op@eeum.dev", "운영자", "OPERATOR", "ACTIVE", List.of("CONTENT_MNG"));
	}

	private void stubLocation() {
		when(codeService.list("POPUP_EXPOSE_LOCATION")).thenReturn(List.of(
				new CodeItemResponse("POPUP_EXPOSE_LOCATION", "APP_LAUNCH", "앱 실행 시", 1),
				new CodeItemResponse("POPUP_EXPOSE_LOCATION", "HOME", "홈", 2),
				new CodeItemResponse("POPUP_EXPOSE_LOCATION", "SEARCH", "검색", 3)));
	}

	private void stubTarget() {
		when(codeService.list("POPUP_EXPOSE_TARGET")).thenReturn(List.of(
				new CodeItemResponse("POPUP_EXPOSE_TARGET", "REQUIRED", "필수", 1),
				new CodeItemResponse("POPUP_EXPOSE_TARGET", "OPTIONAL", "선택", 2)));
	}

	private ContentPopupCreateRequest req(String img, String loc, String tgt, String cd, String val,
			LocalDate bgn, LocalDate end, ContentExposeMode mode) {
		return new ContentPopupCreateRequest(img, "여름 휴가 기획전 팝업", loc, tgt, cd, val, bgn, end, mode);
	}

	private void stubSaved(long sn, String exposeYn) {
		ContentPopupVO saved = new ContentPopupVO();
		saved.setContentPopupSn(sn);
		saved.setTitle("여름 휴가 기획전 팝업");
		saved.setExposeLocationCd("APP_LAUNCH");
		saved.setExposeTargetCd("REQUIRED");
		saved.setExposeYn(exposeYn);
		when(popupMapper.selectPopupById(sn)).thenReturn(saved);
	}

	@Test
	@DisplayName("외부 임의 imageUrl → CONTENT_IMAGE_INVALID")
	void externalImage_rejected() {
		BusinessException e = assertThrows(() -> service.create(
				req("https://evil.example.com/x.jpg", "APP_LAUNCH", "REQUIRED", null, null, null, null,
						ContentExposeMode.IMMEDIATE), user()));
		assertEquals(ContentErrorCode.CONTENT_IMAGE_INVALID, e.getErrorCode());
	}

	@Test
	@DisplayName("노출 종료일 < 시작일 → CONTENT_DATE_RANGE_INVALID")
	void dateReversed_rejected() {
		BusinessException e = assertThrows(() -> service.create(
				req(VALID_IMG, "APP_LAUNCH", "REQUIRED", null, null, LocalDate.of(2026, 7, 10), LocalDate.of(2026, 7, 1),
						ContentExposeMode.IMMEDIATE), user()));
		assertEquals(ContentErrorCode.CONTENT_DATE_RANGE_INVALID, e.getErrorCode());
	}

	@Test
	@DisplayName("없는 노출위치코드 → CONTENT_POPUP_LOCATION_INVALID")
	void location_unknown_rejected() {
		stubLocation();
		BusinessException e = assertThrows(() -> service.create(
				req(VALID_IMG, "NOWHERE", "REQUIRED", null, null, null, null, ContentExposeMode.IMMEDIATE), user()));
		assertEquals(ContentErrorCode.CONTENT_POPUP_LOCATION_INVALID, e.getErrorCode());
	}

	@Test
	@DisplayName("없는 노출대상코드 → CONTENT_POPUP_TARGET_INVALID")
	void target_unknown_rejected() {
		stubLocation();
		stubTarget();
		BusinessException e = assertThrows(() -> service.create(
				req(VALID_IMG, "APP_LAUNCH", "MAYBE", null, null, null, null, ContentExposeMode.IMMEDIATE), user()));
		assertEquals(ContentErrorCode.CONTENT_POPUP_TARGET_INVALID, e.getErrorCode());
	}

	@Test
	@DisplayName("IMMEDIATE 등록 성공 → status=EXPOSED")
	void create_immediate_exposed() {
		stubLocation();
		stubTarget();
		doAnswer(inv -> {
			inv.getArgument(0, ContentPopupVO.class).setContentPopupSn(100L);
			return 1;
		}).when(popupMapper).insertPopup(any());
		stubSaved(100L, "Y");
		ContentPopupResponse res = service.create(
				req(VALID_IMG, "APP_LAUNCH", "REQUIRED", null, null, null, null, ContentExposeMode.IMMEDIATE), user());
		assertEquals(100L, res.popupId());
		assertEquals("EXPOSED", res.status());
		verify(popupMapper).insertPopup(any());
	}

	@Test
	@DisplayName("SAVE_INACTIVE 등록 → status=HIDDEN")
	void create_saveInactive_hidden() {
		stubLocation();
		stubTarget();
		doAnswer(inv -> {
			inv.getArgument(0, ContentPopupVO.class).setContentPopupSn(101L);
			return 1;
		}).when(popupMapper).insertPopup(any());
		stubSaved(101L, "N");
		ContentPopupResponse res = service.create(
				req(VALID_IMG, "HOME", "OPTIONAL", null, null, null, null, ContentExposeMode.SAVE_INACTIVE), user());
		assertEquals("HIDDEN", res.status());
	}

	@Test
	@DisplayName("없는 팝업 조회/토글/삭제 → CONTENT_NOT_FOUND")
	void notFound_paths() {
		when(popupMapper.selectPopupById(404L)).thenReturn(null);
		assertEquals(ContentErrorCode.CONTENT_NOT_FOUND, assertThrows(() -> service.get(404L)).getErrorCode());
		when(popupMapper.updatePopupExposeYn(anyLong(), any(), anyLong())).thenReturn(0);
		assertEquals(ContentErrorCode.CONTENT_NOT_FOUND,
				assertThrows(() -> service.toggle(404L, false, user())).getErrorCode());
		when(popupMapper.softDeletePopup(anyLong(), anyLong())).thenReturn(0);
		assertEquals(ContentErrorCode.CONTENT_NOT_FOUND,
				assertThrows(() -> service.delete(404L, user())).getErrorCode());
	}

	@Test
	@DisplayName("삭제 성공 → softDeletePopup 호출")
	void delete_success() {
		when(popupMapper.softDeletePopup(5L, 9L)).thenReturn(1);
		service.delete(5L, user());
		verify(popupMapper).softDeletePopup(5L, 9L);
	}

	@Test
	@DisplayName("목록 상태필터 — status=ENDED 는 산출 ENDED 만 남긴다")
	void list_statusFilter() {
		ContentPopupVO active = new ContentPopupVO();
		active.setContentPopupSn(1L);
		active.setExposeYn("Y"); // → EXPOSED
		ContentPopupVO ended = new ContentPopupVO();
		ended.setContentPopupSn(2L);
		ended.setExposeYn("Y");
		ended.setExposeEndDate(LocalDate.of(2000, 1, 1)); // 과거 종료 → ENDED
		when(popupMapper.selectPopupList(any(), any())).thenReturn(List.of(active, ended));

		var page = service.list(new ContentPopupSearchRequest("ENDED", null, null));

		assertEquals(1, page.items().size());
		assertEquals("ENDED", page.items().get(0).status());
		assertEquals(2L, page.items().get(0).popupId());
	}

	// ------------------------------------------------------------------

	private static BusinessException assertThrows(org.junit.jupiter.api.function.Executable exec) {
		return org.junit.jupiter.api.Assertions.assertThrows(BusinessException.class, exec);
	}
}
