package egovframework.eeum.reservation.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 예약 게스트 요청 항목 — 예약자(RESERVER) 1명 필수 + 투숙자(OCCUPANT) 다수.
 *
 * <p>전화번호는 선택(암호화 저장). 이름은 평문 저장(암호화 제외 대상).
 */
@Schema(description = "예약 게스트")
public record GuestRequest(

		@Schema(description = "게스트유형(RESERVER=예약자/OCCUPANT=투숙자)", example = "RESERVER")
		@Pattern(regexp = "RESERVER|OCCUPANT", message = "게스트유형은 RESERVER 또는 OCCUPANT 여야 합니다.")
		String guestType,

		@Schema(description = "게스트명", example = "홍길동")
		@NotBlank(message = "게스트명을 입력해 주세요.")
		@Size(max = 100, message = "게스트명은 100자 이내여야 합니다.")
		String guestNm,

		@Schema(description = "게스트 휴대전화번호(선택)", example = "01012345678")
		@Size(max = 20, message = "휴대전화번호 형식이 올바르지 않습니다.")
		String guestMblTelno) {
}
