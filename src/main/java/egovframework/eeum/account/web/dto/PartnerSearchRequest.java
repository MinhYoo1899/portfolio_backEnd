package egovframework.eeum.account.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

/**
 * 파트너 목록 검색 DTO (GET query 바인딩 — 서버사이드 페이징).
 */
@Schema(description = "파트너 목록 검색")
@Getter
@Setter
public class PartnerSearchRequest {

	/** 승인상태 필터 (미지정 시 전체) */
	@Schema(description = "승인상태 필터(REQUESTED/APPROVED/REJECTED — 생략 시 전체)", example = "REQUESTED")
	@Pattern(regexp = "REQUESTED|APPROVED|REJECTED", message = "승인상태 값이 올바르지 않습니다.")
	private String aprvStts;

	/** 페이지(1부터) */
	@Schema(description = "페이지(1부터)", example = "1", defaultValue = "1")
	@Min(value = 1, message = "page는 1 이상이어야 합니다.")
	private int page = 1;

	/** 페이지 크기(최대 100) */
	@Schema(description = "페이지 크기(최대 100)", example = "20", defaultValue = "20")
	@Min(value = 1, message = "pageSize는 1 이상이어야 합니다.")
	@Max(value = 100, message = "pageSize는 100 이하여야 합니다.")
	private int pageSize = 20;

	/** LIMIT 시작 offset (매퍼 #{offset} 바인딩 — JavaBean getter 필수) */
	public int getOffset() {
		return (page - 1) * pageSize;
	}
}
