package egovframework.eeum.terms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import egovframework.eeum.base.exception.BusinessException;
import egovframework.eeum.terms.exception.TermsErrorCode;
import egovframework.eeum.terms.service.terms.dao.TermsMapper;
import egovframework.eeum.terms.service.terms.impl.TermsServiceImpl;
import egovframework.eeum.terms.vo.TrmsVO;

/**
 * 약관 서비스 검증(DB 없이 mock) — 목록 위임, 상세 null→TERMS_NOT_FOUND.
 * (selectEffectiveTerms/Detail 의 실제 SQL 동작은 통합계층(dev 서버)에서 검증)
 */
class TermsServiceVerification {

	private final TermsMapper termsMapper = mock(TermsMapper.class);
	private final TermsServiceImpl service = new TermsServiceImpl(termsMapper);

	@Test
	@DisplayName("getEffectiveTerms — 매퍼 selectEffectiveTerms 결과를 그대로 위임")
	void getEffectiveTermsDelegates() {
		List<TrmsVO> stub = List.of(new TrmsVO());
		when(termsMapper.selectEffectiveTerms()).thenReturn(stub);

		assertSame(stub, service.getEffectiveTerms());
	}

	@Test
	@DisplayName("getTermsDetail — 매퍼 결과를 그대로 반환")
	void getTermsDetailReturnsVo() {
		TrmsVO vo = new TrmsVO();
		vo.setTrmsSn(5L);
		when(termsMapper.selectEffectiveTermsDetail(5L)).thenReturn(vo);

		assertSame(vo, service.getTermsDetail(5L));
	}

	@Test
	@DisplayName("getTermsDetail — 미존재(null)면 TERMS_NOT_FOUND")
	void getTermsDetailNotFound() {
		when(termsMapper.selectEffectiveTermsDetail(99L)).thenReturn(null);

		BusinessException e = assertThrows(BusinessException.class, () -> service.getTermsDetail(99L));
		assertEquals(TermsErrorCode.TERMS_NOT_FOUND, e.getErrorCode());
	}
}
