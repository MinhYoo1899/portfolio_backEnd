package egovframework.eeum.house.web.dto;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import egovframework.eeum.house.vo.PublicHouseDetailVO;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 손님 공개 고택 상세 응답 — 비로그인 공개(ACTIVE 고택만, 그 외 404 은닉).
 *
 * <p>노출 금지(공개 계약): prtnrSn·houseSttsCd·zip·daddr·감사컬럼(regDt/mdfcnDt)·담당자 연락처.
 * 주소는 기본주소(addr)까지, 판매자 표시정보는 {@link PublicSellerInfo} 4필드만(사용자 결정).
 */
@Schema(description = "공개 고택 상세")
public record PublicHouseDetailResponse(

		@Schema(description = "고택일련번호") Long houseSn,
		@Schema(description = "고택명") String houseNm,
		@Schema(description = "시도명") String sidoNm,
		@Schema(description = "시군구명") String sigunguNm,
		@Schema(description = "지역 표시 텍스트(시도 시군구)") String regionText,
		@Schema(description = "주소(기본주소까지 — 상세주소 미제공)") String addr,
		@Schema(description = "고택설명/소개") String houseDc,
		@Schema(description = "체크인 시각(HH:mm)") String chkinTime,
		@Schema(description = "체크아웃 시각(HH:mm)") String chkoutTime,
		@Schema(description = "레이트 체크아웃 시각(HH:mm, 없으면 null)") String lateChkoutTime,
		@Schema(description = "성인(19+) 1인당 추가금(KRW)") Integer adultAddChrgAmt,
		@Schema(description = "아동(8~18) 1인당 추가금(KRW)") Integer childAddChrgAmt,
		@Schema(description = "영유아(<8) 1인당 추가금(KRW)") Integer infantAddChrgAmt,
		@Schema(description = "예약확정방식(AUTO=즉시확정/MANUAL=판매자승인)") String rsvtCnfirmCd,
		@Schema(description = "취소·환불규정") String cnclRfndPlcyCn,
		@Schema(description = "대중교통 안내") String pbtrnspGuideCn,
		@Schema(description = "예약 공지/주의사항") String noticeCn,
		@Schema(description = "활성 결제수단 코드 목록(SORT_SEQ 순)") List<String> payMethods,
		@Schema(description = "이미지 목록(정렬순, 대표 포함)") List<PublicHouseImageItem> images,
		@Schema(description = "편의시설 목록(분류·정렬순)") List<AmenityResponse> amenities,
		@Schema(description = "객실 요약 목록(ACTIVE 만)") List<PublicRoomSummary> rooms,
		@Schema(description = "판매자 표시정보") PublicSellerInfo seller) {

	private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

	public static PublicHouseDetailResponse from(PublicHouseDetailVO vo, List<PublicHouseImageItem> images,
			List<AmenityResponse> amenities, List<String> payMethods, List<PublicRoomSummary> rooms) {
		return new PublicHouseDetailResponse(
				vo.getHouseSn(),
				vo.getHouseNm(),
				vo.getSidoNm(),
				vo.getSigunguNm(),
				joinRegion(vo.getSidoNm(), vo.getSigunguNm()),
				vo.getAddr(),
				vo.getHouseDc(),
				formatTime(vo.getChkinTime()),
				formatTime(vo.getChkoutTime()),
				formatTime(vo.getLateChkoutTime()),
				vo.getAdultAddChrgAmt(),
				vo.getChildAddChrgAmt(),
				vo.getInfantAddChrgAmt(),
				vo.getRsvtCnfirmCd(),
				vo.getCnclRfndPlcyCn(),
				vo.getPbtrnspGuideCn(),
				vo.getNoticeCn(),
				payMethods == null ? List.of() : payMethods,
				images == null ? List.of() : images,
				amenities == null ? List.of() : amenities,
				rooms == null ? List.of() : rooms,
				PublicSellerInfo.from(vo));
	}

	/** LocalTime → "HH:mm"(초 제거). null 이면 null. ({@link HouseDetailResponse} 와 동일 표기) */
	private static String formatTime(LocalTime t) {
		return t == null ? null : t.format(HH_MM);
	}

	/** 지역 표시 텍스트 — "시도 시군구"(둘 다 없으면 null). ({@link PublicHouseListItem} 과 동일 규칙) */
	private static String joinRegion(String sido, String sigungu) {
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
