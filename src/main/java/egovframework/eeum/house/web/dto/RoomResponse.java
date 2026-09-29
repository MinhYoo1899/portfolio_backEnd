package egovframework.eeum.house.web.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import egovframework.eeum.house.vo.RoomVO;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 객실 응답(목록 항목 겸 상세).
 */
@Schema(description = "객실")
public record RoomResponse(

		@Schema(description = "객실일련번호") Long roomSn,
		@Schema(description = "고택일련번호") Long houseSn,
		@Schema(description = "객실명") String roomNm,
		@Schema(description = "객실상태(DRAFT/ACTIVE/HIDDEN/SUSPENDED)") String roomSttsCd,
		@Schema(description = "기준인원") Integer bscPrsnCnt,
		@Schema(description = "최대인원") Integer maxPrsnCnt,
		@Schema(description = "기본재고수량") Integer dfltStockQty,
		@Schema(description = "최소숙박박수") Integer minStayNgtCnt,
		@Schema(description = "객실설명") String roomDc,
		@Schema(description = "면적(m²)") BigDecimal areaSqm,
		@Schema(description = "방 개수") Integer roomCnt,
		@Schema(description = "화장실 개수") Integer bathrmCnt,
		@Schema(description = "침구 목록") List<RoomBeddingResponse> beddings,
		@Schema(description = "편의시설 목록(상세에서만 채움)") List<AmenityResponse> amenities,
		@Schema(description = "추가옵션 목록(상세에서만 채움)") List<RoomOptionResponse> options,
			@Schema(description = "냉방 코드 목록(COMM_CD COOLING_TYPE, 상세에서만 채움)") List<String> coolingCds,
			@Schema(description = "난방 코드 목록(COMM_CD HEATING_TYPE, 상세에서만 채움)") List<String> heatingCds,
		@Schema(description = "등록일시(UTC, ISO-8601 Z)") Instant regDt,
		@Schema(description = "수정일시(UTC, ISO-8601 Z)") Instant mdfcnDt) {

	public static RoomResponse from(RoomVO vo) {
		return from(vo, List.of());
	}

	/** 목록용 — 편의시설/추가옵션은 N+1 회피 위해 비움(상세에서만 채움). */
	public static RoomResponse from(RoomVO vo, List<RoomBeddingResponse> beddings) {
		return from(vo, beddings, List.of(), List.of(), List.of(), List.of());
	}

	/** 상세용 — 침구/편의시설/추가옵션 전부 채움. */
	public static RoomResponse from(RoomVO vo, List<RoomBeddingResponse> beddings,
			List<AmenityResponse> amenities, List<RoomOptionResponse> options,
			List<String> coolingCds, List<String> heatingCds) {
		return new RoomResponse(
				vo.getRoomSn(),
				vo.getHouseSn(),
				vo.getRoomNm(),
				vo.getRoomSttsCd(),
				vo.getBscPrsnCnt(),
				vo.getMaxPrsnCnt(),
				vo.getDfltStockQty(),
				vo.getMinStayNgtCnt(),
				vo.getRoomDc(),
				vo.getAreaSqm(),
				vo.getRoomCnt(),
				vo.getBathrmCnt(),
				beddings == null ? List.of() : beddings,
				amenities == null ? List.of() : amenities,
				options == null ? List.of() : options,
				coolingCds == null ? List.of() : coolingCds,
				heatingCds == null ? List.of() : heatingCds,
				vo.getRegDt() == null ? null : vo.getRegDt().toInstant(ZoneOffset.UTC),
				vo.getMdfcnDt() == null ? null : vo.getMdfcnDt().toInstant(ZoneOffset.UTC));
	}
}
