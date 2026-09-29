package egovframework.eeum.house.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 상세 drawer 객실 편의시설 VO(roomSn IN 배치) — ROOM_AMENITY→AMENITY_MST + 분류명(COMM_CD AMENITY_CTGRY).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DrawerRoomAmenityVO {

	private Long roomSn;
	private Long amenitySn;
	private String amenityNm;
	private String amenityCtgryCd;
	private String amenityCtgryNm;
}
