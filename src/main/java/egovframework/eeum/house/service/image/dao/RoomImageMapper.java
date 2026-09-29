package egovframework.eeum.house.service.image.dao;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import egovframework.eeum.house.vo.RoomImageVO;

/**
 * 객실 이미지 매퍼 — XML: {@code egovframework/mapper/eeum/house/RoomImageMapper_mysql.xml} (HOUSE_IMAGE 와 동일 패턴, 부모 ROOM).
 */
@Mapper
public interface RoomImageMapper {

	void insertRoomImage(RoomImageVO image);

	List<RoomImageVO> selectActiveImagesByRoom(@Param("roomSn") long roomSn);

	RoomImageVO selectRoomImageForUpdate(@Param("roomImgSn") long roomImgSn);

	int maxSortSeqByRoom(@Param("roomSn") long roomSn);

	int countActiveImagesByRoom(@Param("roomSn") long roomSn);

	Long selectFirstActiveImageSnByRoom(@Param("roomSn") long roomSn);

	int softDeleteRoomImage(@Param("roomImgSn") long roomImgSn, @Param("roomSn") long roomSn,
			@Param("mdfrSn") long mdfrSn);

	int clearRepresentativeByRoom(@Param("roomSn") long roomSn, @Param("mdfrSn") long mdfrSn);

	int setRepresentative(@Param("roomImgSn") long roomImgSn, @Param("roomSn") long roomSn,
			@Param("mdfrSn") long mdfrSn);
}
