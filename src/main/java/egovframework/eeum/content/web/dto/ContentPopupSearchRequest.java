package egovframework.eeum.content.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * 팝업 목록 검색 요청(전부 선택 — 미지정 시 전체). status 는 산출값 기준 in-service 필터, 나머지는 SQL 필터.
 */
@Schema(description = "팝업 목록 검색")
public record ContentPopupSearchRequest(

		@Schema(description = "상태 필터(EXPOSED/HIDDEN/ENDED, null=전체)") @Size(max = 20) String status,

		@Schema(description = "노출위치 필터(POPUP_EXPOSE_LOCATION, null=전체)") @Size(max = 50) String exposeLocationCd,

		@Schema(description = "제목 검색어(부분일치, null=전체)") @Size(max = 200) String keyword) {
}
