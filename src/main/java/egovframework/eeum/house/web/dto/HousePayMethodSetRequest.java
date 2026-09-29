package egovframework.eeum.house.web.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 고택 결제수단 설정 요청(위저드 Step2) — 선택 집합 전체 교체(replace).
 *
 * <p>{@code payMthdCds} 에 담긴 결제수단(COMM_CD PAY_MTHD)만 활성으로 남긴다. 빈 배열이면 전부 해제.
 * 입력 순서가 정렬순서(SORT_SEQ)로 저장된다.
 */
@Schema(description = "고택 결제수단 설정 요청(전체 교체)")
public record HousePayMethodSetRequest(

		@Schema(description = "고택일련번호")
		@NotNull(message = "고택일련번호가 필요합니다.")
		Long houseSn,

		@Schema(description = "선택한 결제수단 코드 목록(빈 배열=전부 해제)", example = "[\"CARD\",\"BANK_TRANSFER\"]")
		@NotNull(message = "결제수단 목록이 필요합니다.")
		@Size(max = 50, message = "결제수단은 최대 50개까지 선택할 수 있습니다.")
		List<String> payMthdCds) {

	public HousePayMethodSetRequest {
		payMthdCds = payMthdCds == null ? List.of() : List.copyOf(payMthdCds);
	}
}
