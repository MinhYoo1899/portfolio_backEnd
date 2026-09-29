package egovframework.eeum.content.web.dto;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;

import egovframework.eeum.content.service.banner.ContentExposeMode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 추천고택 등록 요청. recCategory/houseId 는 서비스에서 검증(공통코드·존재 고택). 연결대상은 항상 해당 고택.
 */
@Schema(description = "추천고택 등록 요청")
public record ContentRecHouseCreateRequest(

		@Schema(description = "추천분류코드(REC_HOUSE_CATEGORY: TODAY_DEAL/BEST/MD_PICK)")
		@NotBlank @Size(max = 50) String recCategoryCd,

		@Schema(description = "대상 고택일련번호") @NotNull Long houseSn,

		@Schema(description = "노출시작일(KST)") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate exposeBgnDate,

		@Schema(description = "노출종료일(KST)") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate exposeEndDate,

		@Schema(description = "앱 노출 설정(IMMEDIATE=즉시노출 / SAVE_INACTIVE=비활성저장)")
		@NotNull ContentExposeMode exposeMode) {
}
