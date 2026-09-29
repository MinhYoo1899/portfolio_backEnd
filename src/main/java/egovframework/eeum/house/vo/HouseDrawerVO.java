package egovframework.eeum.house.vo;

import java.time.LocalTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 상세 drawer 헤더 VO — HOUSE + 소유 파트너(PRTNR_MST) 조인 1행.
 *
 * <p>운영자 조회(HOUSE_VIEW) 전용. 판매자 정보는 상호/대표/사업자번호/통신판매신고번호/사업장주소만(연락처 PII 제외).
 * 대표이미지는 {@code repImgFileSn}(첨부일련번호)으로 받아 응답에서 다운로드 URL 로 조립.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HouseDrawerVO {

	private Long houseSn;
	private String houseNm;
	private String prtnrConm;
	private String addr;
	private String houseSttsCd;
	private String houseDc;
	private LocalTime chkinTime;
	private LocalTime chkoutTime;
	private String noticeCn;
	private String cnclRfndPlcyCn;
	/** 교통안내(대중교통) — 미리보기 '위치·교통' 표출용. detail 과 동일 필드(drawer 누락 보정). */
	private String pbtrnspGuideCn;
	private Long repImgFileSn;

	// ---- 판매자 정보(연락처 제외) ----
	private String rprsvNm;
	private String brno;
	private String mailOrderSalesNo;
	/** 사업장주소(PRTNR_MST.ADDR + DADDR 결합). */
	private String bizAddr;
}
