package egovframework.eeum.account.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

/**
 * 운영자 목록 검색 DTO (GET query 바인딩 — 서버사이드 페이징).
 *
 * <p>대상은 항상 USER_TYPE_CD='OPERATOR' (매퍼가 고정). 상태/역할 필터는 선택.
 */
@Schema(description = "운영자 목록 검색")
@Getter
@Setter
public class OperatorSearchRequest {

	/** 계정상태 필터 (미지정 시 전체) */
	@Schema(description = "계정상태 필터(PENDING/ACTIVE/SUSPENDED — 생략 시 전체)", example = "ACTIVE")
	@Pattern(regexp = "PENDING|ACTIVE|SUSPENDED", message = "계정상태 값이 올바르지 않습니다.")
	private String acntStts;

	/** 역할 필터 (미지정 시 전체) — STAFF/SUPERVISOR */
	@Schema(description = "역할 필터(STAFF/SUPERVISOR — 생략 시 전체)", example = "SUPERVISOR")
	@Pattern(regexp = "STAFF|SUPERVISOR", message = "역할 값이 올바르지 않습니다.")
	private String role;

	/** 페이지(1부터) */
	@Schema(description = "페이지(1부터)", example = "1", defaultValue = "1")
	@Min(value = 1, message = "page는 1 이상이어야 합니다.")
	private int page = 1;

	/** 페이지 크기(최대 100) */
	@Schema(description = "페이지 크기(최대 100)", example = "20", defaultValue = "20")
	@Min(value = 1, message = "pageSize는 1 이상이어야 합니다.")
	@Max(value = 100, message = "pageSize는 100 이하여야 합니다.")
	private int pageSize = 20;

	/** 역할 필터 → ROLE_CD (매퍼 #{roleCd} 바인딩). 미지정이면 null. */
	public String getRoleCd() {
		if (role == null || role.isBlank()) {
			return null;
		}
		return "SUPERVISOR".equals(role) ? "OPERATOR_SUPERVISOR" : "OPERATOR_STAFF";
	}

	/** LIMIT 시작 offset (매퍼 #{offset} 바인딩 — JavaBean getter 필수). */
	public int getOffset() {
		return (page - 1) * pageSize;
	}
}
