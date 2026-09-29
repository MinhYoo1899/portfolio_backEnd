package egovframework.eeum.dm.vo;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

/**
 * DM 발송 VO — DM_MESSAGE 매핑. 상태(DM_STTS_CD)는 저장 라이프사이클(SENT/SCHEDULED/DRAFT/FAILED).
 */
@Getter
@Setter
public class DmMessageVO {

	/** DM일련번호(PK). */
	private Long dmMessageSn;

	/** 제목. */
	private String title;

	/** 내용. */
	private String content;

	/** 채널(ALIMTALK/SMS). */
	private String channelCd;

	/** 이미지 URL(선택). */
	private String imgUrl;

	/** 대상(ALL/SELECTED). */
	private String targetTypeCd;

	/** 발송 인원. */
	private Integer targetCnt;

	/** 발송시점(NOW/SCHEDULED). */
	private String sendTimingCd;

	/** 예약 발송 일시. */
	private LocalDateTime scheduledAt;

	/** 발송 완료 일시. */
	private LocalDateTime sentAt;

	/** 상태(SENT/SCHEDULED/DRAFT/FAILED). */
	private String dmSttsCd;

	/** 실패 사유. */
	private String failRsn;

	/** 등록일시. */
	private LocalDateTime regDt;

	/** 등록자. */
	private Long rgtrSn;

	/** 수정일시. */
	private LocalDateTime mdfcnDt;

	/** 수정자. */
	private Long mdfrSn;
}
