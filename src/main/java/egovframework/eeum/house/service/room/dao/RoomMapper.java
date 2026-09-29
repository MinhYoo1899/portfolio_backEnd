package egovframework.eeum.house.service.room.dao;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import egovframework.eeum.house.vo.RoomVO;

/**
 * 객실 매퍼 — XML: {@code egovframework/mapper/eeum/house/RoomMapper_mysql.xml}
 *
 * <p>소유권은 부모 고택(HOUSE.PRTNR_SN)으로 판정. 변경계 쿼리는 HOUSE 조인 + {@code prtnrSn} 조건으로 SQL 2중 방어.
 */
@Mapper
public interface RoomMapper {

	/** 객실 생성 — roomSn generated key 수신. */
	void insertRoom(RoomVO room);

	/** 상세(부모 고택 소유자 조인). 객실/고택 DEL_YN='N'. 소유권은 서비스가 판정. */
	RoomVO selectRoomDetail(@Param("roomSn") long roomSn);

	/** 활성 객실의 부모 고택일련번호(미잠금, 미존재 시 null) — 락 순서 HOUSE→ROOM 위해 houseSn 선확보용. */
	Long selectHouseSnByRoom(@Param("roomSn") long roomSn);

	/** 상태 검증용 행 잠금(ROOM 단일 FOR UPDATE) — 소유권은 부모 고택 선잠금으로 검증(데드락 방지). */
	RoomVO selectRoomForUpdate(@Param("roomSn") long roomSn);

	/** 고택별 객실 목록(최신순). */
	List<RoomVO> selectRoomsByHouse(@Param("houseSn") long houseSn);

	/** 수정 — 부모 고택이 본인 소유 + 활성 + 객실 미삭제일 때만. */
	int updateRoom(@Param("roomSn") long roomSn, @Param("prtnrSn") long prtnrSn,
			@Param("roomNm") String roomNm, @Param("bscPrsnCnt") int bscPrsnCnt,
			@Param("maxPrsnCnt") int maxPrsnCnt, @Param("dfltStockQty") int dfltStockQty,
			@Param("minStayNgtCnt") int minStayNgtCnt, @Param("roomDc") String roomDc,
			@Param("areaSqm") java.math.BigDecimal areaSqm, @Param("roomCnt") Integer roomCnt,
			@Param("bathrmCnt") Integer bathrmCnt, @Param("mdfrSn") long mdfrSn);

	/** 상태변경 — 본인 소유 + 미삭제 + SUSPENDED 아님일 때만. */
	int updateRoomStatus(@Param("roomSn") long roomSn, @Param("prtnrSn") long prtnrSn,
			@Param("roomSttsCd") String roomSttsCd, @Param("mdfrSn") long mdfrSn);

	/** 삭제(soft) — 본인 소유 + 미삭제일 때만. */
	int softDeleteRoom(@Param("roomSn") long roomSn, @Param("prtnrSn") long prtnrSn,
			@Param("mdfrSn") long mdfrSn);

	/** 고택 삭제 동반 — 해당 고택의 활성 객실 전부 soft delete. JOIN HOUSE + prtnrSn 가드(타 소유 방지). */
	int softDeleteRoomsByHouse(@Param("houseSn") long houseSn, @Param("prtnrSn") long prtnrSn,
			@Param("mdfrSn") long mdfrSn);
}
