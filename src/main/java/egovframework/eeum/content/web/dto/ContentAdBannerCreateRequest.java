package egovframework.eeum.content.web.dto;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 광고배너 등록 요청.
 *
 * <p>화면정의서 v3 광고배너 §1.2/§2 기준. imageUrl 은 선택이며, 있으면 자체 업로드 API 반환 경로만 허용(서비스 교차검증).
 * positionCd 는 AD_BANNER_POSITION 공통코드, linkTarget 규칙은 서비스에서 검증.
 * exposeMode/adType/region/order 는 도입하지 않는다(생성 시 EXPOSE_YN 기본 Y).
 */
@Schema(description = "광고배너 등록 요청")
public record ContentAdBannerCreateRequest(

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
