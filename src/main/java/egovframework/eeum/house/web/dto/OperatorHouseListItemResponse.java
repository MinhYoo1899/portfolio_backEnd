package egovframework.eeum.house.web.dto;

import java.time.Instant;
import java.time.ZoneOffset;

import egovframework.eeum.house.vo.HouseVO;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 운영자 고택 목록 항목 — 판매자 정보 포함.
 */
@Schema(description = "운영자 고택 목록 항목")
public record OperatorHouseListItemResponse(

		@Schema(description = "고택일련번호") Long houseSn,
		@Schema(description = "소유파트너일련번호") Long prtnrSn,
		@Schema(description = "상호(판매자명)") String prtnrConm,
		@Schema(description = "고택명") String houseNm,
		@Schema(description = "고택상태(DRAFT/ACTIVE/HIDDEN/SUSPENDED)") String houseSttsCd,
		@Schema(description = "주소") String addr,
		@Schema(description = "활성 객실 수") Integer roomCnt,
		@Schema(description = "등록일시(UTC, ISO-8601 Z)") Instant regDt,
		@Schema(description = "수정일시(UTC, ISO-8601 Z, 없으면 null)") Instant mdfcnDt,
		@Schema(description = "생성자명(없으면 null)") String creator,
		@Schema(description = "대표이미지 첨부파일SN(없으면 null)") Long repImgFileSn,
		@Schema(description = "대표이미지 다운로드 URL(백엔드 경유, 없으면 null)") String repImgUrl) {

	public static OperatorHouseListItemResponse from(HouseVO vo) {
		Long repImgFileSn = vo.getRprsAtchFileSn();
		String repImgUrl = repImgFileSn == null ? null : "/files/download?atchFileSn=" + repImgFileSn;
		return new OperatorHouseListItemResponse(
				vo.getHouseSn(),
				vo.getPrtnrSn(),
				vo.getPrtnrConm(),
				vo.getHouseNm(),
				vo.getHouseSttsCd(),
				vo.getAddr(),
				vo.getRoomCnt() == null ? 0 : vo.getRoomCnt(),
				vo.getRegDt() == null ? null : vo.getRegDt().toInstant(ZoneOffset.UTC),
				vo.getMdfcnDt() == null ? null : vo.getMdfcnDt().toInstant(ZoneOffset.UTC),
				vo.getCreatorNm(),
				repImgFileSn,
				repImgUrl);
	}
}
