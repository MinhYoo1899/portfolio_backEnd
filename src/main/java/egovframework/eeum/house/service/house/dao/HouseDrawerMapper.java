package egovframework.eeum.house.service.house.dao;

import java.time.LocalDate;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import egovframework.eeum.house.vo.DrawerPriceBucketVO;
import egovframework.eeum.house.vo.DrawerRoomAmenityVO;
import egovframework.eeum.house.vo.DrawerRoomBeddingVO;
import egovframework.eeum.house.vo.DrawerRoomImageVO;
import egovframework.eeum.house.vo.DrawerRoomVO;
import egovframework.eeum.house.vo.HouseDrawerVO;

/**
 * 운영자 상세 drawer 조회 매퍼 — XML: {@code egovframework/mapper/eeum/house/HouseDrawerMapper_mysql.xml}
 *
 * <p>모두 read 전용. 객실 하위(침구/편의시설/이미지)는 {@code roomSn IN} 배치로 조회해 N+1 을 회피한다
 * (객실 수와 무관하게 매퍼 호출 수 고정). 요금/휴관은 하우스 단위 집계 쿼리 1회씩.
 */
@Mapper
public interface HouseDrawerMapper {

	/** 헤더(HOUSE + 소유 파트너 조인) 1행 — 미존재/삭제면 null. */
	HouseDrawerVO selectDrawerHouse(@Param("houseSn") long houseSn);

	/** 미삭제 등록 객실 전체(상태 무관 — 운영자 검수/조회 표면) + 대표요금(창 내 sellable 최저가, 상관 서브쿼리). */
	List<DrawerRoomVO> selectDrawerRooms(@Param("houseSn") long houseSn,
			@Param("from") LocalDate from, @Param("to") LocalDate to);

	/** 침구 배치(roomSn IN) — COMM_CD BEDDING_TYPE 표시명 조인. */
	List<DrawerRoomBeddingVO> selectDrawerBeddings(@Param("roomSns") List<Long> roomSns);

	/** 편의시설 배치(roomSn IN) — AMENITY_MST + COMM_CD AMENITY_CTGRY 분류명 조인. */
	List<DrawerRoomAmenityVO> selectDrawerAmenities(@Param("roomSns") List<Long> roomSns);

	/** 이미지 배치(roomSn IN) — 활성 전체(대표 포함), 정렬순서. */
	List<DrawerRoomImageVO> selectDrawerImages(@Param("roomSns") List<Long> roomSns);

	/** 요일 요금 버킷(하우스 단위) — sellable 밤 MIN/MAX. */
	List<DrawerPriceBucketVO> selectDrawerPriceBuckets(@Param("houseSn") long houseSn,
			@Param("from") LocalDate from, @Param("to") LocalDate to);

	/**
	 * 하우스 휴관일 — 창 내 캘린더 행 보유 미삭제 객실(=calendarRoomCount) 전부가 그 날짜에 CLOSED_YN='Y' 인 날만(행 누락일 제외).
	 * 분모는 {@link #countDrawerCalendarRooms}로 계산(캘린더 없는 DRAFT 객실이 기존 휴관일 표시를 삼키지 않도록).
	 */
	List<LocalDate> selectDrawerHouseClosedDates(@Param("houseSn") long houseSn,
			@Param("from") LocalDate from, @Param("to") LocalDate to,
			@Param("calendarRoomCount") int calendarRoomCount);

	/** 창 내 캘린더 행이 존재하는 미삭제 객실 수(휴관일 분모, 상태 무관·CLOSED 여부 무관). */
	int countDrawerCalendarRooms(@Param("houseSn") long houseSn,
			@Param("from") LocalDate from, @Param("to") LocalDate to);
}
