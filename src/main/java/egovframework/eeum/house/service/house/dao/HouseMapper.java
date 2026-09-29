package egovframework.eeum.house.service.house.dao;

import java.time.LocalTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import egovframework.eeum.house.vo.HouseReviewHstryVO;
import egovframework.eeum.house.vo.HouseVO;

/**
 * 고택 매퍼 — XML: {@code egovframework/mapper/eeum/house/HouseMapper_mysql.xml}
 *
 * <p>소유권은 SQL 에서 강제(변경계 쿼리는 {@code prtnrSn} 조건 — 서비스 의존 외 2중 방어).
 */
@Mapper
public interface HouseMapper {

	/** 고택 생성 — houseSn generated key 수신. */
	void insertHouse(HouseVO house);

	/** 상세(상호 조인 + 활성 객실 수). DEL_YN='N'. 소유권은 서비스가 판정. */
	HouseVO selectHouseDetail(@Param("houseSn") long houseSn);

	/** 소유권/상태 검증용 행 잠금(FOR UPDATE) — prtnrSn/houseSttsCd/delYn. */
	HouseVO selectHouseForUpdate(@Param("houseSn") long houseSn);

	/** 현재 게시상태 코드 — 발행 후 표류 재검증용(미삭제 행만, 없으면 null). */
	String selectHouseSttsCd(@Param("houseSn") long houseSn);

	/** 목록(오프셋, 본인 소유 prtnrSn 범위). */
	List<HouseVO> selectHouseListOffset(@Param("prtnrSn") long prtnrSn, @Param("status") String status,
			@Param("keyword") String keyword, @Param("limit") int limit, @Param("offset") int offset);

	long countHouseList(@Param("prtnrSn") long prtnrSn, @Param("status") String status,
			@Param("keyword") String keyword);

	/** 수정 — 본인 소유 + 미삭제일 때만. */
	int updateHouse(@Param("houseSn") long houseSn, @Param("prtnrSn") long prtnrSn,
			@Param("houseNm") String houseNm, @Param("zip") String zip, @Param("addr") String addr,
			@Param("daddr") String daddr, @Param("houseDc") String houseDc,
			@Param("sidoRegionCd") String sidoRegionCd, @Param("sigunguRegionCd") String sigunguRegionCd,
			@Param("mdfrSn") long mdfrSn);

	/** 상태변경 — 본인 소유 + 미삭제 + SUSPENDED 아님(판매자 자가복구 차단)일 때만. */
	int updateHouseStatus(@Param("houseSn") long houseSn, @Param("prtnrSn") long prtnrSn,
			@Param("houseSttsCd") String houseSttsCd, @Param("mdfrSn") long mdfrSn);

	/**
	 * 검수 상태 전이 — 소스상태(fromSttsCd) + 미삭제 가드(경합 방지, 0=상태불일치). 운영자 전용(전 파트너 대상, 소유권 제한 없음 —
	 * suspend 계열과 동일). 서비스가 selectHouseForUpdate 로 선잠금·소스상태 확인 후 호출.
	 */
	int transitionHouseReviewStatus(@Param("houseSn") long houseSn, @Param("fromSttsCd") String fromSttsCd,
			@Param("toSttsCd") String toSttsCd, @Param("mdfrSn") long mdfrSn);

	/** 검수이력 append(REQUEST/APPROVE/REJECT/REVERT — 반려사유 등 감사/조회 저장). */
	void insertHouseReviewHstry(HouseReviewHstryVO hstry);

	/** 정책 수정(위저드 Step2 전체저장) — 본인 소유 + 미삭제일 때만. 10개 정책컬럼 전체 SET(부분수정 아님). */
	int updateHousePolicy(@Param("houseSn") long houseSn, @Param("prtnrSn") long prtnrSn,
			@Param("adultAddChrgAmt") Integer adultAddChrgAmt, @Param("childAddChrgAmt") Integer childAddChrgAmt,
			@Param("infantAddChrgAmt") Integer infantAddChrgAmt, @Param("chkinTime") LocalTime chkinTime,
			@Param("chkoutTime") LocalTime chkoutTime, @Param("lateChkoutTime") LocalTime lateChkoutTime,
			@Param("rsvtCnfirmCd") String rsvtCnfirmCd,
			@Param("cnclRfndPlcyCn") String cnclRfndPlcyCn, @Param("pbtrnspGuideCn") String pbtrnspGuideCn,
			@Param("noticeCn") String noticeCn, @Param("mdfrSn") long mdfrSn);

	/** 일자별 대표요금 설정(주중/금/토/성수기 4구간 전체 SET) — 본인 소유 + 미삭제일 때만. */
	int updateHouseDailyRate(@Param("houseSn") long houseSn, @Param("prtnrSn") long prtnrSn,
			@Param("weekdayRateAmt") Integer weekdayRateAmt, @Param("fridayRateAmt") Integer fridayRateAmt,
			@Param("saturdayRateAmt") Integer saturdayRateAmt, @Param("peakRateAmt") Integer peakRateAmt,
			@Param("mdfrSn") long mdfrSn);

	/** 정기휴관 설정(주간휴관요일·공휴일·명절 휴무 전체 SET) — 본인 소유 + 미삭제일 때만. */
	int updateHouseRegularClosure(@Param("houseSn") long houseSn, @Param("prtnrSn") long prtnrSn,
			@Param("closedWkdayCd") String closedWkdayCd, @Param("holidayClosedYn") String holidayClosedYn,
			@Param("lunarHolidayClosedYn") String lunarHolidayClosedYn, @Param("mdfrSn") long mdfrSn);

	/** 삭제(soft) — 본인 소유 + 미삭제일 때만. */
	int softDeleteHouse(@Param("houseSn") long houseSn, @Param("prtnrSn") long prtnrSn,
			@Param("mdfrSn") long mdfrSn);

	// ---- 운영자(HOUSE_VIEW/HOUSE_MODER) — 전 판매자 대상, 소유권 필터 없음 ----

	/** 운영자 목록(전 판매자, 상호 조인). 필터 status/keyword(고택명·상호)/prtnrSn/sidoRegionCd/sigunguRegionCd. */
	List<HouseVO> selectHouseListForOperator(@Param("status") String status, @Param("keyword") String keyword,
			@Param("prtnrSn") Long prtnrSn, @Param("sidoRegionCd") String sidoRegionCd,
			@Param("sigunguRegionCd") String sigunguRegionCd, @Param("limit") int limit, @Param("offset") int offset);

	long countHouseListForOperator(@Param("status") String status, @Param("keyword") String keyword,
			@Param("prtnrSn") Long prtnrSn, @Param("sidoRegionCd") String sidoRegionCd,
			@Param("sigunguRegionCd") String sigunguRegionCd);

	/** 비삭제 고택 중 시도지역코드 미설정(NULL) 건수 — filter-options 의 regionUnmappedCount(미매핑 데이터 개수, UX 안내용). 지역필터 기능 활성 여부와 무관. */
	long countHousesWithoutRegion();

	/** 강제 게시중지(임시조치) — 미삭제 + SUSPENDED 아님일 때만 SUSPENDED 로 전이. */
	int forceSuspendHouse(@Param("houseSn") long houseSn, @Param("mdfrSn") long mdfrSn);

	/** 임시조치 해제 — SUSPENDED→HIDDEN(미삭제 + 현재 SUSPENDED일 때만). */
	int releaseHouse(@Param("houseSn") long houseSn, @Param("mdfrSn") long mdfrSn);
}
