package egovframework.eeum.house.vo;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 고택 검수이력 VO — HOUSE_REVIEW_HSTRY 매핑(append-only).
 *
 * <p>검수 상태 전이(REQUEST/APPROVE/REJECT)와 수정에 의한 자동복귀(REVERT)의 감사/조회용.
 * 반려사유(rjctRsn)는 REJECT 시에만 채움. AUDIT_LOG_INFO 엔 payload 컬럼이 없어 별도 이력으로 저장.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HouseReviewHstryVO {

	/** 고택검수이력일련번호(PK). */
	private Long houseReviewHstrySn;

	/** 고택일련번호. */
	private Long houseSn;

	/** 검수행위(REQUEST/APPROVE/REJECT/REVERT). */
	private String rvwActnCd;

	/** 변경전 고택상태. */
	private String bfrSttsCd;

	/** 변경후 고택상태. */
	private String aftSttsCd;

	/** 행위자(사용자일련번호). */
	private Long actrUserSn;

	/** 반려사유(REJECT 시 필수, 그 외 null). */
	private String rjctRsn;

	/** 등록일시. */
	private LocalDateTime regDt;

	/** 이력행 팩토리 — 전이/복귀 기록 생성(REG_DT 는 매퍼가 UTC_TIMESTAMP 로 채움). */
	public static HouseReviewHstryVO of(long houseSn, String rvwActnCd, String bfrSttsCd, String aftSttsCd,
			Long actrUserSn, String rjctRsn) {
		return HouseReviewHstryVO.builder()
				.houseSn(houseSn)
				.rvwActnCd(rvwActnCd)
				.bfrSttsCd(bfrSttsCd)
				.aftSttsCd(aftSttsCd)
				.actrUserSn(actrUserSn)
				.rjctRsn(rjctRsn)
				.build();
	}
}
