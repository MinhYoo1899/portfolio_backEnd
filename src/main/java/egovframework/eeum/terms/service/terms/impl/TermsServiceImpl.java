package egovframework.eeum.terms.service.terms.impl;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import egovframework.eeum.base.exception.BusinessException;
import egovframework.eeum.terms.exception.TermsErrorCode;
import egovframework.eeum.terms.service.terms.TermsService;
import egovframework.eeum.terms.service.terms.dao.TermsMapper;
import egovframework.eeum.terms.vo.TrmsVO;
import lombok.RequiredArgsConstructor;

/**
 * 약관 서비스 구현 — 조회 전용(읽기).
 */
@Service
@RequiredArgsConstructor
public class TermsServiceImpl implements TermsService {

	private final TermsMapper termsMapper;

	@Override
	@Transactional(readOnly = true)
	public List<TrmsVO> getEffectiveTerms() {
		return termsMapper.selectEffectiveTerms();
	}

	@Override
	@Transactional(readOnly = true)
	public TrmsVO getTermsDetail(long trmsSn) {
		TrmsVO terms = termsMapper.selectEffectiveTermsDetail(trmsSn);
		if (terms == null) {
			throw new BusinessException(TermsErrorCode.TERMS_NOT_FOUND);
		}
		return terms;
	}
}
