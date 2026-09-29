package egovframework.eeum.house.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * 고택 일자별 대표요금 설정 요청(위저드 3단계) — 주중/금/토/성수기 4구간, House 단위 참고요금(객실 판매가와 별도).
 *
 * <p>전체 교체(full replace) — 미전송 구간은 NULL 로 저장한다(정책 저장과 동일 계약). 소유자(PRTNR_SN)는 서버 해석.
 */
@Schema(description = "고택 일자별 대표요금 설정 요청(전체 교체)")
public record HouseDailyRateSetRequest(

		@Schema(description = "고택일련번호")
		@NotNull(message = "고택일련번호가 필요합니다.")
		Long houseSn,

		@Schema(description = "주중(일~목) 대표요금(KRW, 0 이상, 선택)", example = "180000")
		@PositiveOrZero(message = "주중 요금은 0 이상이어야 합니다.")
		Integer weekdayRateAmt,

		@Schema(description = "금요일 대표요금(KRW, 0 이상, 선택)", example = "220000")
		@PositiveOrZero(message = "금요일 요금은 0 이상이어야 합니다.")
		Integer fridayRateAmt,

		@Schema(description = "주말(토) 대표요금(KRW, 0 이상, 선택)", example = "250000")
		@PositiveOrZero(message = "주말 요금은 0 이상이어야 합니다.")
		Integer saturdayRateAmt,

		@Schema(description = "성수기·공휴일 대표요금(KRW, 0 이상, 선택)", example = "300000")
		@PositiveOrZero(message = "성수기 요금은 0 이상이어야 합니다.")
		Integer peakRateAmt) {
}
