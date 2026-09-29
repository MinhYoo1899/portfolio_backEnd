package egovframework.eeum.inquiry.web.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 문의 목록 응답 — 모드별 메타.
 *
 * <ul>
 *   <li>오프셋 모드: {@code total/page/pageSize/totalPages}(그리드).</li>
 *   <li>커서 모드(무한스크롤): {@code nextCursor/hasNext}(없으면 마지막).</li>
 * </ul>
 */
@Schema(description = "문의 목록 응답")
public record InqrListResponse(
		List<InqrListItemResponse> items,
		@Schema(description = "전체 건수(오프셋 모드)") Long total,
		@Schema(description = "현재 페이지(오프셋 모드)") Integer page,
		@Schema(description = "페이지 크기(오프셋 모드)") Integer pageSize,
		@Schema(description = "전체 페이지 수(오프셋 모드)") Integer totalPages,
		@Schema(description = "다음 커서(무한스크롤 — null이면 끝)") String nextCursor,
		@Schema(description = "다음 페이지 존재 여부") boolean hasNext) {

	public static InqrListResponse offset(List<InqrListItemResponse> items, long total, int page, int pageSize) {
		int totalPages = pageSize <= 0 ? 0 : (int) Math.ceil((double) total / pageSize);
		return new InqrListResponse(items, total, page, pageSize, totalPages, null, page < totalPages);
	}

	public static InqrListResponse cursor(List<InqrListItemResponse> items, Long nextCursor, boolean hasNext) {
		return new InqrListResponse(items, null, null, null, null,
				nextCursor == null ? null : String.valueOf(nextCursor), hasNext);
	}
}
