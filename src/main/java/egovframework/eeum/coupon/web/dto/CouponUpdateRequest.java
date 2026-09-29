package egovframework.eeum.coupon.web.dto;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * 쿠폰 수정 요청(전체 교체 — 발급수/사용수는 보존, 요청에 없음).
 * 이미 발급/사용된 쿠폰도 전체 필드 수정 가능(기존 발급분 소급 아님, 이후 사용분에만 적용 — 화면정의서 확정).
 */
@Schema(description = "쿠폰 수정 요청(전체 교체)")
public record CouponUpdateRequest(

		@Schema(description = "제목") @NotBlank @Size(max = 200) String title,

		@Schema(description = "내용") @NotBlank @Size(max = 500) String content,

		@Schema(description = "할인유형코드(COUPON_DISCOUNT_TYPE: RATE/FLAT)") @NotBlank @Size(max = 20) String discountTypeCd,

		@Schema(description = "할인값(RATE=%[1~100], FLAT=원[1이상])") @NotNull @Positive Integer discountVal,

		@Schema(description = "수취인(현 단계 ALL, 미지정 시 ALL)") @Size(max = 50) String targetTypeCd,

		@Schema(description = "사용시작일(KST)") @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate usageBgnDate,

		@Schema(description = "사용종료일(KST)") @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate usageEndDate) {
}
