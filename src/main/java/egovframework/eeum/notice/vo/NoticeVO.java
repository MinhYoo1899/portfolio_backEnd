package egovframework.eeum.notice.vo;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

/**
 * 공지사항 VO — NOTICE 매핑. 상태(NOTICE_STTS_CD)는 저장 라이프사이클(DRAFT/SCHEDULED/PUBLISHED_ONGOING/CLOSED).
 */
@Getter
@Setter
public class NoticeVO {

	/** 공지일련번호(PK). */
	private Long noticeSn;

	/** 제목. */
	private String title;

	/** 내용. */
	private String content;

	/** 대상(ALL/ALL_PARTNER/MEMBER/SPECIFIC_PARTNER). */
	private String targetTypeCd;

	/** 발송시점(IMMEDIATE/SCHEDULED). */
	private String sendTimingCd;

	/** 예약 발송 일시. */
	private LocalDateTime scheduledAt;

	/** 발행 일시. */
	private LocalDateTime publishedAt;

	/** 상태(DRAFT/SCHEDULED/PUBLISHED_ONGOING/CLOSED). */
	private String noticeSttsCd;

	/** 알림 발송 여부(Y/N). */
	private String notifySendYn;

	/** 등록일시. */
	private LocalDateTime regDt;

	/** 등록자. */
	private Long rgtrSn;

	/** 수정일시. */
	private LocalDateTime mdfcnDt;

	/** 수정자. */
	private Long mdfrSn;
}
