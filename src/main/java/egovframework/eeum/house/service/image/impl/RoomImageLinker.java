package egovframework.eeum.house.service.image.impl;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import egovframework.eeum.base.exception.BusinessException;
import egovframework.eeum.house.exception.HouseErrorCode;
import egovframework.eeum.house.exception.RoomErrorCode;
import egovframework.eeum.house.service.house.dao.HouseMapper;
import egovframework.eeum.house.service.image.dao.RoomImageMapper;
import egovframework.eeum.house.service.room.dao.RoomMapper;
import egovframework.eeum.house.vo.HouseVO;
import egovframework.eeum.house.vo.RoomImageVO;
import egovframework.eeum.house.vo.RoomVO;
import lombok.RequiredArgsConstructor;

/**
 * 객실 이미지 연결 트랜잭션 — 업로드(트랜잭션 밖) 후 ROOM_IMAGE 행 삽입.
 *
 * <p>락 순서 HOUSE→ROOM 통일: houseSn 선확보 → 부모 고택 FOR UPDATE(소유·활성) → 객실 FOR UPDATE(활성) → 삽입.
 */
@Component
@RequiredArgsConstructor
public class RoomImageLinker {

	private static final String Y = "Y";

	private final RoomImageMapper roomImageMapper;
	private final RoomMapper roomMapper;
	private final HouseMapper houseMapper;

	@Transactional
	public RoomImageVO link(long roomSn, long prtnrSn, long atchFileSn, long actorUserSn) {
		Long houseSn = roomMapper.selectHouseSnByRoom(roomSn);
		if (houseSn == null) {
			throw new BusinessException(RoomErrorCode.ROOM_NOT_FOUND);
		}
		HouseVO house = houseMapper.selectHouseForUpdate(houseSn);
		if (house == null || Y.equals(house.getDelYn()) || !house.getPrtnrSn().equals(prtnrSn)) {
			throw new BusinessException(HouseErrorCode.HOUSE_NOT_FOUND);
		}
		RoomVO room = roomMapper.selectRoomForUpdate(roomSn);
		if (room == null || Y.equals(room.getDelYn())) {
			throw new BusinessException(RoomErrorCode.ROOM_NOT_FOUND);
		}
		int sortSeq = roomImageMapper.maxSortSeqByRoom(roomSn) + 1;
		String rprsYn = roomImageMapper.countActiveImagesByRoom(roomSn) == 0 ? "Y" : "N";
		RoomImageVO vo = RoomImageVO.builder()
				.roomSn(roomSn)
				.atchFileSn(atchFileSn)
				.sortSeq(sortSeq)
				.rprsYn(rprsYn)
				.rgtrSn(actorUserSn)
				.build();
		roomImageMapper.insertRoomImage(vo);
		return vo;
	}
}
