package egovframework.eeum.inquiry.vo;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 문의글 VO — INQR_INFO 매핑.
 *
 * <p>{@code writerNm}(작성자명 조인)·{@code answerCnt}(활성 답변 수)는 비테이블 필드.
 * 비밀글 마스킹은 SQL 에서 {@code inqrTtl}/{@code writerNm} 를 NULL 로 내려 강제(서비스 의존 금지 — codex).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InqrInfoVO {

	/** 문의일련번호(PK). */
	private Long inqrSn;

	/** 작성자(사용자일련번호) — 소유권 판정 기준. */
	private Long userSn;

	/** 문의유형코드(공통코드 INQR_TYPE). */
	private String inqrTypeCd;

	/** 문의제목(비밀글 마스킹 시 NULL). */
	private String inqrTtl;

	/** 문의내용. */
	private String inqrCn;

	/** 비밀글(비공개게시물)여부. */
	private String prvtPstYn;

	/** 답변상태(PENDING/ANSWERED). */
	private String ansSttsCd;

	/** 게시상태(NORMAL/BLINDED — 운영자 블라인드). */
	private String pstgSttsCd;

	/** 게시상태사유(블라인드 사유). */
	private String pstgSttsRsn;

	/** 등록일시. */
	private LocalDateTime regDt;

	/** 등록자. */
	private Long rgtrSn;

	/** 수정일시. */
	private LocalDateTime mdfcnDt;

	/** 수정자. */
	private Long mdfrSn;

	/** 삭제여부. */
	private String delYn;

	/** 작성자명(조인 — 비밀글 마스킹 시 NULL). 비테이블. */
	private String writerNm;

	/** 활성 답변 수(집계). 비테이블. */
	private Integer answerCnt;
}
