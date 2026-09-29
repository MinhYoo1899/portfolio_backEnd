package egovframework.eeum.content.vo;

import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

/**
 * 콘텐츠 지역별 즐길거리 VO — CONTENT_REGION 매핑(mybatis map-underscore-to-camel).
 *
 * <p>{@code status}는 저장 컬럼이 아니라 서비스가 산출(배너와 동일). {@code viewCnt}/{@code cmntCnt}는 읽기전용(등록/수정 대상 아님).
 */
@Getter
@Setter
public class ContentRegionVO {

	/** 즐길거리일련번호(PK). */
	private Long contentRegionSn;

	/** 카테고리(REGION_CONTENT_CATEGORY — EDITOR_PICK/TRENDING/REC_COURSE). */
	private String categoryCd;

	/** 유형(REGION_CONTENT_TYPE — DAY_COURSE/TOUR). */
	private String typeCd;

	/** 지역(시도, REGION 참조). */
	private String sidoRegionCd;

	/** 대표이미지 URL(자체 파일인프라 반환 경로). */
	private String thumbnailUrl;

	/** 제목. */
	private String title;

	/** 경로 요약(카드 표시). */
	private String routeSummary;

	/** 본문(리치텍스트 HTML). */
	private String bodyCn;

	/** 조회수(읽기전용). */
	private Integer viewCnt;

	/** 댓글수(읽기전용). */
	private Integer cmntCnt;

	/** 노출시작일(KST). */
	private LocalDate exposeBgnDate;

	/** 노출종료일(KST). */
	private LocalDate exposeEndDate;

	/** 노출여부(Y/N — isActive 토글). */
	private String exposeYn;

	/** 등록일시. */
	private LocalDateTime regDt;

	/** 등록자(사용자일련번호). */
	private Long rgtrSn;

	/** 수정일시. */
	private LocalDateTime mdfcnDt;

	/** 수정자(사용자일련번호). */
	private Long mdfrSn;
}
