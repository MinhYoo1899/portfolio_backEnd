package egovframework.eeum.content.web.dto;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;

import egovframework.eeum.content.service.banner.ContentExposeMode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 추천고택 수정 요청(전체 교체). exposeMode 로 EXPOSE_YN 갱신. 순서는 PATCH /order, 즉시 토글은 PATCH /toggle.
 */
@Schema(description = "추천고택 수정 요청(전체 교체)")
public record ContentRecHouseUpdateRequest(

		@Schema(description = "추천분류코드(REC_HOUSE_CATEGORY: TODAY_DEAL/BEST/MD_PICK)")
		@NotBlank @Size(max = 50) String recCategoryCd,

		@Schema(description = "대상 고택일련번호") @NotNull Long houseSn,

		@Schema(description = "노출시작일(KST)") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate exposeBgnDate,

		@Schema(description = "노출종료일(KST)") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate exposeEndDate,

		@Schema(description = "앱 노출 설정(IMMEDIATE=즉시노출 / SAVE_INACTIVE=비활성저장)")
		@NotNull ContentExposeMode exposeMode) {
}
