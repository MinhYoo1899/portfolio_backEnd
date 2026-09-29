package egovframework.eeum.terms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import egovframework.eeum.base.response.ApiResponse;
import egovframework.eeum.terms.service.terms.TermsService;
import egovframework.eeum.terms.vo.TrmsVO;
import egovframework.eeum.terms.web.TermsController;
import egovframework.eeum.terms.web.dto.TermsDetailRequest;
import egovframework.eeum.terms.web.dto.TermsDetailResponse;
import egovframework.eeum.terms.web.dto.TermsListItem;

/**
 * 약관 컨트롤러 단위 검증 — VO→record 매핑 및 서비스 위임.
 */
class TermsControllerVerification {

	private final TermsService termsService = mock(TermsService.class);
	private final TermsController controller = new TermsController(termsService);

	@Test
	@DisplayName("list — getEffectiveTerms 결과를 TermsListItem 으로 매핑")
	void listMaps() {
		TrmsVO vo = new TrmsVO();
		vo.setTrmsSn(1L);
		vo.setTrmsTypeCd("SERVICE");
		vo.setVerNo("1.0");
		vo.setTrmsTtl("서비스 이용약관");
		vo.setEsntlYn("Y");
		when(termsService.getEffectiveTerms()).thenReturn(List.of(vo));

		ApiResponse<List<TermsListItem>> response = controller.list();

		assertEquals(1, response.result().size());
		TermsListItem item = response.result().get(0);
		assertEquals(1L, item.trmsSn());
		assertEquals("SERVICE", item.trmsTypeCd());
		assertEquals("Y", item.esntlYn());
	}

	@Test
	@DisplayName("detail — getTermsDetail 결과를 TermsDetailResponse(본문 포함)로 매핑")
	void detailMaps() {
		TrmsVO vo = new TrmsVO();
		vo.setTrmsSn(1L);
		vo.setTrmsTypeCd("SERVICE");
		vo.setVerNo("1.0");
		vo.setTrmsTtl("서비스 이용약관");
		vo.setTrmsCn("본문 내용");
		when(termsService.getTermsDetail(1L)).thenReturn(vo);

		ApiResponse<TermsDetailResponse> response = controller.detail(new TermsDetailRequest(1L));

		assertEquals(1L, response.result().trmsSn());
		assertEquals("본문 내용", response.result().trmsCn());
	}
}
