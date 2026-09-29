package egovframework.eeum.reservation.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 예약 동의 요청 항목(선택) — 개인정보 수집/제3자 제공 등 동의 스냅샷.
 *
 * <p>약관버전번호는 서버가 TRMS_MST 에서 해석하므로 요청에서 받지 않는다(클라이언트 값 미신뢰).
 */
@Schema(description = "예약 동의 항목")
public record AgreementRequest(

		@Schema(description = "약관일련번호(선택 — 특정 약관 버전 연결 시)")
		Long trmsSn,

		@Schema(description = "동의목적코드", example = "RESERVATION_FULFILLMENT")
		@NotNull(message = "동의목적코드가 필요합니다.")
		@Size(max = 40, message = "동의목적코드는 40자 이내여야 합니다.")
		String agrePrpsCd,

		@Schema(description = "동의(수집)항목내용 스냅샷(선택)")
		@Size(max = 500, message = "동의항목내용은 500자 이내여야 합니다.")
		String agreItemCn) {
}
