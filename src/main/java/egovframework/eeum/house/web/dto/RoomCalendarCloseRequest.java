package egovframework.eeum.house.web.dto;

import java.time.LocalDate;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 객실 캘린더 마감/해제 전용 요청 — 지정한 날짜들의 {@code CLOSED_YN}만 토글한다(가격/수량/판매수량 보존).
 *
 * <p>한 호출은 한 의도만 처리: {@code closedYn="Y"}=마감, {@code "N"}=해제. 마감/해제 혼합은 두 번 호출.
 * 기존 {@code /rooms/calendar/set}(가격·수량 설정)과 책임 분리 — set 계약은 변경하지 않는다.
 */
@Schema(description = "객실 캘린더 마감/해제 요청(CLOSED_YN 토글 전용)")
public record RoomCalendarCloseRequest(

		@Schema(description = "객실일련번호")
		@NotNull(message = "객실일련번호가 필요합니다.")
		Long roomSn,

		@Schema(description = "대상 숙박일 목록(KST). 오늘 이전 날짜는 허용하지 않음.", example = "[\"2026-07-01\", \"2026-07-02\"]")
		@NotEmpty(message = "대상 날짜가 필요합니다.")
		@Size(max = 366, message = "한 번에 설정할 수 있는 날짜 수를 초과했습니다.")
		List<@NotNull(message = "날짜는 비어 있을 수 없습니다.") LocalDate> dates,

		@Schema(description = "마감여부 (Y=마감, N=해제)", example = "Y")
		@NotNull(message = "마감여부가 필요합니다.")
		@Pattern(regexp = "Y|N", message = "마감여부는 Y 또는 N만 허용됩니다.")
		String closedYn,

		@Schema(description = "휴무/마감 사유(선택, ≤200자 — 마감 시에만 저장)", example = "시설 점검")
		@Size(max = 200, message = "휴무 사유는 200자 이내여야 합니다.")
		String closeRsn) {

	/** 마감(Y) 의도인지. */
	public boolean isClose() {
		return "Y".equals(closedYn);
	}
}
