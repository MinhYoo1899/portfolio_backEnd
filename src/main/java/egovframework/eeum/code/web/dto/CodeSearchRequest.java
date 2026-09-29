package egovframework.eeum.code.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 공통코드 목록 요청 DTO (GET query 바인딩).
 */
@Schema(description = "공통코드 목록 요청")
@Getter
@Setter
public class CodeSearchRequest {

	/** 그룹코드 — 어떤 코드 묶음인지 (예: USER_TYPE, ACNT_STTS) */
	@Schema(description = "그룹코드", example = "USER_TYPE")
	@NotBlank(message = "그룹코드(groupCd)는 필수입니다.")
	@Size(max = 50, message = "그룹코드는 50자 이내여야 합니다.")
	private String groupCd;
}
