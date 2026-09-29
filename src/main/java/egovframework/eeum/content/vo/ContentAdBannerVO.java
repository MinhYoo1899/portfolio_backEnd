package egovframework.eeum.content.vo;

import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

/**
 * 콘텐츠 광고배너 VO — CONTENT_AD_BANNER 매핑(mybatis map-underscore-to-camel).
 *
 * <p>{@code status}(EXPOSED/HIDDEN/ENDED)는 저장 컬럼이 아니라 서비스가 (EXPOSE_YN + 노출기간)으로 산출한다
 * (메인배너와 동일 규칙 — {@code ContentBannerStatus} 재사용).
 */
@Getter
@Setter
public class ContentAdBannerVO {

	/** 광고배너일련번호(PK). */
	private Long contentAdBannerSn;

	/** 제목. */
	private String title;

	/** 부제. */
	private String subCopy;

	/** 광고이미지 URL(선택 — 자체 파일인프라 반환 경로). */
	private String imgUrl;

	/** 노출위치코드(AD_BANNER_POSITION — MAIN/SEARCH/RECOMMENDED_HOUSE). */
	private String positionCd;

	/** 연결대상코드(CONTENT_LINK_TARGET — PRODUCT/URL/PROMOTION, null=연결없음). */
	private String linkTargetCd;

	/** 연결대상값(PRODUCT=houseId / URL=url / PROMOTION=promotionId). */
	private String linkTargetVal;

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
