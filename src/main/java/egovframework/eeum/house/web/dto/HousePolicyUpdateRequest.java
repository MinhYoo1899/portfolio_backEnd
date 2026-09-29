package egovframework.eeum.house.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * 고택 정책 수정 요청(위저드 Step2) — 추가금/체크인·아웃 시각/예약확정방식/취소환불·대중교통 안내.
 *
 * <p>소유자(PRTNR_SN)는 서버에서 해석(파트너=토큰, 운영자=대상 고택). 시각은 "HH:mm" 문자열로 받아 서비스에서 {@code LocalTime} 파싱.
 */
@Schema(description = "고택 정책 수정 요청")
public record HousePolicyUpdateRequest(

		@Schema(description = "고택일련번호")
		@NotNull(message = "고택일련번호가 필요합니다.")
		Long houseSn,

		@Schema(description = "성인(19+) 1인당 추가금(KRW, 0 이상)", example = "20000")
		@PositiveOrZero(message = "성인 추가금은 0 이상이어야 합니다.")
		Integer adultAddChrgAmt,

		@Schema(description = "아동(8~18) 1인당 추가금(KRW, 0 이상)", example = "10000")
		@PositiveOrZero(message = "아동 추가금은 0 이상이어야 합니다.")
		Integer childAddChrgAmt,

		@Schema(description = "영유아(<8) 1인당 추가금(KRW, 0 이상)", example = "0")
		@PositiveOrZero(message = "영유아 추가금은 0 이상이어야 합니다.")
		Integer infantAddChrgAmt,

		@Schema(description = "체크인 시각(HH:mm, 선택)", example = "15:00")
		@Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$", message = "체크인 시각은 HH:mm 형식이어야 합니다.")
		String chkinTime,

		@Schema(description = "체크아웃 시각(HH:mm, 선택)", example = "11:00")
		@Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$", message = "체크아웃 시각은 HH:mm 형식이어야 합니다.")
		String chkoutTime,

		@Schema(description = "레이트 체크아웃 시각(HH:mm, 선택)", example = "13:00")
		@Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$", message = "레이트 체크아웃 시각은 HH:mm 형식이어야 합니다.")
		String lateChkoutTime,

		@Schema(description = "예약확정방식(COMM_CD RSVT_CNFIRM: AUTO/MANUAL, 선택)", example = "AUTO")
		@Size(max = 20, message = "예약확정방식 코드는 20자 이내여야 합니다.")
		String rsvtCnfirmCd,

		@Schema(description = "취소·환불규정(선택)")
		@Size(max = 10000, message = "취소·환불규정은 10000자 이내여야 합니다.")
		String cnclRfndPlcyCn,

		@Schema(description = "대중교통 안내(선택)")
		@Size(max = 10000, message = "대중교통 안내는 10000자 이내여야 합니다.")
		String pbtrnspGuideCn,

		@Schema(description = "예약 공지/주의사항(선택)")
		@Size(max = 10000, message = "예약 공지는 10000자 이내여야 합니다.")
		String noticeCn) {
}
