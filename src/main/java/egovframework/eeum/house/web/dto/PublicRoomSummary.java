package egovframework.eeum.house.web.dto;

import java.math.BigDecimal;

import egovframework.eeum.house.vo.PublicRoomSummaryVO;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 손님 공개 객실 요약 — 고택 상세 하위 목록용(ACTIVE 객실만).
 *
 * <p>가격/재고는 가용검색 API 가 담당(날짜 없이는 미제공). 상태코드·내부 재고설정은 노출하지 않는다.
 */
@Schema(description = "공개 객실 요약")
public record PublicRoomSummary(

		@Schema(description = "객실일련번호") Long roomSn,
		@Schema(description = "객실명") String roomNm,
		@Schema(description = "기준인원") Integer bscPrsnCnt,
		@Schema(description = "최대인원") Integer maxPrsnCnt,
		@Schema(description = "최소숙박박수") Integer minStayNgtCnt,
		@Schema(description = "객실설명") String roomDc,
		@Schema(description = "면적(m²)") BigDecimal areaSqm,
		@Schema(description = "방 개수") Integer roomCnt,
		@Schema(description = "화장실 개수") Integer bathrmCnt,
		@Schema(description = "객실 대표이미지 다운로드 URL(없으면 null)") String repImgUrl) {

	public static PublicRoomSummary from(PublicRoomSummaryVO vo) {
		return new PublicRoomSummary(
				vo.getRoomSn(),
				vo.getRoomNm(),
				vo.getBscPrsnCnt(),
				vo.getMaxPrsnCnt(),
				vo.getMinStayNgtCnt(),
				vo.getRoomDc(),
				vo.getAreaSqm(),
				vo.getRoomCnt(),
				vo.getBathrmCnt(),
				vo.getRprsAtchFileSn() == null ? null : "/files/download?atchFileSn=" + vo.getRprsAtchFileSn());
	}
}
