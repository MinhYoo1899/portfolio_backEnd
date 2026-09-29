package egovframework.eeum.house.web.dto;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

import egovframework.eeum.house.vo.HouseVO;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 고택 상세 응답.
 */
@Schema(description = "고택 상세")
public record HouseDetailResponse(

		@Schema(description = "고택일련번호") Long houseSn,
		@Schema(description = "소유파트너일련번호") Long prtnrSn,
		@Schema(description = "상호(판매자명)") String prtnrConm,
		@Schema(description = "고택명") String houseNm,
		@Schema(description = "고택상태(DRAFT/ACTIVE/HIDDEN/SUSPENDED)") String houseSttsCd,
		@Schema(description = "우편번호") String zip,
		@Schema(description = "주소") String addr,
		@Schema(description = "상세주소") String daddr,
		@Schema(description = "고택설명/소개") String houseDc,
		@Schema(description = "성인(19+) 1인당 추가금(KRW)") Integer adultAddChrgAmt,
		@Schema(description = "아동(8~18) 1인당 추가금(KRW)") Integer childAddChrgAmt,
		@Schema(description = "영유아(<8) 1인당 추가금(KRW)") Integer infantAddChrgAmt,
			@Schema(description = "주중(일~목) 대표요금(KRW, House 참고요금)") Integer weekdayRateAmt,
			@Schema(description = "금요일 대표요금(KRW)") Integer fridayRateAmt,
			@Schema(description = "주말(토) 대표요금(KRW)") Integer saturdayRateAmt,
			@Schema(description = "성수기·공휴일 대표요금(KRW)") Integer peakRateAmt,
			@Schema(description = "매주 휴관 요일(MON~SUN, 없으면 null)") String closedWkdayCd,
			@Schema(description = "공휴일 휴무 여부") boolean holidayClosed,
			@Schema(description = "명절 휴무 여부") boolean lunarHolidayClosed,
		@Schema(description = "체크인 시각(HH:mm)") String chkinTime,
		@Schema(description = "체크아웃 시각(HH:mm)") String chkoutTime,
		@Schema(description = "레이트 체크아웃 시각(HH:mm)") String lateChkoutTime,
			@Schema(description = "예약확정방식(AUTO/MANUAL)") String rsvtCnfirmCd,
		@Schema(description = "취소·환불규정") String cnclRfndPlcyCn,
		@Schema(description = "대중교통 안내") String pbtrnspGuideCn,
			@Schema(description = "예약 공지/주의사항") String noticeCn,
		@Schema(description = "활성 결제수단 코드 목록(SORT_SEQ 순)") List<String> payMethods,
		@Schema(description = "활성 객실 수") Integer roomCnt,
		@Schema(description = "등록일시(UTC, ISO-8601 Z)") Instant regDt,
		@Schema(description = "수정일시(UTC, ISO-8601 Z)") Instant mdfcnDt,
		@Schema(description = "대표이미지 첨부파일SN(없으면 null)") Long repImgFileSn,
		@Schema(description = "대표이미지 다운로드 URL(백엔드 경유, 없으면 null)") String repImgUrl) {

	private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

	public static HouseDetailResponse from(HouseVO vo) {
		return new HouseDetailResponse(
				vo.getHouseSn(),
				vo.getPrtnrSn(),
				vo.getPrtnrConm(),
				vo.getHouseNm(),
				vo.getHouseSttsCd(),
				vo.getZip(),
				vo.getAddr(),
				vo.getDaddr(),
				vo.getHouseDc(),
				vo.getAdultAddChrgAmt(),
				vo.getChildAddChrgAmt(),
				vo.getInfantAddChrgAmt(),
				vo.getWeekdayRateAmt(),
				vo.getFridayRateAmt(),
				vo.getSaturdayRateAmt(),
				vo.getPeakRateAmt(),
				vo.getClosedWkdayCd(),
				"Y".equals(vo.getHolidayClosedYn()),
				"Y".equals(vo.getLunarHolidayClosedYn()),
				formatTime(vo.getChkinTime()),
				formatTime(vo.getChkoutTime()),
				formatTime(vo.getLateChkoutTime()),
				vo.getRsvtCnfirmCd(),
				vo.getCnclRfndPlcyCn(),
				vo.getPbtrnspGuideCn(),
				vo.getNoticeCn(),
				vo.getPayMethods() == null ? List.of() : vo.getPayMethods(),
				vo.getRoomCnt() == null ? 0 : vo.getRoomCnt(),
				vo.getRegDt() == null ? null : vo.getRegDt().toInstant(ZoneOffset.UTC),
				vo.getMdfcnDt() == null ? null : vo.getMdfcnDt().toInstant(ZoneOffset.UTC),
				vo.getRprsAtchFileSn(),
				vo.getRprsAtchFileSn() == null ? null : "/files/download?atchFileSn=" + vo.getRprsAtchFileSn());
	}

	/** LocalTime → "HH:mm"(초 제거). null 이면 null. */
	private static String formatTime(LocalTime t) {
		return t == null ? null : t.format(HH_MM);
	}
}
