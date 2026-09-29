package egovframework.eeum.inquiry.service.inquiry;

import egovframework.eeum.auth.vo.AuthUser;
import egovframework.eeum.inquiry.web.dto.InqrAnswerInsertRequest;
import egovframework.eeum.inquiry.web.dto.InqrAnswerUpdateRequest;
import egovframework.eeum.inquiry.web.dto.InqrBlindRequest;
import egovframework.eeum.inquiry.web.dto.InqrDetailResponse;
import egovframework.eeum.inquiry.web.dto.InqrInsertRequest;
import egovframework.eeum.inquiry.web.dto.InqrListResponse;
import egovframework.eeum.inquiry.web.dto.InqrSearchRequest;
import egovframework.eeum.inquiry.web.dto.InqrUpdateRequest;

/**
 * 문의(게시판형 CS Q&A) 서비스.
 *
 * <p>공개 목록/상세(비밀글·블라인드 접근제어), 회원 작성/수정/삭제, 운영자 답변(INQR_ANS)/블라인드(INQR_MODER).
 * 목록/상세의 {@code viewer} 는 비로그인 시 null.
 */
public interface InquiryService {

	/** 문의글 작성(회원). */
	InqrDetailResponse insert(InqrInsertRequest request, AuthUser writer);

	/** 목록(공개 — 비밀글 마스킹/블라인드 가시성/검색 제한). 오프셋·커서(무한스크롤). */
	InqrListResponse list(InqrSearchRequest request, AuthUser viewer);

	/** 상세(공개글 누구나, 비밀글/블라인드는 작성자·운영자만 — 비인가는 404). */
	InqrDetailResponse detail(long inqrSn, AuthUser viewer);

	/** 수정(작성자 본인, 답변 전 PENDING 에만). */
	void update(InqrUpdateRequest request, AuthUser writer);

	/** 삭제(작성자 본인, soft). */
	void delete(long inqrSn, AuthUser writer);

	/** 답변 등록(운영자 INQR_ANS). */
	InqrDetailResponse answer(InqrAnswerInsertRequest request, AuthUser operator);

	/** 답변 수정(운영자 INQR_ANS). */
	void updateAnswer(InqrAnswerUpdateRequest request, AuthUser operator);

	/** 답변 삭제(운영자 INQR_ANS) — 남은 답변 없으면 상태 PENDING 복귀. */
	void deleteAnswer(long ansSn, AuthUser operator);

	/** 블라인드(운영자 INQR_MODER) — 부적절글 비노출. */
	void blind(InqrBlindRequest request, AuthUser operator);

	/** 블라인드 해제(운영자 INQR_MODER). */
	void unblind(long inqrSn, AuthUser operator);
}
