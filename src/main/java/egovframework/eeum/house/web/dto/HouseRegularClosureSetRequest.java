package egovframework.eeum.house.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 고택 정기휴관 설정 요청(위저드 3단계) — 매주 휴관 요일(선택 1개) + 공휴일/명절 휴무 플래그.
 *
 * <p>전체 교체(full replace) — House 단위 정기휴관 메타. 실제 판매일 마감은 별도(ROOM_CALENDAR). 소유자는 서버 해석.
 * {@code closedWkdayCd} 는 MON~SUN 중 1개 또는 null(서비스 검증).
 */
@Schema(description = "고택 정기휴관 설정 요청(전체 교체)")
public record HouseRegularClosureSetRequest(

		@Schema(description = "고택일련번호")
		@NotNull(message = "고택일련번호가 필요합니다.")
		Long houseSn,

		@Schema(description = "매주 휴관 요일(MON/TUE/WED/THU/FRI/SAT/SUN, 선택)", example = "TUE")
		@Size(max = 3, message = "요일 코드는 3자 이내여야 합니다.")
		String closedWkdayCd,

		@Schema(description = "공휴일 휴무 여부")
		@NotNull(message = "공휴일 휴무 여부가 필요합니다.")
		Boolean holidayClosed,

		@Schema(description = "명절 휴무 여부")
		@NotNull(message = "명절 휴무 여부가 필요합니다.")
		Boolean lunarHolidayClosed) {
}
