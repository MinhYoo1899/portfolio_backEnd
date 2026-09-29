package egovframework.eeum.content.web.dto;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;

import egovframework.eeum.content.service.banner.ContentExposeMode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 팝업 수정 요청(전체 교체 — 등록과 동일 필드셋). exposeMode 로 EXPOSE_YN 갱신. 목록 즉시 토글은 PATCH /toggle 사용.
 */
@Schema(description = "팝업 수정 요청(전체 교체)")
public record ContentPopupUpdateRequest(

		@Schema(description = "팝업이미지 URL(자체 업로드 API 반환 경로)", example = "/files/download?atchFileSn=12")
		@NotBlank @Size(max = 500) String imageUrl,

		@Schema(description = "팝업 제목") @NotBlank @Size(max = 200) String title,

		@Schema(description = "노출위치코드(POPUP_EXPOSE_LOCATION: APP_LAUNCH/HOME/SEARCH)")
		@NotBlank @Size(max = 50) String exposeLocationCd,

		@Schema(description = "노출대상코드(POPUP_EXPOSE_TARGET: REQUIRED/OPTIONAL)")
		@NotBlank @Size(max = 50) String exposeTargetCd,

		@Schema(description = "연결대상코드(CONTENT_LINK_TARGET: PRODUCT/URL/PROMOTION, null=연결없음)")
		@Size(max = 50) String linkTargetCd,

		@Schema(description = "연결대상값(PRODUCT=houseId / URL=url / PROMOTION=promotionId)")
		@Size(max = 500) String linkTargetVal,

		@Schema(description = "노출시작일(KST)") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate exposeBgnDate,

		@Schema(description = "노출종료일(KST)") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate exposeEndDate,

		@Schema(description = "앱 노출 설정(IMMEDIATE=즉시노출 / SAVE_INACTIVE=비활성저장)")
		@NotNull ContentExposeMode exposeMode) {
}
