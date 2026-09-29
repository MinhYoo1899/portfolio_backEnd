package egovframework.eeum.house.vo;

import java.time.LocalTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 손님 공개 고택 상세 조회 결과(읽기 전용 프로젝션) — 공개 가시성(publicVisibleWhere) 통과 행만.
 *
 * <p>노출 금지 필드는 애초에 담지 않는다: PRTNR_SN·상태코드·ZIP·DADDR·감사컬럼·담당자 연락처.
 * 지역명은 REGION 마스터 조인, 판매자 표시정보(상호/대표자/사업자번호/통신판매번호)는 PRTNR_MST 조인.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublicHouseDetailVO {

	private Long houseSn;
	private String houseNm;
	private String addr;        // 공개 주소는 기본주소까지(상세주소 DADDR 미포함 — 사용자 결정)
	private String houseDc;
	private Integer adultAddChrgAmt;
	private Integer childAddChrgAmt;
	private Integer infantAddChrgAmt;
	private LocalTime chkinTime;
	private LocalTime chkoutTime;
	private LocalTime lateChkoutTime;
	private String rsvtCnfirmCd;    // 예약확정방식(AUTO/MANUAL) — 즉시확정/승인대기 UX 구분용
	private String cnclRfndPlcyCn;
	private String pbtrnspGuideCn;
	private String noticeCn;
	private String sidoNm;          // REGION 조인
	private String sigunguNm;       // REGION 조인
	private String conm;            // 판매자 상호(PRTNR_MST)
	private String rprsvNm;         // 판매자 대표자명
	private String brno;            // 사업자등록번호
	private String mailOrderSalesNo; // 통신판매업신고번호(미입력 파트너는 null)
}
