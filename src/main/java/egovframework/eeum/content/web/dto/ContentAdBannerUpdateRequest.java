package egovframework.eeum.content.web.dto;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 광고배너 수정 요청(전체 교체 — 등록과 동일 필드셋).
 *
 * <p>EXPOSE_YN(노출여부)은 수정 대상이 아니다 — 기존 값을 보존하며, 노출 on/off 는 PATCH /toggle 로만 변경한다.
 * 따라서 exposeMode 는 두지 않는다.
 */
@Schema(description = "광고배너 수정 요청(전체 교체, EXPOSE_YN 보존)")
public record ContentAdBannerUpdateRequest(

		@Schema(description = "제목") @NotBlank @Size(max = 200) String title,

		@Schema(description = "부제") @Size(max = 200) String subCopy,

		@Schema(description = "광고이미지 URL(선택, 자체 업로드 API 반환 경로)", example = "/files/download?atchFileSn=12")
		@Size(max = 500) String imageUrl,

		@Schema(description = "노출위치코드(AD_BANNER_POSITION: MAIN/SEARCH/RECOMMENDED_HOUSE)")
		@NotBlank @Size(max = 50) String positionCd,

		@Schema(description = "연결대상코드(CONTENT_LINK_TARGET: PRODUCT/URL/PROMOTION, null=연결없음)")
		@Size(max = 50) String linkTargetCd,

		@Schema(description = "연결대상값(PRODUCT=houseId / URL=url / PROMOTION=promotionId)")
		@Size(max = 500) String linkTargetVal,

		@Schema(description = "노출시작일(KST)") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate exposeBgnDate,

		@Schema(description = "노출종료일(KST)") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate exposeEndDate) {
}
