package egovframework.eeum.house.web.dto;

import java.time.Instant;
import java.time.ZoneOffset;

import egovframework.eeum.house.vo.HouseVO;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 고택 목록 항목 응답.
 */
@Schema(description = "고택 목록 항목")
public record HouseListItemResponse(

		@Schema(description = "고택일련번호") Long houseSn,
		@Schema(description = "고택명") String houseNm,
		@Schema(description = "고택상태(DRAFT/ACTIVE/HIDDEN/SUSPENDED)") String houseSttsCd,
		@Schema(description = "주소") String addr,
		@Schema(description = "활성 객실 수") Integer roomCnt,
		@Schema(description = "등록일시(UTC, ISO-8601 Z)") Instant regDt) {

	public static HouseListItemResponse from(HouseVO vo) {
		return new HouseListItemResponse(
				vo.getHouseSn(),
				vo.getHouseNm(),
				vo.getHouseSttsCd(),
				vo.getAddr(),
				vo.getRoomCnt() == null ? 0 : vo.getRoomCnt(),
				vo.getRegDt() == null ? null : vo.getRegDt().toInstant(ZoneOffset.UTC));
	}
}
