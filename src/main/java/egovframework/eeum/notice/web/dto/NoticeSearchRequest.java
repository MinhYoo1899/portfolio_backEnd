package egovframework.eeum.notice.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * 공지 목록 검색(전부 선택). status/target/notifySend/keyword 필터 + 페이지네이션(기본 10).
 */
@Schema(description = "공지 목록 검색")
public record NoticeSearchRequest(

		@Schema(description = "상태 필터(DRAFT/SCHEDULED/PUBLISHED_ONGOING/CLOSED, null=전체)") @Size(max = 30) String status,

		@Schema(description = "대상 필터(null=전체)") @Size(max = 30) String target,

		@Schema(description = "알림 발송 여부 필터(Y/N, null=전체)") @Size(max = 1) String notifySend,

		@Schema(description = "제목 검색어(부분일치, null=전체)") @Size(max = 200) String keyword,

		@Schema(description = "페이지(1-base, 기본 1)") @Min(1) Integer page,

		@Schema(description = "페이지 크기(기본 10, 최대 50)") @Min(1) @Max(50) Integer size) {

	public int pageOrDefault() {
		return page == null ? 1 : page;
	}

	public int sizeOrDefault() {
		return size == null ? 10 : size;
	}

	public int offset() {
		return (pageOrDefault() - 1) * sizeOrDefault();
	}
}
