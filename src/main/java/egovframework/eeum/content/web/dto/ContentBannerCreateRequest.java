package egovframework.eeum.content.web.dto;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;

import egovframework.eeum.content.service.banner.ContentExposeMode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 메인배너 등록 요청. imageUrl 은 자체 업로드 API 반환 경로만 허용(서비스 교차검증). linkTarget 규칙은 서비스에서 검증.
 */
@Schema(description = "메인배너 등록 요청")
public record ContentBannerCreateRequest(

		@Schema(description = "배경이미지 URL(자체 업로드 API 반환 경로)", example = "/files/download?atchFileSn=12")
		@NotBlank @Size(max = 500) String imageUrl,

		@Schema(description = "메인카피") @NotBlank @Size(max = 200) String mainCopy,

		@Schema(description = "서브카피") @Size(max = 200) String subCopy,

		@Schema(description = "연결대상코드(CONTENT_LINK_TARGET: PRODUCT/URL/PROMOTION, null=연결없음)")
		@Size(max = 50) String linkTargetCd,

		@Schema(description = "연결대상값(PRODUCT=houseId / URL=url / PROMOTION=promotionId)")
		@Size(max = 500) String linkTargetVal,

		@Schema(description = "노출시작일(KST)") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate exposeBgnDate,

		@Schema(description = "노출종료일(KST)") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate exposeEndDate,

		@Schema(description = "앱 노출 설정(IMMEDIATE=즉시노출 / SAVE_INACTIVE=비활성저장)")
		@NotNull ContentExposeMode exposeMode) {
}
