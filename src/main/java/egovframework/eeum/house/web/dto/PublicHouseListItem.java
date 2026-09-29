package egovframework.eeum.house.web.dto;

import egovframework.eeum.house.vo.PublicHouseListVO;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 손님 공개 고택 목록 항목 — 메인/검색 카드용.
 *
 * <p>{@code priceFrom}은 날짜 미지정 카탈로그 단계라 항상 null(가격은 날짜 조회 슬라이스에서 제공).
 * {@code thumbnailUrl}은 대표이미지 다운로드 경로(없으면 null → 프론트 기본이미지).
 */
@Schema(description = "공개 고택 목록 항목")
public record PublicHouseListItem(

		@Schema(description = "고택일련번호") Long houseSn,
		@Schema(description = "고택명") String houseNm,
		@Schema(description = "시도명") String sidoNm,
		@Schema(description = "시군구명") String sigunguNm,
		@Schema(description = "지역 표시 텍스트(시도 시군구)") String regionText,
		@Schema(description = "대표이미지 다운로드 URL(없으면 null)") String thumbnailUrl,
		@Schema(description = "기준인원(ACTIVE 객실 최소)") Integer bscPrsnCnt,
		@Schema(description = "최대인원(ACTIVE 객실 최대)") Integer maxPrsnCnt,
		@Schema(description = "최저가(날짜 미지정 시 null)") Long priceFrom) {

	public static PublicHouseListItem from(PublicHouseListVO vo) {
		String sido = vo.getSidoNm();
		String sigungu = vo.getSigunguNm();
		String regionText = join(sido, sigungu);
		String thumbnailUrl = vo.getRprsAtchFileSn() == null
				? null
				: "/files/download?atchFileSn=" + vo.getRprsAtchFileSn();
		return new PublicHouseListItem(vo.getHouseSn(), vo.getHouseNm(), sido, sigungu, regionText,
				thumbnailUrl, vo.getBscPrsnCnt(), vo.getMaxPrsnCnt(), null);
	}

	private static String join(String sido, String sigungu) {
		if (sido == null && sigungu == null) {
			return null;
		}
		if (sido == null) {
			return sigungu;
		}
		if (sigungu == null) {
			return sido;
		}
		return sido + " " + sigungu;
	}
}
