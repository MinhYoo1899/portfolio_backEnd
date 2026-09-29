package egovframework.eeum.inquiry.service.inquiry.dao;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import egovframework.eeum.inquiry.vo.InqrAnsInfoVO;
import egovframework.eeum.inquiry.vo.InqrInfoVO;

/**
 * 문의(CS Q&A) 매퍼.
 *
 * <p>비밀글 마스킹·검색 제한은 SQL 에서 강제(viewerSn/isOperator 파라미터 — 서비스 의존 금지, codex).
 * 비로그인 viewerSn 은 -1(어떤 USER_SN 과도 불일치) 전달.
 */
@Mapper
public interface InquiryMapper {

	// ---- 문의글 ----

	void insertInquiry(InqrInfoVO inquiry);

	/** 상세(작성자명 조인). DEL_YN='N'. 접근권한(비밀글/블라인드)은 서비스가 판정. */
	InqrInfoVO selectInquiryDetail(@Param("inqrSn") long inqrSn);

	/** 상태/소유권 검증용 행 잠금(FOR UPDATE). */
	InqrInfoVO selectInquiryForUpdate(@Param("inqrSn") long inqrSn);

	/** 목록(오프셋). 비밀글 마스킹·블라인드 가시성·검색 제한 SQL 강제. */
	List<InqrInfoVO> selectInquiryListOffset(@Param("viewerSn") long viewerSn, @Param("isOperator") String isOperator,
			@Param("mine") boolean mine, @Param("inqrType") String inqrType, @Param("ansStts") String ansStts,
			@Param("keyword") String keyword, @Param("limit") int limit, @Param("offset") int offset);

	long countInquiryList(@Param("viewerSn") long viewerSn, @Param("isOperator") String isOperator,
			@Param("mine") boolean mine, @Param("inqrType") String inqrType, @Param("ansStts") String ansStts,
			@Param("keyword") String keyword);

	/** 목록(커서 — 무한스크롤). hasNext 판정 위해 limit+1 건 요청. */
	List<InqrInfoVO> selectInquiryListCursor(@Param("viewerSn") long viewerSn, @Param("isOperator") String isOperator,
			@Param("mine") boolean mine, @Param("inqrType") String inqrType, @Param("ansStts") String ansStts,
			@Param("keyword") String keyword, @Param("cursor") Long cursor, @Param("limit") int limit);

	/** 작성자 수정 — 본인 + 미삭제일 때만. */
	int updateInquiry(@Param("inqrSn") long inqrSn, @Param("userSn") long userSn,
			@Param("inqrTypeCd") String inqrTypeCd, @Param("inqrTtl") String inqrTtl,
			@Param("inqrCn") String inqrCn, @Param("prvtPstYn") String prvtPstYn);

	/** 작성자 삭제(soft) — 본인 + 미삭제일 때만. */
	int softDeleteInquiry(@Param("inqrSn") long inqrSn, @Param("userSn") long userSn);

	/** 답변상태 갱신. */
	int updateAnswStts(@Param("inqrSn") long inqrSn, @Param("ansSttsCd") String ansSttsCd,
			@Param("mdfrSn") long mdfrSn);

	/** 블라인드(운영자) — 게시상태 BLINDED. 미삭제+NORMAL일 때만. */
	int blindInquiry(@Param("inqrSn") long inqrSn, @Param("pstgSttsRsn") String pstgSttsRsn,
			@Param("mdfrSn") long mdfrSn);

	/** 블라인드 해제(운영자) — 게시상태 NORMAL. */
	int unblindInquiry(@Param("inqrSn") long inqrSn, @Param("mdfrSn") long mdfrSn);

	// ---- 답변 ----

	void insertAnswer(InqrAnsInfoVO answer);

	List<InqrAnsInfoVO> selectAnswersByInquiry(@Param("inqrSn") long inqrSn);

	/** 답변 기본 조회(소속 문의/삭제여부) — 부모 잠금 전 inqrSn 확보용(비잠금, codex 코드검토). */
	InqrAnsInfoVO selectAnswer(@Param("ansSn") long ansSn);

	int updateAnswer(@Param("ansSn") long ansSn, @Param("ansCn") String ansCn, @Param("mdfrSn") long mdfrSn);

	int softDeleteAnswer(@Param("ansSn") long ansSn, @Param("mdfrSn") long mdfrSn);

	int countActiveAnswers(@Param("inqrSn") long inqrSn);

	/** 활성 문의유형 코드 존재 검증(공통코드 INQR_TYPE — 임의 타입 저장 차단). */
	int countActiveInqrType(@Param("inqrTypeCd") String inqrTypeCd);
}
