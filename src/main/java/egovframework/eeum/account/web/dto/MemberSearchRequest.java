package egovframework.eeum.account.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 일반회원 목록 검색 DTO (운영자 콘솔 — GET query 바인딩, 서버사이드 페이징).
 *
 * <p>대상은 USER_TYPE_CD='MEMBER' 고정(서비스/매퍼). 키워드는 이름/이메일/닉네임 부분일치 —
 * 한 글자 PII 탐색 방지를 위해 2자 미만은 서비스에서 무시.
 */
@Schema(description = "일반회원 목록 검색")
@Getter
@Setter
public class MemberSearchRequest {

	/** 계정상태 필터(미지정 시 전체) */
	@Schema(description = "계정상태 필터(PENDING/ACTIVE/SUSPENDED/WITHDRAWN/REJECTED — 생략 시 전체)", example = "ACTIVE")
	@Pattern(regexp = "PENDING|ACTIVE|SUSPENDED|WITHDRAWN|REJECTED", message = "계정상태 값이 올바르지 않습니다.")
	private String acntStts;

	/** 검색어(이름/이메일/닉네임 부분일치, 2자 이상) */
	@Schema(description = "검색어(이름/이메일/닉네임 — 2자 이상)", example = "홍길동")
	@Size(max = 100, message = "검색어는 100자 이내여야 합니다.")
	private String keyword;

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
