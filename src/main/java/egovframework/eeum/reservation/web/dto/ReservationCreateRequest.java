package egovframework.eeum.reservation.web.dto;

import java.time.LocalDate;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

/**
 * 예약 생성 요청(회원) — 객실 + 기간 + 게스트(+ 선택 동의).
 *
 * <p>소유권(파트너/고택/예약자)은 본문에서 받지 않는다 — roomSn 으로 서버가 판매자를, 토큰으로 예약자를 해석(IDOR 방어).
 * 수량은 1 고정(다객실 예약은 후속). 투숙인원수는 게스트 수에서 파생한다.
 */
@Schema(description = "예약 생성 요청")
public record ReservationCreateRequest(

		@Schema(description = "객실일련번호")
		@NotNull(message = "객실일련번호가 필요합니다.")
		Long roomSn,

		@Schema(description = "체크인 숙박일(KST, 포함)", example = "2026-08-01")
		@NotNull(message = "체크인일이 필요합니다.")
		LocalDate checkInDate,

		@Schema(description = "체크아웃일(KST, 미포함 — 반개구간)", example = "2026-08-03")
		@NotNull(message = "체크아웃일이 필요합니다.")
		LocalDate checkOutDate,

		@Schema(description = "게스트 목록(예약자 1명 필수)")
		@NotEmpty(message = "게스트 정보가 필요합니다.")
		@Valid
		List<GuestRequest> guests,

		@Schema(description = "동의 항목 목록(선택)")
		@Valid
		List<AgreementRequest> agreements) {
}
