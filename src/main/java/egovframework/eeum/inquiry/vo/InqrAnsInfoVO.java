package egovframework.eeum.inquiry.vo;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 문의답변 VO — INQR_ANS_INFO 매핑. {@code ansrNm}(답변자명 조인)은 비테이블.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InqrAnsInfoVO {

	/** 답변일련번호(PK). */
	private Long ansSn;

	/** 문의일련번호. */
	private Long inqrSn;

	/** 답변자(운영자 사용자일련번호). */
	private Long ansUserSn;

	/** 답변내용. */
	private String ansCn;

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

	/** 답변자명(조인). 비테이블. */
	private String ansrNm;
}
