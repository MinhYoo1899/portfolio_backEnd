package egovframework.eeum.house.vo;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 고택 VO — HOUSE 매핑.
 *
 * <p>고택은 판매자(PRTNR_MST) 소유의 숙소 컨테이너 — 직접 예약 대상이 아니라 객실(ROOM)을 담는다.
 * 소유자({@code prtnrSn}, 불변)와 행위자({@code rgtrSn}/{@code mdfrSn})는 분리한다.
 * {@code prtnrConm}(상호 조인)·{@code roomCnt}(활성 객실 수)는 비테이블 필드.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HouseVO {

	/** 고택일련번호(PK). */
	private Long houseSn;

	/** 소유파트너(판매자)일련번호 — 소유권 판정 기준(불변). */
	private Long prtnrSn;

	/** 고택명. */
	private String houseNm;

	/** 고택상태(DRAFT/ACTIVE/HIDDEN/SUSPENDED). */
	private String houseSttsCd;

	/** 우편번호(5자리). */
	private String zip;

	/** 주소. */
	private String addr;

	/** 상세주소. */
	private String daddr;

	/** 고택설명(소개). */
	private String houseDc;

	/** 시도지역코드(REGION 참조, 선택). */
	private String sidoRegionCd;

	/** 시군구지역코드(REGION 참조, 선택). */
	private String sigunguRegionCd;

	/** 성인(19+) 1인당 추가금(KRW). */
	private Integer adultAddChrgAmt;

	/** 아동(8~18) 1인당 추가금(KRW). */
	private Integer childAddChrgAmt;

	/** 영유아(<8) 1인당 추가금(KRW). */
	private Integer infantAddChrgAmt;

	/** 주중(일~목) 대표요금(KRW, 선택 — House 단위 참고요금, 객실 판매가와 별도). */
	private Integer weekdayRateAmt;

	/** 금요일 대표요금(KRW, 선택). */
	private Integer fridayRateAmt;

	/** 주말(토) 대표요금(KRW, 선택). */
	private Integer saturdayRateAmt;

	/** 성수기·공휴일 대표요금(KRW, 선택). */
	private Integer peakRateAmt;

	/** 매주 휴관 요일(MON~SUN, 선택 1개 — 정기휴관 메타). */
	private String closedWkdayCd;

	/** 공휴일 휴무 여부(Y/N). */
	private String holidayClosedYn;

	/** 명절 휴무 여부(Y/N). */
	private String lunarHolidayClosedYn;

	/** 체크인 시각(현지 wall-clock, TZ 무관). */
	private LocalTime chkinTime;

	/** 체크아웃 시각(현지 wall-clock). */
	private LocalTime chkoutTime;

	/** 레이트 체크아웃 시각(현지 wall-clock). */
	private LocalTime lateChkoutTime;

	/** 예약확정방식(COMM_CD RSVT_CNFIRM: AUTO/MANUAL). */
	private String rsvtCnfirmCd;

	/** 취소·환불규정(게시 필수). */
	private String cnclRfndPlcyCn;

	/** 대중교통 안내. */
	private String pbtrnspGuideCn;

	/** 예약 공지·주의사항. */
	private String noticeCn;

	/** 등록일시. */
	private LocalDateTime regDt;

	/** 등록자(행위자). */
	private Long rgtrSn;

	/** 수정일시. */
	private LocalDateTime mdfcnDt;

	/** 수정자(행위자). */
	private Long mdfrSn;

	/** 삭제여부. */
	private String delYn;

	/** 상호(PRTNR_MST 조인). 비테이블. */
	private String prtnrConm;

	/** 활성 객실 수(집계). 비테이블. */
	private Integer roomCnt;

	/** 대표이미지 첨부파일SN(HOUSE_IMAGE RPRS_YN='Y'). 없으면 null. 비테이블. */
	private Long rprsAtchFileSn;

	/** 생성자명(RGTR_SN → USER_ACNT.USER_NM 조인). 비테이블. */
	private String creatorNm;

	/** 활성 결제수단 코드 목록(HOUSE_PAY_MTHD DEL_YN='N', SORT_SEQ 순). 비테이블. */
	private List<String> payMethods;
}
