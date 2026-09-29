package egovframework.eeum.terms.service.terms;

import java.util.List;

import egovframework.eeum.terms.vo.TrmsVO;

/**
 * 약관 서비스 — 시행 중 약관 목록/상세 조회 (가입 약관검증·약관 화면 공용).
 */
public interface TermsService {

	/** 시행 중 약관 목록(유형당 최신 1건, 본문 미포함). 가입 검증·목록 공용. */
	List<TrmsVO> getEffectiveTerms();

	/**
	 * 시행 중 약관 상세(본문 포함). 없으면 TERMS_NOT_FOUND.
	 *
	 * @param trmsSn 약관일련번호
	 */
	TrmsVO getTermsDetail(long trmsSn);
}
