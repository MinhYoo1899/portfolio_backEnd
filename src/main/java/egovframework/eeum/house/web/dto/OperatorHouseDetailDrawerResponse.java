package egovframework.eeum.house.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import egovframework.eeum.house.vo.DrawerPriceBucketVO;
import egovframework.eeum.house.vo.DrawerRoomAmenityVO;
import egovframework.eeum.house.vo.DrawerRoomBeddingVO;
import egovframework.eeum.house.vo.DrawerRoomImageVO;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 운영자 상세 drawer 조회 응답(HOUSE_VIEW) — 상품관리 drawer + 객실 모달을 aggregate 1회로 렌더.
 *
 * <p>백엔드 실제값만 제공: 성수기 요금·반복(요일) 휴관은 저장 개념이 없어 항상 미지원(unsupported)으로 신호(fake 금지).
 * 담당자 연락처(암호화 PII)는 seller 에서 제외. priceSummary 는 항상 고정 객체(빈 버킷만 null).
 */
@Schema(description = "운영자 상세 drawer 조회 응답")
public record OperatorHouseDetailDrawerResponse(
		@Schema(description = "고택 헤더") HouseInfo house,
		@Schema(description = "수용 인원 합계") Capacity capacity,
		@Schema(description = "요일별 요금 요약") PriceSummary priceSummary,
		@Schema(description = "객실(모달 데이터 포함)") List<RoomItem> rooms,
		@Schema(description = "하우스 휴관일(특정일만)") List<LocalDate> houseClosedDates,
		@Schema(description = "판매자 정보(연락처 제외)") SellerInfo seller,
		@Schema(description = "미지원 항목 플래그") Unsupported unsupported) {

	/** 첨부 다운로드 URL 접두. */
	public static final String FILE_URL_PREFIX = "/files/download?atchFileSn=";

	@Schema(description = "고택 헤더")
	public record HouseInfo(
			Long houseSn, String houseNm, String prtnrConm, String addr, String houseSttsCd,
			@Schema(description = "대표이미지 URL") String repImgUrl, String houseDc,
			@Schema(description = "체크인(HH:mm)") String chkinTime,
			@Schema(description = "체크아웃(HH:mm)") String chkoutTime,
			String noticeCn, String cnclRfndPlcyCn,
			@Schema(description = "교통안내(대중교통)") String pbtrnspGuideCn) {
	}

	@Schema(description = "수용 인원 합계")
	public record Capacity(int bscPrsnTotal, int maxPrsnTotal) {
	}

	/** 요일 버킷: WEEKDAY=일~목 / FRI=금 / SAT=토. peakSeason 은 저장 개념 없어 항상 null. 빈 버킷도 null. */
	@Schema(description = "요일별 요금 요약")
	public record PriceSummary(PriceBucket weekday, PriceBucket friday, PriceBucket saturday, PriceBucket peakSeason) {
	}

	@Schema(description = "요금 버킷")
	public record PriceBucket(Integer minPrice, Integer maxPrice, boolean variable) {

		public static PriceBucket from(DrawerPriceBucketVO vo) {
			if (vo == null || vo.getMinPrice() == null) {
				return null;
			}
			return new PriceBucket(vo.getMinPrice(), vo.getMaxPrice(), !vo.getMinPrice().equals(vo.getMaxPrice()));
		}
	}

	@Schema(description = "객실")
	public record RoomItem(
			Long roomSn, String roomNm, String roomSttsCd, Integer bscPrsnCnt, Integer maxPrsnCnt,
			String roomDc, BigDecimal areaSqm, Integer roomCnt, Integer bathrmCnt,
			@Schema(description = "대표이미지 URL") String repImgUrl,
			@Schema(description = "대표요금(창 내 최저가, 없으면 null)") Integer repPriceFrom,
			List<Bedding> beddings, List<Amenity> amenities, List<Image> images) {
	}

	@Schema(description = "객실 침구")
	public record Bedding(String beddingTypeCd, String beddingTypeNm, Integer bedCnt) {

		public static Bedding from(DrawerRoomBeddingVO vo) {
			return new Bedding(vo.getBeddingTypeCd(), vo.getBeddingTypeNm(), vo.getBedCnt());
		}
	}

	@Schema(description = "객실 편의시설")
	public record Amenity(Long amenitySn, String amenityNm, String amenityCtgryCd, String amenityCtgryNm) {

		public static Amenity from(DrawerRoomAmenityVO vo) {
			return new Amenity(vo.getAmenitySn(), vo.getAmenityNm(), vo.getAmenityCtgryCd(), vo.getAmenityCtgryNm());
		}
	}

	@Schema(description = "객실 이미지")
	public record Image(String url, String rprsYn, Integer sortSeq) {

		public static Image from(DrawerRoomImageVO vo) {
			return new Image(FILE_URL_PREFIX + vo.getAtchFileSn(), vo.getRprsYn(), vo.getSortSeq());
		}
	}

	@Schema(description = "판매자 정보(연락처 제외)")
	public record SellerInfo(String conm, String rprsvNm, String brno, String mailOrderSalesNo, String bizAddr) {
	}

	/**
	 * @param peakSeasonPrice  성수기·공휴일 요금 미지원(DB 개념 없음 — 항상 true)
	 * @param recurringClosure 반복/요일 휴관 미지원(저장처 없음 — 항상 true, 특정일 휴관만 houseClosedDates)
	 */
	@Schema(description = "미지원 항목 플래그")
	public record Unsupported(boolean peakSeasonPrice, boolean recurringClosure) {
	}
}
