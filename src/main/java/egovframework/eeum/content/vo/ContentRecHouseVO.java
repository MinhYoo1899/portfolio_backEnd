package egovframework.eeum.content.vo;

import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

/**
 * 콘텐츠 추천고택 VO — CONTENT_REC_HOUSE 매핑(mybatis map-underscore-to-camel).
 *
 * <p>{@code status}(EXPOSED/HIDDEN/ENDED)는 저장 컬럼이 아니라 서비스가 산출(배너와 동일). {@code houseNm}/{@code houseAddr}는 HOUSE 조인(비테이블).
 */
@Getter
@Setter
public class ContentRecHouseVO {

	/** 추천고택일련번호(PK). */
	private Long contentRecHouseSn;

	/** 추천분류(REC_HOUSE_CATEGORY — TODAY_DEAL/BEST/MD_PICK). */
	private String recCategoryCd;

	/** 대상 고택일련번호. */
	private Long houseSn;

	/** 분류 내 표시순서(드래그 정렬 저장). */
	private int sortOrder;

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

	/** 고택명(HOUSE 조인, 비테이블). */
	private String houseNm;

	/** 고택 주소(HOUSE 조인, 지역 표시용, 비테이블). */
	private String houseAddr;
}
